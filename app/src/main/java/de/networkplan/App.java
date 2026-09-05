package de.networkplan;

import de.networkplan.ast.Activity;
import de.networkplan.ast.Dependency;
import de.networkplan.ast.NetworkPlan;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class App {
    private App() {
    }

    private static final int MAX_CLI_ARGS = 3;

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static boolean isVisualizationRequested(String[] args) {
        return args.length == 3 && args[1].equals("--visualize");
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || args.length > MAX_CLI_ARGS) {
            err.println(
                "Usage: network-plan-dsl <input-file> [--visualize <output.html>]"
            );
            return 1;
        }

        Path visualizationPath = null;
        if (isVisualizationRequested(args)) {
            try {
                visualizationPath = Path.of(args[2]);
            } catch (RuntimeException exception) {
                err.println("Visualization error: " + exception.getMessage());
                return 1;
            }
        }

        Path networkPath;
        NetworkPlan plan;
        try {
            networkPath = Path.of(args[0]);
            plan = new NetworkPlanReader().read(networkPath);
        } catch (IOException | RuntimeException exception) {
            err.println("File error: " + exception.getMessage());
            return 1;
        } catch (Exception exception) {
            err.println("Parse error: " + exception.getMessage());
            return 2;
        }

        List<String> errors = plan.semanticErrors();
        if (!errors.isEmpty()) {
            err.println("Semantic errors:");
            for (String error : errors) {
                err.println("  - " + error);
            }
            return 3;
        }

        if (visualizationPath != null) {
            try {
                Files.writeString(
                    visualizationPath,
                    new NetworkPlanHtmlRenderer().render(plan),
                    StandardCharsets.UTF_8
                );
            } catch (IOException exception) {
                err.println("Visualization error: " + exception.getMessage());
                return 1;
            }
        }

        printResult(plan, out);
        return 0;
    }

    private static void printResult(NetworkPlan plan, PrintStream out) {
        out.println("Project: " + plan.getName());
        out.println("Semantic analysis: OK");
        out.println();
        out.println(
            "Activity  Dur  ES  EF  LS  LF  TF  FF  CF  IF  Critical"
        );
        for (Activity activity : plan.getActivityList()) {
            out.printf(
                "%-8s %4d %3d %3d %3d %3d %3d %3d %3d %3d  %s%n",
                activity.getName(),
                activity.getDuration(),
                activity.earliestStart(),
                activity.earliestFinish(),
                activity.latestStart(),
                activity.latestFinish(),
                activity.totalFloat(),
                activity.freeFloat(),
                activity.conditionalFloat(),
                activity.independentFloat(),
                activity.isCritical() ? "yes" : "no"
            );
        }
        out.println();
        out.println("Project duration: " + plan.projectDuration());

        List<Activity> path = plan.linearCriticalPath();
        if (!path.isEmpty()) {
            String chain = String.join(" -> ", path.stream().map(Activity::getName).toList());
            out.println("Critical path: " + chain);
        } else {
            out.println("Critical subgraph:");
            String activities = String.join(", ", plan.criticalActivities().stream().map(Activity::getName).toList());
            out.println("  Activities: " + activities);
            out.println("  Dependencies:");

            for (Dependency dependency : plan.criticalDependencies()) {
                out.println("    " + dependency.getSourceName() + " -> " + dependency.getTargetName());
            }
        }
    }
}

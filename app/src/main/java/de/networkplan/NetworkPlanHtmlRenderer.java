package de.networkplan;

import de.networkplan.ast.Activity;
import de.networkplan.ast.Dependency;
import de.networkplan.ast.NetworkPlan;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class NetworkPlanHtmlRenderer {

    final static String DIAGRAM_TYPE = "flowchart LR\n";
    final static String ACTIVITY_PREFIX = "activity-";
    final static String TAB = "    ";
    final static String CLASS = "class ";
    final static String CRITICAL = "critical\n";
    final static String CRITICAL_CLASS = "classDef critical fill:#ffebee,stroke:#c62828,stroke-width:3px,color:#212121\n";
    final static String NORM = "noncritical\n";
    final static String NORM_CLASS = "classDef noncritical fill:#e3f2fd,stroke:#1565c0,stroke-width:1.5px,color:#212121\n";

    String render(NetworkPlan plan) {
        Map<Activity, String> ids = new IdentityHashMap<>();
        for (int index = 0; index < plan.getNumActivity(); index++) {
            ids.put(plan.getActivity(index), ACTIVITY_PREFIX + index);
        }

        StringBuilder diagram = new StringBuilder(DIAGRAM_TYPE);
        for (Activity activity : plan.getActivityList()) {
            diagram.append(TAB)
                .append(ids.get(activity))
                .append("[\"" + activityLabel(activity) + "\"]\n");
        }

        // Mermaid styles links by their declaration index, so the edges are
        // emitted by index and the critical ones collected as we go.
        List<Integer> criticalEdges = new ArrayList<>();
        for (int index = 0; index < plan.getNumDependency(); index++) {
            Dependency dependency = plan.getDependency(index);
            diagram.append(TAB)
                .append(ids.get(dependency.source()))
                .append(" --> ")
                .append(ids.get(dependency.target()))
                .append("\n");
            if (dependency.isCritical()) {
                criticalEdges.add(index);
            }
        }

        diagram
            .append(TAB).append(CRITICAL_CLASS)
            .append(TAB).append(NORM_CLASS);

        for (Activity activity : plan.getActivityList()) {
            diagram.append(TAB).append(CLASS)
                .append(ids.get(activity))
                .append(" ")
                .append(activity.isCritical() ? CRITICAL : NORM);
        }
        // A plan without critical dependencies - a single activity, say - still
        // has critical nodes, and Mermaid rejects an empty linkStyle index list.
        if (!criticalEdges.isEmpty()) {
            diagram.append(TAB).append("linkStyle ")
                .append(joinIndices(criticalEdges))
                .append(" stroke:#c62828,stroke-width:3px\n");
        }

        String title = escapeHtml(plan.getName()) + " — Network Plan";
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>%s</title>
              <style>
                body { font-family: system-ui, sans-serif; margin: 2rem; color: #212121; }
                h1 { margin-bottom: .25rem; }
                .summary { margin: 0 0 .25rem; }
                .legend { color: #555; margin: 0 0 1.5rem; }
                .mermaid { overflow: auto; }
              </style>
            </head>
            <body>
              <h1>%s</h1>
              <p class="summary"><strong>Project duration:</strong> %d</p>
              <p class="legend">
                ES/EF: earliest start/finish · LS/LF: latest start/finish ·
                TF: total float · red: critical
              </p>
              <pre class="mermaid">
            %s  </pre>
              <script type="module">
                import mermaid from
                  'https://cdn.jsdelivr.net/npm/mermaid@11.17.2/dist/mermaid.esm.min.mjs';
                mermaid.initialize({ startOnLoad: true, securityLevel: 'strict' });
              </script>
            </body>
            </html>
            """.formatted(title, title, plan.projectDuration(), diagram);
    }

    private String activityLabel(Activity activity) {
      return """
        %s<br/>Duration: %d<br/>ES: %d | EF: %d<br/>LS: %d | LF: %d<br/>TF: %d"""
        .formatted(
          escapeMermaid(activity.getName()),
          activity.getDuration(),
          activity.earliestStart(),
          activity.earliestFinish(),
          activity.latestStart(),
          activity.latestFinish(),
          activity.totalFloat()
        );
    }

    private String joinIndices(List<Integer> indices) {
        return String.join(",", indices.stream().map(String::valueOf).toList());
    }

    private String escapeMermaid(String value) {
        return escapeHtml(value).replace("\"", "&quot;");
    }

    private String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}

package de.networkplan;

import de.networkplan.ast.Activity;
import de.networkplan.ast.NetworkPlan;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

final class PlanTestSupport {
    private PlanTestSupport() {}

    static NetworkPlan parse(String source) throws Exception {
        return new NetworkPlanReader().read(new StringReader(source));
    }

    static NetworkPlan parseResource(String resource) throws Exception {
        InputStream input = PlanTestSupport.class.getResourceAsStream(resource);
        assertNotNull(input);
        
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return new NetworkPlanReader().read(reader);
        }
    }

    static Activity activity(NetworkPlan plan, String name) {
        for (Activity activity : plan.getActivityList()) {
            if (name.equals(activity.getName())) {
                return activity;
            }
        }
        throw new AssertionError("Missing activity " + name);
    }

    static List<String> names(List<Activity> activities) {
        return activities.stream().map(Activity::getName).toList();
    }

    static NetworkPlan chainPlan(int activityCount) throws Exception {
        StringBuilder source = new StringBuilder("project Chain {\n");
        for (int index = 0; index < activityCount; index++) {
            source.append("activity A")
                .append(index)
                .append(" duration 1;\n");
        }
        for (int index = 0; index < activityCount - 1; index++) {
            source.append("dependency A")
                .append(index)
                .append(" -> A")
                .append(index + 1)
                .append(";\n");
        }
        source.append("}\n");
        return parse(source.toString());
    }
}

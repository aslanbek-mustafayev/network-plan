package de.networkplan;

import de.networkplan.ast.NetworkPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class NameAnalysisTest {
    @Test
    void resolvesDependencyEndpointsToActivityObjects() throws Exception {
        NetworkPlan plan = PlanTestSupport.parse("""
            project Demo {
                activity A duration 2;
                activity B duration 3;
                dependency A -> B;
            }
            """);

        assertSame(plan.getActivity(0), plan.getDependency(0).source());
        assertSame(plan.getActivity(1), plan.getDependency(0).target());
    }
}

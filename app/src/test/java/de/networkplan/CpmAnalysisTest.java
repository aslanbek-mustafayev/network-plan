package de.networkplan;

import de.networkplan.ast.Activity;
import de.networkplan.ast.Dependency;
import de.networkplan.ast.NetworkPlan;
import de.networkplan.ast.PlanList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpmAnalysisTest {
    private static NetworkPlan lecturePlan() throws Exception {
        return PlanTestSupport.parseResource("/lecture-example.network");
    }

    @Test
    void computesOneBasedForwardPass() throws Exception {
        NetworkPlan plan = lecturePlan();

        assertSchedule(plan, "A", 10, 1, 10);
        assertSchedule(plan, "B", 20, 11, 30);
        assertSchedule(plan, "C", 5, 31, 35);
        assertSchedule(plan, "D", 10, 36, 45);
        assertSchedule(plan, "E", 20, 46, 65);
        assertSchedule(plan, "F", 15, 11, 25);
        assertSchedule(plan, "G", 5, 36, 40);
        assertSchedule(plan, "H", 15, 11, 25);
        assertEquals(65, plan.projectDuration());

        assertBackward(plan, "A", 1, 10, 0);
        assertBackward(plan, "B", 11, 30, 0);
        assertBackward(plan, "C", 31, 35, 0);
        assertBackward(plan, "D", 36, 45, 0);
        assertBackward(plan, "E", 46, 65, 0);
        assertBackward(plan, "F", 26, 40, 15);
        assertBackward(plan, "G", 41, 45, 5);
        assertBackward(plan, "H", 31, 45, 20);

        assertFloats(plan, "A", 0, 0, 0, 0);
        assertFloats(plan, "B", 0, 0, 0, 0);
        assertFloats(plan, "C", 0, 0, 0, 0);
        assertFloats(plan, "D", 0, 0, 0, 0);
        assertFloats(plan, "E", 0, 0, 0, 0);
        assertFloats(plan, "F", 15, 10, 5, 10);
        assertFloats(plan, "G", 5, 5, 0, 0);
        assertFloats(plan, "H", 20, 20, 0, 20);

        for (Activity activity : plan.getActivityList()) {
            assertEquals(activity.latestStart() - activity.earliestStart(), activity.totalFloat());
            assertEquals(activity.latestFinish() - activity.earliestFinish(), activity.totalFloat());
            assertEquals(activity.getDuration(), activity.earliestFinish() - activity.earliestStart() + 1);
            assertEquals(activity.getDuration(), activity.latestFinish() - activity.latestStart() + 1);
            assertTrue(activity.totalFloat() >= activity.freeFloat());
            assertTrue(activity.freeFloat() >= activity.independentFloat());
            assertTrue(activity.independentFloat() >= 0);
        }

        List<String> criticalNames = new ArrayList<String>();
        for (Activity activity : plan.getActivityList()) {
            if (activity.isCritical()) {
                criticalNames.add(activity.getName());
            }
        }

        var nodes = List.of("A", "B", "C", "D", "E");
        assertEquals(nodes, criticalNames);
        assertEquals(nodes, PlanTestSupport.names(plan.linearCriticalPath()));

        // We leave this as string comparison to have a simple and readable test for critical dependencies.
        var expectedCriticalDependencies = List.of("A -> B", "B -> C", "C -> D", "D -> E");
        assertEquals(5, plan.criticalActivities().size());
        assertEquals(
            expectedCriticalDependencies,
            plan.criticalDependencies().stream()
                .map(dependency -> dependency.getSourceName() + " -> " + dependency.getTargetName())
                .toList()
        );
    }

    @Test
    void reportsBranchingCriticalSubgraphWithoutChoosingMainChain() throws Exception {
        NetworkPlan plan = PlanTestSupport.parse("""
            project BranchingCritical {
                activity A duration 1;
                activity B duration 1;
                activity C duration 1;
                activity D duration 1;
                dependency A -> B;
                dependency A -> C;
                dependency B -> D;
                dependency C -> D;
            }
            """);

        assertTrue(plan.semanticErrors().isEmpty());
        assertTrue(plan.linearCriticalPath().isEmpty());
        assertEquals(
            List.of("A", "B", "C", "D"),
            PlanTestSupport.names(plan.criticalActivities())
        );
        assertEquals(4, plan.criticalDependencies().size());
    }

    @Test
    void evaluatesHundredActivityChainWithoutRecursiveTraversal() throws Exception {
        assertChainSchedule(100);
    }

    @Test
    void evaluatesThousandActivityChainWithoutRecursiveTraversal()throws Exception {
        assertChainSchedule(1_000);
    }

    @Test
    void memoizesProjectDurationAfterFirstEvaluation() {
        CountingActivity first = new CountingActivity("A", 1);
        CountingActivity second = new CountingActivity("B", 1);
        NetworkPlan plan = new NetworkPlan(
            "Memoized",
            new PlanList<Activity>().add(first).add(second),
            new PlanList<Dependency>().add(new Dependency("A", "B"))
        );

        assertEquals(2, plan.projectDuration());
        int readsAfterFirstEvaluation = first.durationReads() + second.durationReads();

        assertEquals(2, plan.projectDuration());
        int readsAfterSecondEvaluation = first.durationReads() + second.durationReads();

        // if these are equal, it means the project duration was memoized and the activities' durations were not read again.
        assertEquals(readsAfterFirstEvaluation, readsAfterSecondEvaluation);
    }

    

    private static void assertSchedule(
            NetworkPlan plan,
            String name,
            int duration,
            int start,
            int finish) {
        Activity activity = PlanTestSupport.activity(plan, name);
        assertEquals(duration, activity.getDuration());
        assertEquals(start, activity.earliestStart());
        assertEquals(finish, activity.earliestFinish());
    }

    private static void assertBackward(
            NetworkPlan plan,
            String name,
            int latestStart,
            int latestFinish,
            int totalFloat) {
        Activity activity = PlanTestSupport.activity(plan, name);
        assertEquals(latestStart, activity.latestStart());
        assertEquals(latestFinish, activity.latestFinish());
        assertEquals(totalFloat, activity.totalFloat());
    }

    private static void assertFloats(
            NetworkPlan plan,
            String name,
            int total,
            int free,
            int conditional,
            int independent) {
        Activity activity = PlanTestSupport.activity(plan, name);
        assertEquals(total, activity.totalFloat());
        assertEquals(free, activity.freeFloat());
        assertEquals(conditional, activity.conditionalFloat());
        assertEquals(independent, activity.independentFloat());
    }

    private static void assertChainSchedule(int activityCount) throws Exception {
        NetworkPlan plan = PlanTestSupport.chainPlan(activityCount);

        assertTrue(plan.semanticErrors().isEmpty());
        assertEquals(activityCount, plan.projectDuration());
        assertEquals(1, plan.getActivity(0).earliestStart());
        assertEquals(activityCount, plan.getActivity(activityCount - 1).earliestFinish());
        assertEquals(0, plan.getActivity(0).totalFloat());
        assertEquals(0, plan.getActivity(activityCount - 1).totalFloat());
    }

    private static final class CountingActivity extends Activity {
        private int durationReads;

        CountingActivity(String name, int duration) {
            super(name, duration);
        }

        @Override
        public Integer getDuration() {
            durationReads++;
            return super.getDuration();
        }

        int durationReads() {
            return durationReads;
        }
    }
}

package de.networkplan;

import de.networkplan.ast.Activity;
import de.networkplan.ast.NetworkPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphAnalysisTest {
    @Test
    void derivesOrderedPredecessorsSuccessorsSourceAndSink() throws Exception {
        NetworkPlan plan = PlanTestSupport.parse("""
            project Diamond {
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

        Activity a = PlanTestSupport.activity(plan, "A");
        Activity b = PlanTestSupport.activity(plan, "B");
        Activity c = PlanTestSupport.activity(plan, "C");
        Activity d = PlanTestSupport.activity(plan, "D");

        assertEquals(List.of(b, c), a.successors());
        assertEquals(List.of(b, c), d.predecessors());
        assertEquals(List.of(a), plan.sources());
        assertEquals(List.of(d), plan.sinks());
        assertTrue(a.isSource());
        assertFalse(a.isSink());
        assertFalse(b.isSource());
        assertFalse(c.isSink());
        assertFalse(d.isSource());
        assertTrue(d.isSink());
        assertSame(a, plan.source());
        assertSame(d, plan.sink());
    }

    @Test
    void uniqueEndpointsAreNullWhenSourcesAndSinksAreNonUnique() throws Exception {
        NetworkPlan plan = PlanTestSupport.parse("""
            project Disconnected {
                activity A duration 1;
                activity B duration 1;
                activity C duration 1;
                activity D duration 1;
                dependency A -> B;
                dependency C -> D;
            }
            """);

        var planSources = PlanTestSupport.names(plan.sources());
        var planSinks = PlanTestSupport.names(plan.sinks());

        assertEquals(List.of("A", "C"), planSources);
        assertEquals(List.of("B", "D"), planSinks);
        assertNull(plan.source());
        assertNull(plan.sink());
    }

    @Test
    void preservesActivityDeclarationOrderInLectureRelations()throws Exception {
        NetworkPlan plan = PlanTestSupport.parseResource("/lecture-example.network");

        assertRelations(plan, "A", List.of(), List.of("B", "F", "H"));
        assertRelations(plan, "B", List.of("A"), List.of("C"));
        assertRelations(plan, "C", List.of("B"), List.of("D", "G"));
        assertRelations(plan, "D", List.of("C"), List.of("E"));
        assertRelations(plan, "E", List.of("D", "G", "H"), List.of());
        assertRelations(plan, "F", List.of("A"), List.of("G"));
        assertRelations(plan, "G", List.of("C", "F"), List.of("E"));
        assertRelations(plan, "H", List.of("A"), List.of("E"));
    }

    private static void assertRelations(NetworkPlan plan, String name, List<String> predecessors, List<String> successors) {
        Activity activity = PlanTestSupport.activity(plan, name);

        var predecessorsNames = PlanTestSupport.names(activity.predecessors());
        var successorsNames = PlanTestSupport.names(activity.successors());

        assertEquals(predecessors, predecessorsNames);
        assertEquals(successors, successorsNames);
    }
}

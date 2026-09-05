package de.networkplan;

import de.networkplan.ast.NetworkPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParserTest {
    @Test
    void parsesNamedProjectIntoSeparateLists() throws Exception {
        NetworkPlan plan = PlanTestSupport.parse("""
            project Demo {
                activity A duration 2;
                activity B duration 3;
                dependency A -> B;
            }
            """);

        assertEquals("Demo", plan.getName());
        assertEquals(2, plan.getNumActivity());
        assertEquals(1, plan.getNumDependency());
        assertEquals("A", plan.getActivity(0).getName());
        assertEquals(3, plan.getActivity(1).getDuration());
    }

    @Test
    void rejectsInvalidSyntax() {
        var graph =  """
            project Invalid {
                activity A duration ;
            }
            """;
        assertThrows(Exception.class, () -> PlanTestSupport.parse(graph));
    }

    @Test
    void rejectsActivityDeclaredAfterDependency() {
        var graph =  """
            project InvalidOrder {
                activity A duration 1;
                dependency A -> B;
                activity B duration 1;
            }
            """;
        assertThrows(Exception.class, () -> PlanTestSupport.parse(graph));
    }
}

package de.networkplan;

import de.networkplan.ast.NetworkPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SemanticAnalysisTest {

    /**
     * Returns the list of semantic errors found in the given network source.
     * @param source text of the network plan to analyze
     * @return the list of semantic errors found after creation of the network plan
     * @throws Exception if parsing the network plan fails
     */
    private static List<String> getErrorsFromPlan(String source) throws Exception {
        NetworkPlan plan = PlanTestSupport.parse(source);
        return plan.semanticErrors();
    }

    @Test
    void reportsEmptyProject() throws Exception {
        var givenGraph = "project Empty { }";
        var expectedErrors = List.of("Project must contain at least one activity");
        assertEquals(expectedErrors, getErrorsFromPlan(givenGraph));
    }

    @Test
    void reportsDurationDuplicateNameAndDependencyProblems() throws Exception {
        var givenGraph = """
                project Invalid {
                    activity A duration 0;
                    activity A duration 2;
                    dependency A -> X;
                    dependency A -> A;
                    dependency A -> X;
                }
                """;
        var expectedErrors = List.of(
            "Activity 'A' has non-positive duration 0",
            "Duplicate activity name 'A'",
            "Unknown activity 'X' used as dependency target",
            "Activity 'A' cannot depend on itself",
            "Duplicate dependency A -> X"
        );
        assertEquals(expectedErrors, getErrorsFromPlan(givenGraph));
    }

    @Test
    void reportsUnknownDependencySource() throws Exception {
        var givenGraph = """
            project UnknownSource {
                activity A duration 1;
                dependency X -> A;
            }
            """;
        var expectedErrors = List.of("Unknown activity 'X' used as dependency source");
        assertEquals(expectedErrors, getErrorsFromPlan(givenGraph));
    }

    @Test
    void reportsDuplicateResolvedDependency() throws Exception {
        var givenGraph = """
                project DuplicateEdge {
                    activity A duration 1;
                    activity B duration 1;
                    dependency A -> B;
                    dependency A -> B;
                }
                """;
        var expectedErrors = List.of("Duplicate dependency A -> B");
        assertEquals(expectedErrors, getErrorsFromPlan(givenGraph));
    }

    @Test
    void reportsDeterministicCyclePath() throws Exception {
        var givenGraph = """
                project Cyclic {
                    activity A duration 1;
                    activity B duration 1;
                    activity C duration 1;
                    dependency A -> B;
                    dependency B -> C;
                    dependency C -> A;
                }
                """;
        var expectedErrors = List.of("Dependency cycle detected: A -> B -> C -> A");
        assertEquals(expectedErrors, getErrorsFromPlan(givenGraph));
    }

    @Test
    void retainsDurationError() throws Exception {
        var givenGraph = """
                project MixedDurationTopology {
                    activity A duration 0;
                    activity B duration 1;
                    activity C duration 1;
                    dependency A -> C;
                }
                """;
        var expectedErrors = List.of(
            "Activity 'A' has non-positive duration 0",
            "Project must have exactly one source activity; found A, B",
            "Project must have exactly one terminal activity; found B, C"
        );
        assertEquals(expectedErrors, getErrorsFromPlan(givenGraph));
    }

    @Test
    void retainsDuplicateEdgeWhileReportingIndependentCycle() throws Exception {
        var givenGraph = """
                project MixedDuplicateCycle {
                    activity A duration 1;
                    activity B duration 1;
                    activity C duration 1;
                    dependency A -> B;
                    dependency A -> B;
                    dependency B -> C;
                    dependency C -> B;
                }
                """;
        var expectedErrors = List.of(
            "Duplicate dependency A -> B",
            "Dependency cycle detected: B -> C -> B"
        );
        assertEquals(expectedErrors, getErrorsFromPlan(givenGraph));
    }

    @Test
    void selfDependencyGatesCycleAndTopologyNoise() throws Exception {
        var givenGraph = """
                project SelfEdge {
                    activity A duration 1;
                    activity B duration 1;
                    dependency A -> A;
                }
                """;
        var expectedErrors = List.of("Activity 'A' cannot depend on itself");
        assertEquals(expectedErrors, getErrorsFromPlan(givenGraph));
    }

    @Test
    void rejectsMultipleSourcesAndSinks() throws Exception {
        var givenGraph = """
                project Disconnected {
                    activity A duration 1;
                    activity B duration 1;
                    activity C duration 1;
                    activity D duration 1;
                    dependency A -> B;
                    dependency C -> D;
                }
                """;
        var expectedErrors = List.of(
            "Project must have exactly one source activity; found A, C",
            "Project must have exactly one terminal activity; found B, D"
        );
        assertEquals(expectedErrors, getErrorsFromPlan(givenGraph));
    }
}

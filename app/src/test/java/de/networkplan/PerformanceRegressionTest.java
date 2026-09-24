package de.networkplan;

import de.networkplan.ast.NetworkPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Benchmarks the CPM evaluator against performance and memory regressions.
 *
 * Note, that budgetst are meant to catch a blowup,
 * such as losing the memoization in {@code CpmAnalysis.jrag} and falling back
 * to quadratic re-evaluation, without failing on a busy machine. 
 * Precise measurements come from the JMH benchmarks instead, see
 * {@code ./gradlew :app:benchmark}.
 */
class PerformanceRegressionTest {
    private static final int LARGE_SCALE = 10_000; // number of activities for large-scale tests
    private static final long TIME_BUDGET_MILLIS = 2_000; // 2 seconds
    private static final long MEMORY_BUDGET_BYTES = 50L * 1024 * 1024; // 50 MB

    @Test
    void evaluatesLargeChainWithinBudget() throws Exception {
        NetworkPlan plan = PlanTestSupport.chainPlan(LARGE_SCALE);

        assertWithinBudget(plan, LARGE_SCALE, "chain of " + LARGE_SCALE + " activities");
    }

    @Test
    void evaluatesLargeFanOutMergeWithinBudget() throws Exception {
        NetworkPlan plan = PlanTestSupport.fanOutMergePlan(LARGE_SCALE);

        // Source, any one of the parallel middle activities, then the sink.
        assertWithinBudget(plan, 3, "fan-out/merge of " + LARGE_SCALE + " activities");
    }

    /**
     * Measures one full CPM evaluation. The plan is parsed by the caller, so
     * parsing is outside the measured window and only the analysis counts.
     */
    private static void assertWithinBudget(NetworkPlan plan, int expectedDuration, String label) {
        System.gc();
        long memoryBefore = usedMemoryBytes();
        long start = System.nanoTime();

        int duration = plan.projectDuration();

        long elapsedMillis = (System.nanoTime() - start) / 1_000_000; // convert to milliseconds
        long memoryUsedBytes = usedMemoryBytes() - memoryBefore;

        assertEquals(expectedDuration, duration);
        assertTrue(
            elapsedMillis <= TIME_BUDGET_MILLIS,
            label + " took " + elapsedMillis + " ms, expected at most " + TIME_BUDGET_MILLIS + " ms"
        );
        assertTrue(
            memoryUsedBytes <= MEMORY_BUDGET_BYTES,
            label + " used " + memoryUsedBytes + " bytes, expected at most " + MEMORY_BUDGET_BYTES + " bytes"
        );

        System.out.printf("%s: %d ms, %d KB%n", label, elapsedMillis, memoryUsedBytes / 1024);
    }

    private static long usedMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}

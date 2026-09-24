package de.networkplan;

import de.networkplan.ast.NetworkPlan;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.io.StringReader;
import java.util.concurrent.TimeUnit;

/**
 * Measures the three stages of the tool: 
 * - parsing network plan text into a plan,
 * - validating the plan, and
 * - running the CPM analysis on it.
 *
 * Every benchmark below is repeated for each size from 10 up to 10000
 * activities and each shape (chain, fanOutMerge). Resulting numbers show how the
 * cost grows as plans get bigger and more branched.
 *
 * Run with {@code ./gradlew :app:benchmark}, or add {@code -Pquick} for a smoke run.
 */
@BenchmarkMode(Mode.AverageTime)        // report the average time of one call
@OutputTimeUnit(TimeUnit.MICROSECONDS)  // ...in microseconds
public class NetworkPlanBenchmark {
    /** Text in, plan out. Lexer and parser only, nothing is checked or analysed. */
    @Benchmark
    public NetworkPlan parseOnly(InputState state) throws Exception {
        return parse(state.source);
    }

    /**
     * This benchmark only checks for semantic errors: duplicate or unknown activity names, self dependencies,
     * cycles, and if the plan has exactly one start and one end activity.
     *
     * More close to "rebuild a plan, then check it". A fresh plan has
     * to be made before every call, see {@link FreshPlanState}, and that
     * rebuild costs more than the check itself.
     */
    @Benchmark
    public int semanticValidation(FreshPlanState state) {
        return state.plan.semanticErrors().size();
    }

    /**
     * The CPM analysis on a plan that has never been analysed, so the forward
     * pass, the backward pass and the float calculation all really run.
     *
     * Note: the graph is rebuilt before each call, so the timing includes that cost.
     */
    @Benchmark
    public int firstCpmEvaluation(FreshPlanState state) {
        return state.plan.projectDuration();
    }

    /**
     * Everything in one go. Same as the command line tool will perform: 
     * parse, check, analyse. Nothing is carried over between calls, 
     * which makes this the most trustworthy benchmark of all.
     */
    @Benchmark
    public int coldEndToEnd(InputState state) throws Exception {
        NetworkPlan plan = parse(state.source);
        if (!plan.semanticErrors().isEmpty()) {
            throw new IllegalStateException("Generated invalid plan");
        }
        return plan.projectDuration();
    }

    /**
     * An already analysed plan should remember the result and return it immediately 
     * when asked a second time. The answer was kept by the lazy attribute. 
     * Near zero time and near zero memory here is the evidence that
     * the memoisation works as expected.
     */
    @Benchmark
    public int cachedProjectDuration(CachedPlanState state) {
        return state.plan.projectDuration();
    }

    /** Plan text, built once and reused by every call. */
    @State(Scope.Benchmark)
    public static class InputState {
        @Param({"10", "100", "1000", "5000", "10000"})
        public int activities;

        @Param({"chain", "fanOutMerge"})
        public String topology;

        private String source;

        @Setup(Level.Trial)
        public void setUp() {
            source = input(topology, activities);
        }
    }

    /**
     * A plan rebuilt from scratch before every single call.
     *
     * This is needed because both the checks and the analysis remember their
     * results, so any one plan can only be measured cold. Otherwise it will reuse cached results. 
     * Which makes every measurement includes the cost of rebuilding the plan from scratch.
     */
    @State(Scope.Benchmark)
    public static class FreshPlanState {
        @Param({"10", "100", "1000", "5000", "10000"})
        public int activities;

        @Param({"chain", "fanOutMerge"})
        public String topology;

        private String source;
        private NetworkPlan plan;

        @Setup(Level.Trial)
        public void setUpTrial() {
            source = input(topology, activities);
        }

        @Setup(Level.Invocation)
        public void setUpInvocation() throws Exception {
            plan = parse(source);
        }
    }

    /** A plan built, checked and analysed once up front, then reused as is. */
    @State(Scope.Benchmark)
    public static class CachedPlanState {
        @Param({"10", "100", "1000", "5000", "10000"})
        public int activities;

        @Param({"chain", "fanOutMerge"})
        public String topology;

        private NetworkPlan plan;

        @Setup(Level.Trial)
        public void setUp() throws Exception {
            String source = input(topology, activities);
            plan = parse(source);
            if (!plan.semanticErrors().isEmpty()) {
                throw new IllegalStateException(
                    String.join("; ", plan.semanticErrors())
                );
            }
            plan.projectDuration();
        }
    }

    private static NetworkPlan parse(String value) throws Exception {
        return new NetworkPlanReader().read(new StringReader(value));
    }

    /**
     * Writes plan text with the given number of activities, each lasting one
     * day. A "chain" is a straight line, every activity waiting for the one
     * before it. A "fanOutMerge" starts with one activity, splits into many
     * that can all run side by side, then joins them back into a single last
     * activity.
     */
    private static String input(String topology, int count) {
        StringBuilder source = new StringBuilder("project Synthetic {\n");
        for (int index = 0; index < count; index++) {
            source.append("activity A")
                .append(index)
                .append(" duration 1;\n");
        }
        if (topology.equals("chain")) {
            for (int index = 0; index < count - 1; index++) {
                edge(source, index, index + 1);
            }
        } else {
            for (int index = 1; index < count - 1; index++) {
                edge(source, 0, index);
                edge(source, index, count - 1);
            }
        }
        source.append("}\n");
        return source.toString();
    }

    private static void edge(StringBuilder source, int from, int to) {
        // will be like "dependency A0 -> A1;"
        // where from and to are the indices of the activities being connected.
        source.append("dependency A")
            .append(from)
            .append(" -> A")
            .append(to)
            .append(";\n");
    }
}

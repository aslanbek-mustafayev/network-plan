# Network Plan

A small DSL for project network plans, with critical path (CPM) analysis
implemented as JastAdd attributes over a JFlex/Beaver front end.

## Build

Generating the lexer, parser and AST is wired into `compileJava`, so a plain
build produces everything and runs the tests:

```shell
./gradlew :app:build
```

## Run

```shell
./gradlew :app:run --args="examples/example.network"
```

The input file is required. Paths in `--args` are relative to the repo root.

### Visualize the critical path

Pass `--visualize <output.html>` to also write a standalone HTML page with a
Mermaid diagram of the plan's activities and dependencies. Critical activities
and the dependencies between them are drawn in red, and every node is labelled
with its duration, ES/EF, LS/LF and TF. You can use it with:

```shell
./gradlew :app:run --args="examples/example-with-8-activities.network --visualize results/output.html"
```
```shell
./gradlew :app:run --args="examples/example-with-10-activities.network --visualize results/output.html"
```
```shell
./gradlew :app:run --args="examples/example-with-29-activities.network --visualize results/output.html"
```
```shell
./gradlew :app:run --args="examples/two-equal-critical-paths.network --visualize results/output.html"
```
```shell
./gradlew :app:run --args="examples/invalid-reference.network --visualize results/output.html"
```
```shell
./gradlew :app:run --args="examples/invalid-syntax.network --visualize results/output.html"
```

Generated files are written under `results/` and are not tracked by git.

## Examples

| File | Shows |
| --- | --- |
| `examples/example-with-8-activities.network` | 1st lecture example with a single critical path |
| `examples/example-with-10-activities.network` | 2nd lecture example with a single critical path |
| `examples/example-with-29-activities.network` | 3nd lecture example with a single critical path |
| `examples/example-with-10-activities.network` | lecture example with two ambiguous critical path, printed as a subgraph |
| `examples/invalid-syntax.network` | parse error handling |
| `examples/invalid-reference.network` | semantic error handling |

## Exit codes

| Code | Meaning |
| --- | --- |
| 0 | analysis printed |
| 1 | usage or file error |
| 2 | parse error |
| 3 | semantic errors |

## Benchmarks

Runtime and memory are measured with [JMH](https://github.com/openjdk/jmh). The
benchmarks live in `app/src/jmh/java` and are not part of a normal build.

```shell
./gradlew :app:benchmark
```

That is the full run: five benchmarks, each repeated for five plan sizes (10 up
to 10000 activities) and two plan shapes, which takes over an hour. For a short
smoke run instead:

```shell
./gradlew :app:benchmark -Pquick
```

Either way the results land in `app/build/reports/jmh/results.json`.

A much faster guard against performance and memory regressions runs on every
build as part of the test suite, see `PerformanceRegressionTest`.

### Charts

The charts are drawn by a small Python script. It is packaged in a container,
so nothing needs to be installed locally:

```shell
docker build -t network-plan-charts tools
docker run --rm -v "$PWD:/work" network-plan-charts
```

PNG files are written to `results/`, which is not tracked by git. 

On Linux, add `--user "$(id -u):$(id -g)"` to the `docker run` command so the
generated files do not be owned by root.

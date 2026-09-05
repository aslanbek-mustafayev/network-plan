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

package de.networkplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppTest {
    @Test
    void printsLectureResultAndReturnsZero(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("lecture.network");
        try (InputStream resource = AppTest.class.getResourceAsStream("/lecture-example.network")) {
            assertNotNull(resource);
            Files.copy(resource, input);
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int result = App.run(
            new String[] { input.toString() },
            new PrintStream(stdout),
            new PrintStream(stderr)
        );

        assertEquals(0, result);
        assertEquals("", stderr.toString());
        List<String> lines = stdout.toString().lines().toList();
        assertEquals("Activity  Dur  ES  EF  LS  LF  TF  FF  CF  IF  Critical", lines.get(3));
        assertEquals(
            List.of(
                "A          10   1  10   1  10   0   0   0   0  yes",
                "B          20  11  30  11  30   0   0   0   0  yes",
                "C           5  31  35  31  35   0   0   0   0  yes",
                "D          10  36  45  36  45   0   0   0   0  yes",
                "E          20  46  65  46  65   0   0   0   0  yes",
                "F          15  11  25  26  40  15  10   5  10  no",
                "G           5  36  40  41  45   5   5   0   0  no",
                "H          15  11  25  31  45  20  20   0  20  no"
            ),
            lines.subList(4, 12)
        );
        assertEquals("Project duration: 65", lines.get(13));
        assertEquals("Critical path: A -> B -> C -> D -> E", lines.get(14));
    }

    @Test
    void writesVisualizationWithoutChangingAnalysisOutput(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("lecture.network");
        try (InputStream resource = AppTest.class.getResourceAsStream("/lecture-example.network")) {
            assertNotNull(resource);
            Files.copy(resource, input);
        }
        Path visualization = directory.resolve("lecture.html");

        ByteArrayOutputStream baselineStdout = new ByteArrayOutputStream();
        int baselineResult = App.run(
            new String[] { input.toString() },
            new PrintStream(baselineStdout),
            new PrintStream(new ByteArrayOutputStream())
        );
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int result = App.run(
            new String[] { input.toString(), "--visualize", visualization.toString() },
            new PrintStream(stdout),
            new PrintStream(stderr)
        );

        assertEquals(0, baselineResult);
        assertEquals(0, result);
        assertEquals("", stderr.toString());
        assertEquals(baselineStdout.toString(), stdout.toString());
        String html = Files.readString(visualization);
        assertTrue(html.contains("<title>LectureExample — Network Plan</title>"));
        assertTrue(html.contains("<strong>Project duration:</strong> 65"));
        assertTrue(html.contains("class activity-0 critical"));
    }

    @Test
    void reportsVisualizationWriteFailure(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("lecture.network");
        try (InputStream resource = AppTest.class.getResourceAsStream("/lecture-example.network")) {
            assertNotNull(resource);
            Files.copy(resource, input);
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int result = App.run(
            new String[] { input.toString(), "--visualize", directory.toString() },
            new PrintStream(stdout),
            new PrintStream(stderr)
        );

        assertEquals(1, result);
        assertEquals("", stdout.toString());
        assertTrue(stderr.toString().contains("Visualization error:"));
    }

    @Test
    void identifiesInvalidVisualizationPath(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("lecture.network");
        try (java.io.InputStream resource = AppTest.class.getResourceAsStream("/lecture-example.network")) {
            assertNotNull(resource);
            Files.copy(resource, input);
        }

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int result = App.run(
            new String[] { input.toString(), "--visualize", "invalid\0visualization.html" },
            new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(stderr)
        );

        assertEquals(1, result);
        assertTrue(stderr.toString().contains("Visualization error:"));
    }

    @Test
    void printsBranchingCriticalSubgraphInDeclarationOrder(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("branching.network");
        Files.writeString(input, """
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

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int result = App.run(
            new String[] { input.toString() },
            new PrintStream(stdout),
            new PrintStream(stderr)
        );

        assertEquals(0, result);
        assertEquals("", stderr.toString());
        assertTrue(stdout.toString().replace("\r\n", "\n").endsWith("""
            Critical subgraph:
              Activities: A, B, C, D
              Dependencies:
                A -> B
                A -> C
                B -> D
                C -> D
            """));
    }

    @Test
    void reportsSemanticErrorsAndReturnsNonzero(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("invalid.network");
        Files.writeString(input, """
            project Invalid {
                activity A duration 1;
                dependency A -> X;
            }
            """);

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int result = App.run(
            new String[] { input.toString() },
            new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(stderr)
        );

        var expectedMessage = "Unknown activity 'X' used as dependency target";
        assertEquals(3, result);
        assertTrue(stderr.toString().contains(expectedMessage));
    }

    @Test
    void doesNotWriteVisualizationForInvalidPlan(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("invalid.network");
        Files.writeString(input, """
            project Invalid {
                activity A duration 1;
                dependency A -> X;
            }
            """);
        Path visualization = directory.resolve("invalid.html");

        int result = App.run(
            new String[] { input.toString(), "--visualize", visualization.toString() },
            new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(new ByteArrayOutputStream())
        );

        assertEquals(3, result);
        assertTrue(Files.notExists(visualization));
    }

    @Test
    void reportsUsageError() {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int result = App.run(
            new String[0],
            new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(stderr)
        );

        assertEquals(1, result);
        assertTrue(stderr.toString().contains("Usage:"));
    }

    @Test
    void reportsMissingFile(@TempDir Path directory) {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int result = App.run(
            new String[] { directory.resolve("missing.network").toString() },
            new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(stderr)
        );

        assertEquals(1, result);
        assertTrue(stderr.toString().contains("File error:"));
    }

    @Test
    void reportsSyntaxError(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("syntax-error.network");
        Files.writeString(input, """
            project Invalid {
                activity A duration ;
            }
            """);

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int result = App.run(
            new String[] { input.toString() },
            new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(stderr)
        );

        assertEquals(2, result);
        assertTrue(stderr.toString().contains("Parse error:"));
    }
}

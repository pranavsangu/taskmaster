package com.trustdesk;

import com.trustdesk.ai.DraftInput;
import com.trustdesk.ai.TriageInput;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The project's non-negotiable rules (CLAUDE.md), checked against the source code itself, so that a
 * future change which breaks one fails the build instead of slipping through. Plain JUnit: no Spring.
 */
class SafetyRulesTest {

    private static final Path MAIN = Path.of("src/main/java/com/trustdesk");
    private static final Path REPO = Path.of("..");

    private static List<Path> javaFiles(String... packages) throws IOException {
        List<Path> files = new java.util.ArrayList<>();
        for (String pkg : packages) {
            try (Stream<Path> s = Files.walk(MAIN.resolve(pkg))) {
                s.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            }
        }
        return files;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Source code without comments: rules are about what the code does, not what comments mention. */
    private static String code(Path file) {
        return read(file).replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    // ---- "Do not use the expected labels as hidden answers in your AI flow" ----

    @Test
    void codeThatTalksToTheAiNeverReadsEvaluationLabels() throws IOException {
        // Everything on the path from a request to the AI and back. Only `eval` and `seed` may read labels.
        List<Path> aiPath = javaFiles("ai", "triage", "draft", "guardrail", "policy", "action", "kb", "trace", "ticket");
        assertThat(aiPath).isNotEmpty();
        for (Path file : aiPath) {
            if (file.getFileName().toString().startsWith("TicketEvalLabel")) {
                continue; // the label entity and its repository themselves
            }
            assertThat(code(file)).as(file.toString())
                    .doesNotContain("TicketEvalLabel")
                    .doesNotContain("com.trustdesk.eval.")
                    .doesNotContain("EvalCase");
        }
    }

    @Test
    void aiInputsHaveNoExpectedFields() {
        Stream.of(TriageInput.class, DraftInput.class).forEach(type ->
                assertThat(Arrays.stream(type.getRecordComponents()).map(c -> c.getName().toLowerCase()))
                        .as(type.getSimpleName())
                        .noneMatch(name -> name.contains("expected") || name.contains("label")));
    }

    // ---- "Return/warranty windows are calculated from each ticket's created_at, never today's date" ----

    @Test
    void policyWindowCodeNeverReadsTheClock() throws IOException {
        Pattern clock = Pattern.compile("\\.now\\(|Clock\\b|currentTimeMillis|LocalDate\\.now|new Date\\(");
        for (Path file : javaFiles("policy")) {
            assertThat(clock.matcher(code(file)).find()).as(file + " must not use the current date").isFalse();
        }
    }

    // ---- "Never put API keys in code; use environment variables" ----

    @Test
    void noRealApiKeyIsCommittedAnywhere() throws IOException {
        // Real Groq/OpenAI keys are long; the short fake keys in tests are allowed.
        Pattern realKey = Pattern.compile("gsk_[A-Za-z0-9]{40,}|sk-[A-Za-z0-9]{40,}");
        List<Path> roots = List.of(REPO.resolve("backend/src"), REPO.resolve("frontend"), REPO.resolve("documents"),
                REPO.resolve("CLAUDE.md"));
        for (Path root : roots) {
            try (Stream<Path> s = Files.walk(root)) {
                s.filter(Files::isRegularFile).forEach(file ->
                        assertThat(realKey.matcher(read(file)).find()).as(file + " contains what looks like an API key").isFalse());
            }
        }
    }

    @Test
    void apiKeyComesOnlyFromEnvironmentOrIgnoredSecretsFile() throws IOException {
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));
        assertThat(properties).contains("trustdesk.ai.groq.api-key=${GROQ_API_KEY:}");
        assertThat(properties).contains("spring.config.import=optional:file:secrets.properties");
        assertThat(Files.readAllLines(Path.of(".gitignore"))).contains("secrets.properties");
    }
}

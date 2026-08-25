package cn.gov.enterprise.modules.workflow.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;

/** Single test-only source for the frozen RC2 controlled-canary time contract. */
final class Rc2ControlledCanaryFixtureContract {
    static final String EFFECTIVE_AT_ENV = "RC2_CANARY_DIRECTORY_EFFECTIVE_AT";
    static final Path REPOSITORY_ROOT = repositoryRoot();
    static final Path MANIFEST_PATH = REPOSITORY_ROOT.resolve(
            "database/test-fixtures/rc2/rc2-canary-fixture-manifest.json");
    static final Instant DIRECTORY_EFFECTIVE_AT;
    static final Instant ASSIGNMENT_EFFECTIVE_TO;

    static {
        try {
            JsonNode effectiveWindow = new ObjectMapper().readTree(
                    Files.readString(MANIFEST_PATH)).path("effectiveWindow");
            DIRECTORY_EFFECTIVE_AT = Instant.parse(required(
                    effectiveWindow, "directoryEffectiveAt"));
            ASSIGNMENT_EFFECTIVE_TO = Instant.parse(required(
                    effectiveWindow, "assignmentEffectiveTo"));
            if (!ASSIGNMENT_EFFECTIVE_TO.isAfter(DIRECTORY_EFFECTIVE_AT)) {
                throw new IllegalStateException("fixture assignment window excludes effectiveAt");
            }
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private Rc2ControlledCanaryFixtureContract() {
    }

    static void requireUnified(Instant... values) {
        if (values == null || values.length == 0
                || Arrays.stream(values).anyMatch(value -> value == null
                        || !DIRECTORY_EFFECTIVE_AT.equals(value))) {
            throw new IllegalArgumentException("RC2_FIXTURE_EFFECTIVE_AT_MISMATCH");
        }
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing fixture contract field: " + field);
        }
        return value;
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve(
                "database/test-fixtures/rc2/rc2-canary-fixture-manifest.json"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve(
                "database/test-fixtures/rc2/rc2-canary-fixture-manifest.json"))) {
            return parent;
        }
        throw new IllegalStateException("RC2 fixture repository root not found");
    }
}

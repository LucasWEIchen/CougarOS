package com.centralbrain.runtime.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.graph.CheckpointEnvelope;
import com.centralbrain.runtime.graph.CheckpointSerializer;
import com.centralbrain.runtime.graph.CheckpointSerializer.CheckpointException;
import com.centralbrain.runtime.graph.CheckpointSerializer.ErrorCode;
import com.centralbrain.runtime.graph.CheckpointSerializer.PayloadCodec;
import com.centralbrain.runtime.graph.CheckpointSerializer.Registration;
import com.centralbrain.runtime.graph.CheckpointValue;
import com.centralbrain.runtime.graph.JsonPrimitiveCheckpointSerializer;
import com.centralbrain.runtime.scenario.ScenarioManifestParser;
import com.centralbrain.runtime.scenario.ScenarioManifestParser.ParseException;
import com.centralbrain.runtime.tools.ToolManifest;
import com.centralbrain.runtime.tools.ToolManifest.FieldSchema;
import com.centralbrain.runtime.tools.ToolManifest.HealthContract;
import com.centralbrain.runtime.tools.ToolManifest.IdempotencyMode;
import com.centralbrain.runtime.tools.ToolManifest.ObjectSchema;
import com.centralbrain.runtime.tools.ToolManifest.RiskClass;
import com.centralbrain.runtime.tools.ToolSchemaValidator;
import com.centralbrain.runtime.tools.ToolSchemaValidator.ValidationException;

import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ParserSecurityCorpusContractTest {
    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);
    private static final String SHA_C = "c".repeat(64);
    private static final Path COLD_MANIFEST = Path.of(
            "src/main/assets/scenarios/scene.comfort.cold.v1.json");

    @Test
    public void fixedCatalogHasThreeSurfacesAndEighteenUniqueCases() {
        assertEquals(3, ParserSecurityCorpusContract.SURFACE_COUNT);
        assertEquals(6, ParserSecurityCorpusContract.CASES_PER_SURFACE);
        assertEquals(18, ParserSecurityCorpusContract.CASE_COUNT);
        assertEquals(18, ParserSecurityCorpusContract.cases().size());
        assertEquals(
                18,
                ParserSecurityCorpusContract.cases().stream()
                        .map(ParserSecurityCorpusContract.CorpusCase::getCaseId)
                        .distinct()
                        .count());
        assertTrue(ParserSecurityCorpusContract.corpusDigest().matches("[0-9a-f]{64}"));
    }

    @Test
    public void checkpointCorpusRejectsSixHostileInputsWithExactErrors() {
        JsonPrimitiveCheckpointSerializer serializer = checkpointSerializer();
        String valid = new String(validCheckpoint(serializer), StandardCharsets.UTF_8);

        assertCheckpoint(
                serializer,
                "checkpoint.malformed_json.v1",
                "{".getBytes(StandardCharsets.UTF_8));
        assertCheckpoint(
                serializer,
                "checkpoint.duplicate_field.v1",
                valid.replaceFirst("\\{", "{\"schemaVersion\":1,")
                        .getBytes(StandardCharsets.UTF_8));
        assertCheckpoint(
                serializer,
                "checkpoint.unknown_field.v1",
                valid.replace(
                                "\"createdAt\":\"2000\"",
                                "\"unknownField\":true,\"createdAt\":\"2000\"")
                        .getBytes(StandardCharsets.UTF_8));
        assertCheckpoint(
                serializer,
                "checkpoint.oversize.v1",
                new byte[CheckpointSerializer.MAX_CHECKPOINT_BYTES + 1]);
        assertCheckpoint(
                serializer,
                "checkpoint.digest_tamper.v1",
                valid.replace(SHA_A, "d".repeat(64)).getBytes(StandardCharsets.UTF_8));
        assertCheckpoint(
                serializer,
                "checkpoint.privileged_path_key.v1",
                valid.replace("\"value\":\"ok\"", "\"filePath\":\"../private\"")
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void scenarioManifestCorpusRejectsSixHostileInputsWithExactErrors()
            throws Exception {
        ScenarioManifestParser parser = new ScenarioManifestParser();
        String valid = new String(Files.readAllBytes(COLD_MANIFEST), StandardCharsets.UTF_8);

        assertScenario(
                parser,
                "scenario.invalid_source_path.v1",
                "../scene.json",
                valid.getBytes(StandardCharsets.UTF_8));
        assertScenario(
                parser,
                "scenario.unknown_field.v1",
                "unknown-field.json",
                valid.replaceFirst("\\{", "{\"unknown\":true,")
                        .getBytes(StandardCharsets.UTF_8));
        assertScenario(
                parser,
                "scenario.duplicate_field.v1",
                "duplicate-field.json",
                valid.replaceFirst(
                                "\"schemaVersion\": 1,",
                                "\"schemaVersion\": 1, \"schemaVersion\": 1,")
                        .getBytes(StandardCharsets.UTF_8));
        byte[] oversize = new byte[ScenarioManifestParser.MAX_MANIFEST_BYTES + 1];
        Arrays.fill(oversize, (byte) ' ');
        assertScenario(parser, "scenario.oversize.v1", "oversize.json", oversize);
        assertScenario(
                parser,
                "scenario.trailing_json.v1",
                "trailing.json",
                (valid + "{}").getBytes(StandardCharsets.UTF_8));
        String depthBomb = "{\"x\":" + "[".repeat(18) + "0" + "]".repeat(18) + "}";
        assertScenario(
                parser,
                "scenario.depth_bomb.v1",
                "depth-bomb.json",
                depthBomb.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void toolSchemaCorpusRejectsSixHostileInputsWithExactErrors() {
        ToolSchemaValidator validator = new ToolSchemaValidator();
        ToolManifest manifest = toolManifest();
        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("requestDigest", SHA_A);
        withNull.put("targetZone", null);
        withNull.put("temperatureDeciC", 225L);

        assertTool(
                "tool_schema.missing_field.v1",
                () -> validator.validateInput(manifest, Map.of()));
        assertTool(
                "tool_schema.unknown_field.v1",
                () -> validator.validateInput(manifest, Map.of(
                        "requestDigest", SHA_A,
                        "targetZone", "driver",
                        "temperatureDeciC", 225L,
                        "unknown", true)));
        assertTool(
                "tool_schema.null_value.v1",
                () -> validator.validateInput(manifest, withNull));
        assertTool(
                "tool_schema.type_confusion.v1",
                () -> validator.validateInput(manifest, Map.of(
                        "requestDigest", SHA_A,
                        "targetZone", "driver",
                        "temperatureDeciC", 225)));
        assertTool(
                "tool_schema.value_boundary.v1",
                () -> validator.validateInput(manifest, Map.of(
                        "requestDigest", SHA_A,
                        "targetZone", "x".repeat(33),
                        "temperatureDeciC", 225L)));
        assertTool(
                "tool_schema.payload_oversize.v1",
                () -> validator.validateInput(
                        smallToolManifest(),
                        Map.of("first", "1234567890", "second", "1234567890")));
    }

    @Test
    public void corpusIsMetadataOnlyAndDoesNotClaimBroaderSecurityQualification() {
        assertTrue(ParserSecurityCorpusContract.isDeterministicHostRegressionVerified());
        assertFalse(ParserSecurityCorpusContract.isCoverageGuidedFuzzComplete());
        assertFalse(ParserSecurityCorpusContract.isAidlIdentityReviewComplete());
        assertFalse(ParserSecurityCorpusContract.isSignaturePolicyReviewComplete());
        assertFalse(ParserSecurityCorpusContract.isAndroid13Arm64Verified());
        assertFalse(ParserSecurityCorpusContract.isRuntimeWired());
        assertFalse(ParserSecurityCorpusContract.isHardwareAccessed());
        assertFalse(ParserSecurityCorpusContract.isProductionReady());
        assertFalse(ParserSecurityCorpusContract.isTargetHardwareValidated());
    }

    private static void assertCheckpoint(
            JsonPrimitiveCheckpointSerializer serializer,
            String caseId,
            byte[] hostileInput) {
        ErrorCode expected = ErrorCode.valueOf(expectedError(caseId));
        CheckpointException exception = assertThrows(
                caseId,
                CheckpointException.class,
                () -> serializer.deserialize(hostileInput));
        assertEquals(caseId, expected, exception.getErrorCode());
    }

    private static void assertScenario(
            ScenarioManifestParser parser,
            String caseId,
            String sourceName,
            byte[] hostileInput) {
        ScenarioManifestParser.ErrorCode expected =
                ScenarioManifestParser.ErrorCode.valueOf(expectedError(caseId));
        ParseException exception = assertThrows(
                caseId,
                ParseException.class,
                () -> parser.parse(sourceName, hostileInput));
        assertEquals(caseId, expected, exception.getErrorCode());
    }

    private static void assertTool(String caseId, ThrowingRunnable operation) {
        ToolSchemaValidator.ErrorCode expected =
                ToolSchemaValidator.ErrorCode.valueOf(expectedError(caseId));
        ValidationException exception = assertThrows(
                caseId, ValidationException.class, operation);
        assertEquals(caseId, expected, exception.getErrorCode());
    }

    private static String expectedError(String caseId) {
        return ParserSecurityCorpusContract.requireCase(caseId).getExpectedErrorCode();
    }

    private static JsonPrimitiveCheckpointSerializer checkpointSerializer() {
        return new JsonPrimitiveCheckpointSerializer(List.of(new Registration<>(
                "security.primitive.state",
                1,
                CheckpointValue.class,
                new PayloadCodec<CheckpointValue>() {
                    @Override
                    public CheckpointValue encode(CheckpointValue value) {
                        return value;
                    }

                    @Override
                    public CheckpointValue decode(CheckpointValue value) {
                        return value;
                    }
                })));
    }

    private static byte[] validCheckpoint(JsonPrimitiveCheckpointSerializer serializer) {
        CheckpointEnvelope envelope = serializer.create(
                "security.primitive.state",
                1,
                "security_node",
                SHA_A,
                SHA_B,
                CheckpointValue.map(Map.of("value", CheckpointValue.string("ok"))),
                2_000L);
        return serializer.serialize(envelope);
    }

    private static ToolManifest toolManifest() {
        ObjectSchema input = new ObjectSchema(
                "tool.input.security.v1",
                1,
                512,
                List.of(
                        FieldSchema.sha256DigestField("requestDigest", true),
                        FieldSchema.stringField("targetZone", true, 32),
                        FieldSchema.integerField("temperatureDeciC", true, 160L, 300L)));
        return manifest(input, "tool.security.validate.v1");
    }

    private static ToolManifest smallToolManifest() {
        ObjectSchema input = new ObjectSchema(
                "tool.input.security-small.v1",
                1,
                20,
                List.of(
                        FieldSchema.stringField("first", true, 16),
                        FieldSchema.stringField("second", true, 16)));
        return manifest(input, "tool.security.small.v1");
    }

    private static ToolManifest manifest(ObjectSchema input, String toolId) {
        ObjectSchema output = new ObjectSchema(
                "tool.output.security.v1",
                1,
                128,
                List.of(FieldSchema.stringField("status", true, 16)));
        return new ToolManifest(
                1,
                toolId,
                1,
                "runtime.builtin",
                input,
                output,
                "security.validation",
                RiskClass.LOW,
                500L,
                IdempotencyMode.READ_ONLY,
                new HealthContract("health.security.validation.v1", 5_000L, true));
    }
}

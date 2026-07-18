package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.model.StructuredModelOutput.AcceptedOutput;
import com.centralbrain.runtime.model.StructuredModelOutput.ErrorCode;
import com.centralbrain.runtime.model.StructuredModelOutput.Parameter;
import com.centralbrain.runtime.model.StructuredModelOutput.ValidationException;
import com.centralbrain.runtime.scenario.ScenarioCatalog;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.schema.SignalValue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StructuredModelOutputTest {
    private static final Path SCENARIOS = Path.of("src/main/assets/scenarios");
    private static final String TRACE = digest('a');
    private static final String INPUT = digest('b');

    @Test
    public void acceptsCatalogBoundTypedParametersAndStableDigest() throws Exception {
        AcceptedOutput first = StructuredModelOutput.validate(
                request(ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE),
                bytes(validColdOutput()),
                scenarioCatalog(),
                CapabilityCatalog.stage2Defaults());
        AcceptedOutput reordered = StructuredModelOutput.validate(
                request(ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE),
                bytes(validColdOutputReordered()),
                scenarioCatalog(),
                CapabilityCatalog.stage2Defaults());

        assertEquals(1, first.getSchemaVersion());
        assertEquals(StructuredModelOutput.OUTPUT_SCHEMA_ID, first.getOutputSchemaId());
        assertEquals("scene.comfort.cold.v1", first.getScenarioId());
        assertEquals(3, first.getParameters().size());
        assertEquals(first.getOutputDigest(), reordered.getOutputDigest());
        assertEquals(TRACE, first.getTraceId());
        assertTrue(first.getScenarioArtifactDigest().matches("[0-9a-f]{64}"));
        assertTrue(first.getScenarioCatalogDigest().matches("[0-9a-f]{64}"));
        assertTrue(first.getCapabilityCatalogDigest().matches("[0-9a-f]{64}"));

        Parameter temperature = find(
                first, VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE);
        assertEquals(SignalValue.ScalarType.DECIMAL, temperature.getScalarType());
        assertEquals("row1.driver", temperature.getArea());
        assertEquals(22.5, temperature.getDecimalValue(), 0.0);
        assertEquals(2L, find(first, VehicleCapability.CapabilityId.SEAT_HEATING_LEVEL)
                .getIntegerValue());
        assertTrue(find(first, VehicleCapability.CapabilityId.HVAC_POWER).getBooleanValue());
    }

    @Test
    public void rejectsUnknownScenarioAndCapability() throws Exception {
        assertError(
                ErrorCode.UNKNOWN_SCENARIO,
                validColdOutput().replace(
                        "scene.comfort.cold.v1", "scene.unknown.intent.v1"));
        assertError(
                ErrorCode.UNKNOWN_CAPABILITY,
                validColdOutput().replace(
                        "vehicle.hvac.target_temperature", "vehicle.unknown.command"));
    }

    @Test
    public void rejectsCapabilityOutsideScenarioAndUnregisteredArea() throws Exception {
        assertError(
                ErrorCode.CAPABILITY_NOT_REGISTERED_FOR_SCENARIO,
                validColdOutput().replace(
                        "vehicle.seat.heating", "vehicle.seat.recline"));
        assertError(
                ErrorCode.INVALID_AREA,
                validColdOutput().replaceFirst("row1.driver", "row3.driver"));
    }

    @Test
    public void rejectsWrongScalarTypeRangeStepAndDuplicateParameter() throws Exception {
        assertError(
                ErrorCode.TYPE_MISMATCH,
                validColdOutput().replace("\"value\":22.5", "\"value\":\"22.5\""));
        assertError(
                ErrorCode.VALUE_OUT_OF_RANGE,
                validColdOutput().replace("\"value\":22.5", "\"value\":31.0"));
        assertError(
                ErrorCode.VALUE_OUT_OF_RANGE,
                validColdOutput().replace("\"value\":22.5", "\"value\":22.2"));
        String duplicate = validColdOutput().replace(
                "] ,\"summary\"",
                ",{" +
                        "\"capabilityId\":\"vehicle.hvac.power\"," +
                        "\"area\":\"cabin\",\"value\":false}] ,\"summary\"");
        assertError(ErrorCode.DUPLICATE_PARAMETER, duplicate);
    }

    @Test
    public void strictJsonRejectsUnknownDuplicateTrailingAndOversize() throws Exception {
        assertError(
                ErrorCode.UNKNOWN_FIELD,
                validColdOutput().replace("\"summary\"", "\"command\":\"shell\",\"summary\""));
        assertError(
                ErrorCode.DUPLICATE_FIELD,
                validColdOutput().replace(
                        "\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"));
        assertError(ErrorCode.MALFORMED_JSON, validColdOutput() + " true");

        ValidationException malformedUtf8 = assertThrows(
                ValidationException.class,
                () -> StructuredModelOutput.validate(
                        request(ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE),
                        new byte[] {(byte) 0xc3, 0x28},
                        scenarioCatalog(),
                        CapabilityCatalog.stage2Defaults()));
        assertEquals(ErrorCode.MALFORMED_JSON, malformedUtf8.getErrorCode());

        byte[] oversized = new byte[StructuredModelOutput.MAX_OUTPUT_BYTES + 1];
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> StructuredModelOutput.validate(
                        request(ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE),
                        oversized,
                        scenarioCatalog(),
                        CapabilityCatalog.stage2Defaults()));
        assertEquals(ErrorCode.OVERSIZE, exception.getErrorCode());
    }

    @Test
    public void requestPurposeAndCapabilityFailClosed() throws Exception {
        ValidationException capability = assertThrows(
                ValidationException.class,
                () -> StructuredModelOutput.validate(
                        request(ModelContractV2.RequiredCapability.TEXT_GENERATION),
                        bytes(validColdOutput()),
                        scenarioCatalog(),
                        CapabilityCatalog.stage2Defaults()));
        assertEquals(ErrorCode.REQUEST_CAPABILITY_MISMATCH, capability.getErrorCode());

        ModelContractV2.ModelRequest dialogue = new ModelContractV2.ModelRequest(
                "request-1",
                ModelContractV2.Purpose.USER_DIALOGUE,
                ModelContractV2.PrivacyClass.INTERNAL,
                new ModelContractV2.LatencyBudget(1_500),
                new ModelContractV2.TokenBudget(256, 256, 512),
                ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE,
                ModelContractV2.FallbackPolicy.NO_FALLBACK,
                TRACE,
                INPUT);
        ValidationException purpose = assertThrows(
                ValidationException.class,
                () -> StructuredModelOutput.validate(
                        dialogue,
                        bytes(validColdOutput()),
                        scenarioCatalog(),
                        CapabilityCatalog.stage2Defaults()));
        assertEquals(ErrorCode.REQUEST_CAPABILITY_MISMATCH, purpose.getErrorCode());
    }

    @Test
    public void summaryAndAcceptedOutputNeverGrantExecutionAuthority() throws Exception {
        AcceptedOutput output = StructuredModelOutput.validate(
                request(ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE),
                bytes(validColdOutput()),
                scenarioCatalog(),
                CapabilityCatalog.stage2Defaults());
        assertEquals("已生成舒适调节建议", output.getSummary());
        assertFalse(output.isActionAuthorizationGranted());
        assertFalse(output.isApprovalDecisionGranted());
        assertFalse(output.isEffectDispatchRequested());

        assertError(
                ErrorCode.INVALID_SUMMARY,
                validColdOutput().replace("已生成舒适调节建议", " padded "));
        assertError(
                ErrorCode.INVALID_SUMMARY,
                validColdOutput().replace("已生成舒适调节建议", "line\\nfeed"));
        assertFalse(StructuredModelOutput.snapshot().isRepairPromptEnabled());
        assertFalse(StructuredModelOutput.snapshot().isRuntimeWired());
        assertFalse(StructuredModelOutput.snapshot().isModelInvoked());
        assertNotEquals(
                StructuredModelOutput.PROMPT_CONTRACT_ID,
                StructuredModelOutput.OUTPUT_SCHEMA_ID);
    }

    @Test
    public void publishedJsonSchemaHasClosedShape() throws Exception {
        String schema = new String(
                Files.readAllBytes(Path.of(
                        "src/main/assets/model/model-structured-output-v1.schema.json")),
                StandardCharsets.UTF_8);
        assertTrue(schema.contains("\"$id\": \"centralbrain.model.scenario-output.v1\""));
        assertTrue(schema.contains("\"additionalProperties\": false"));
        assertTrue(schema.contains("\"maxItems\": 16"));
        assertTrue(schema.contains("\"maxLength\": 256"));
    }

    private static void assertError(ErrorCode expected, String output) throws Exception {
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> StructuredModelOutput.validate(
                        request(ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE),
                        bytes(output),
                        scenarioCatalog(),
                        CapabilityCatalog.stage2Defaults()));
        assertEquals(expected, exception.getErrorCode());
    }

    private static Parameter find(
            AcceptedOutput output, VehicleCapability.CapabilityId capabilityId) {
        return output.getParameters().stream()
                .filter(parameter -> parameter.getCapabilityId() == capabilityId)
                .findFirst()
                .orElseThrow();
    }

    private static ScenarioCatalog scenarioCatalog() throws IOException {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                SCENARIOS, "scene.*.json")) {
            for (Path path : stream) {
                assets.put(path.getFileName().toString(), Files.readAllBytes(path));
            }
        }
        return ScenarioCatalog.load(assets);
    }

    private static ModelContractV2.ModelRequest request(
            ModelContractV2.RequiredCapability capability) {
        return new ModelContractV2.ModelRequest(
                "request-1",
                ModelContractV2.Purpose.SCENARIO_REASONING,
                ModelContractV2.PrivacyClass.INTERNAL,
                new ModelContractV2.LatencyBudget(1_500),
                new ModelContractV2.TokenBudget(256, 256, 512),
                capability,
                ModelContractV2.FallbackPolicy.NO_FALLBACK,
                TRACE,
                INPUT);
    }

    private static String validColdOutput() {
        return "{\"schemaVersion\":1,"
                + "\"scenarioId\":\"scene.comfort.cold.v1\","
                + "\"parameters\":["
                + "{\"capabilityId\":\"vehicle.hvac.target_temperature\","
                + "\"area\":\"row1.driver\",\"value\":22.5},"
                + "{\"capabilityId\":\"vehicle.hvac.power\","
                + "\"area\":\"cabin\",\"value\":true},"
                + "{\"capabilityId\":\"vehicle.seat.heating\","
                + "\"area\":\"row1.driver\",\"value\":2}"
                + "] ,\"summary\":\"已生成舒适调节建议\"}";
    }

    private static String validColdOutputReordered() {
        return "{\"summary\":\"已生成舒适调节建议\","
                + "\"parameters\":["
                + "{\"value\":2,\"area\":\"row1.driver\","
                + "\"capabilityId\":\"vehicle.seat.heating\"},"
                + "{\"value\":true,\"capabilityId\":\"vehicle.hvac.power\","
                + "\"area\":\"cabin\"},"
                + "{\"area\":\"row1.driver\",\"value\":22.500,"
                + "\"capabilityId\":\"vehicle.hvac.target_temperature\"}],"
                + "\"scenarioId\":\"scene.comfort.cold.v1\",\"schemaVersion\":1}";
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String digest(char value) {
        return String.valueOf(value).repeat(64);
    }
}

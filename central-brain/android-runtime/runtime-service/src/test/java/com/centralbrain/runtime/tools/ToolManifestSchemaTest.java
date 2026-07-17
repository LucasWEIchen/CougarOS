package com.centralbrain.runtime.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.tools.ToolManifest.FieldSchema;
import com.centralbrain.runtime.tools.ToolManifest.HealthContract;
import com.centralbrain.runtime.tools.ToolManifest.IdempotencyMode;
import com.centralbrain.runtime.tools.ToolManifest.ObjectSchema;
import com.centralbrain.runtime.tools.ToolManifest.RiskClass;
import com.centralbrain.runtime.tools.ToolSchemaValidator.ErrorCode;
import com.centralbrain.runtime.tools.ToolSchemaValidator.ValidationException;

import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolManifestSchemaTest {
    private static final String DIGEST = "a".repeat(64);

    @Test
    public void manifestIsImmutableVersionedAndHasDeterministicContractDigest() {
        ToolManifest first = manifest(inputFields());
        ToolManifest second = manifest(List.of(
                FieldSchema.integerField("temperatureDeciC", true, 160L, 300L),
                FieldSchema.stringField("targetZone", true, 32),
                FieldSchema.sha256DigestField("requestDigest", true)));

        assertEquals(ToolManifest.SCHEMA_VERSION, first.getSchemaVersion());
        assertEquals(first.getContractDigest(), second.getContractDigest());
        assertTrue(first.getContractDigest().matches("[0-9a-f]{64}"));
        assertEquals("runtime.builtin", first.getOwnerId());
        assertEquals("vehicle.hvac.temperature", first.getCapabilityId());
        assertEquals(RiskClass.MEDIUM, first.getRiskClass());
        assertEquals(IdempotencyMode.TOKEN_REQUIRED, first.getIdempotencyMode());
        assertTrue(first.getHealthContract().isRequiredBeforeUse());
        assertThrows(
                UnsupportedOperationException.class,
                () -> first.getInputSchema().getFields().clear());

        ToolManifest changed = new ToolManifest(
                1,
                "tool.cockpit.hvac.set.v1",
                1,
                "runtime.builtin",
                first.getInputSchema(),
                first.getOutputSchema(),
                "vehicle.hvac.temperature",
                RiskClass.HIGH,
                2_500L,
                IdempotencyMode.TOKEN_REQUIRED,
                first.getHealthContract());
        assertNotEquals(first.getContractDigest(), changed.getContractDigest());
    }

    @Test
    public void validatesExactBoundedInputAndOutputWithoutMutation() {
        ToolManifest manifest = manifest(inputFields());
        ToolSchemaValidator validator = new ToolSchemaValidator();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("requestDigest", DIGEST);
        source.put("targetZone", "driver");
        source.put("temperatureDeciC", 225L);

        Map<String, Object> input = validator.validateInput(manifest, source);
        Map<String, Object> output = validator.validateOutput(
                manifest, Map.of("status", "accepted"));

        assertEquals(source, input);
        assertEquals("accepted", output.get("status"));
        assertFalse(output.containsKey("observationDigest"));
        assertThrows(UnsupportedOperationException.class, () -> input.put("extra", true));
    }

    @Test
    public void rejectsMissingUnknownNullAndExactTypeMismatch() {
        ToolManifest manifest = manifest(inputFields());
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertCode(
                ErrorCode.MISSING_FIELD,
                () -> validator.validateInput(manifest, Map.of()));
        assertCode(
                ErrorCode.UNKNOWN_FIELD,
                () -> validator.validateInput(manifest, Map.of(
                        "requestDigest", DIGEST,
                        "targetZone", "driver",
                        "temperatureDeciC", 225L,
                        "unknown", true)));
        assertCode(
                ErrorCode.UNKNOWN_FIELD,
                () -> validator.validateInput(manifest, Map.of(7L, true)));
        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("requestDigest", DIGEST);
        withNull.put("targetZone", null);
        withNull.put("temperatureDeciC", 225L);
        assertCode(
                ErrorCode.NULL_VALUE,
                () -> validator.validateInput(manifest, withNull));
        assertCode(
                ErrorCode.TYPE_MISMATCH,
                () -> validator.validateInput(manifest, Map.of(
                        "requestDigest", DIGEST,
                        "targetZone", "driver",
                        "temperatureDeciC", 225)));
    }

    @Test
    public void rejectsDigestStringIntegerAndAggregatePayloadBounds() {
        ToolManifest manifest = manifest(inputFields());
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertCode(
                ErrorCode.VALUE_OUT_OF_RANGE,
                () -> validator.validateInput(manifest, Map.of(
                        "requestDigest", "not-a-digest",
                        "targetZone", "driver",
                        "temperatureDeciC", 225L)));
        assertCode(
                ErrorCode.VALUE_OUT_OF_RANGE,
                () -> validator.validateInput(manifest, Map.of(
                        "requestDigest", DIGEST,
                        "targetZone", "x".repeat(33),
                        "temperatureDeciC", 225L)));
        assertCode(
                ErrorCode.VALUE_OUT_OF_RANGE,
                () -> validator.validateInput(manifest, Map.of(
                        "requestDigest", DIGEST,
                        "targetZone", "driver",
                        "temperatureDeciC", 301L)));

        ObjectSchema small = new ObjectSchema(
                "tool.input.small.v1",
                1,
                20,
                List.of(
                        FieldSchema.stringField("first", true, 16),
                        FieldSchema.stringField("second", true, 16)));
        ToolManifest bounded = new ToolManifest(
                1,
                "tool.cockpit.small.read.v1",
                1,
                "runtime.builtin",
                small,
                manifest.getOutputSchema(),
                "cockpit.small.read",
                RiskClass.LOW,
                100L,
                IdempotencyMode.READ_ONLY,
                manifest.getHealthContract());
        assertCode(
                ErrorCode.PAYLOAD_TOO_LARGE,
                () -> validator.validateInput(
                        bounded, Map.of("first", "1234567890", "second", "1234567890")));
    }

    @Test
    public void rejectsNonCanonicalManifestAndNonFailClosedHealth() {
        ToolManifest valid = manifest(inputFields());
        assertThrows(
                IllegalArgumentException.class,
                () -> new HealthContract("health.vehicle.hvac.v1", 5_000L, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolManifest(
                        2,
                        "tool.cockpit.hvac.set.v1",
                        1,
                        "runtime.builtin",
                        valid.getInputSchema(),
                        valid.getOutputSchema(),
                        "vehicle.hvac.temperature",
                        RiskClass.MEDIUM,
                        2_500L,
                        IdempotencyMode.TOKEN_REQUIRED,
                        valid.getHealthContract()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolManifest(
                        1,
                        "tool.cockpit.hvac.set.v2",
                        1,
                        "runtime.builtin",
                        valid.getInputSchema(),
                        valid.getOutputSchema(),
                        "vehicle.hvac.temperature",
                        RiskClass.MEDIUM,
                        2_500L,
                        IdempotencyMode.TOKEN_REQUIRED,
                        valid.getHealthContract()));
    }

    private static ToolManifest manifest(List<FieldSchema> inputFields) {
        ObjectSchema input = new ObjectSchema(
                "tool.input.hvac-set.v1", 1, 512, inputFields);
        ObjectSchema output = new ObjectSchema(
                "tool.output.hvac-set.v1",
                1,
                256,
                List.of(
                        FieldSchema.stringField("status", true, 16),
                        FieldSchema.sha256DigestField("observationDigest", false)));
        return new ToolManifest(
                1,
                "tool.cockpit.hvac.set.v1",
                1,
                "runtime.builtin",
                input,
                output,
                "vehicle.hvac.temperature",
                RiskClass.MEDIUM,
                2_500L,
                IdempotencyMode.TOKEN_REQUIRED,
                new HealthContract("health.vehicle.hvac.v1", 5_000L, true));
    }

    private static List<FieldSchema> inputFields() {
        return List.of(
                FieldSchema.sha256DigestField("requestDigest", true),
                FieldSchema.stringField("targetZone", true, 32),
                FieldSchema.integerField("temperatureDeciC", true, 160L, 300L));
    }

    private static void assertCode(ErrorCode code, ThrowingRunnable runnable) {
        ValidationException exception = assertThrows(ValidationException.class, runnable);
        assertEquals(code, exception.getErrorCode());
    }

}

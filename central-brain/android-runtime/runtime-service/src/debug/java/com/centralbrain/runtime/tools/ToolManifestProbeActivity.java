package com.centralbrain.runtime.tools;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.tools.ToolManifest.FieldSchema;
import com.centralbrain.runtime.tools.ToolManifest.HealthContract;
import com.centralbrain.runtime.tools.ToolManifest.IdempotencyMode;
import com.centralbrain.runtime.tools.ToolManifest.ObjectSchema;
import com.centralbrain.runtime.tools.ToolManifest.RiskClass;
import com.centralbrain.runtime.tools.ToolSchemaValidator.ErrorCode;
import com.centralbrain.runtime.tools.ToolSchemaValidator.ValidationException;

import java.util.List;
import java.util.Map;

public final class ToolManifestProbeActivity extends Activity {
    private static final String TAG = "CbToolManifest";
    private static final String DIGEST = "a".repeat(64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            ToolManifest manifest = manifest();
            ToolSchemaValidator validator = new ToolSchemaValidator();
            Map<String, Object> input = validator.validateInput(
                    manifest,
                    Map.of(
                            "requestDigest", DIGEST,
                            "targetZone", "driver",
                            "temperatureDeciC", 225L));
            Map<String, Object> output = validator.validateOutput(
                    manifest, Map.of("status", "accepted"));
            boolean contractDefined = manifest.getSchemaVersion() == 1
                    && manifest.getToolId().equals("tool.cockpit.hvac.set.v1")
                    && manifest.getOwnerId().equals("runtime.builtin")
                    && manifest.getCapabilityId().equals("vehicle.hvac.temperature")
                    && manifest.getRiskClass() == RiskClass.MEDIUM
                    && manifest.getTimeoutMs() == 2_500L
                    && manifest.getIdempotencyMode() == IdempotencyMode.TOKEN_REQUIRED;
            boolean digestVerified = manifest.getContractDigest().matches("[0-9a-f]{64}");
            boolean inputOutputVerified = input.size() == 3
                    && input.get("temperatureDeciC").equals(225L)
                    && output.get("status").equals("accepted");
            boolean unknownRejected = rejects(
                    validator,
                    manifest,
                    Map.of(
                            "requestDigest", DIGEST,
                            "targetZone", "driver",
                            "temperatureDeciC", 225L,
                            "unknown", true),
                    ErrorCode.UNKNOWN_FIELD);
            boolean typeBoundsVerified = rejects(
                    validator,
                    manifest,
                    Map.of(
                            "requestDigest", DIGEST,
                            "targetZone", "driver",
                            "temperatureDeciC", 301L),
                    ErrorCode.VALUE_OUT_OF_RANGE)
                    && rejects(
                            validator,
                            manifest,
                            Map.of(
                                    "requestDigest", DIGEST,
                                    "targetZone", "driver",
                                    "temperatureDeciC", 225),
                            ErrorCode.TYPE_MISMATCH);
            boolean healthFailClosed = manifest.getHealthContract().isRequiredBeforeUse();
            boolean android13Arm64 = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            boolean complete = contractDefined
                    && digestVerified
                    && inputOutputVerified
                    && unknownRejected
                    && typeBoundsVerified
                    && healthFailClosed
                    && android13Arm64;

            Log.i(TAG, "nonce=" + nonce
                    + " tool_manifest_probe_complete=" + complete
                    + " tool_manifest_contract_defined=" + contractDefined
                    + " tool_manifest_schema_version=" + manifest.getSchemaVersion()
                    + " tool_manifest_contract_digest_verified=" + digestVerified
                    + " tool_schema_input_output_verified=" + inputOutputVerified
                    + " tool_schema_unknown_field_rejected=" + unknownRejected
                    + " tool_schema_type_bounds_verified=" + typeBoundsVerified
                    + " tool_manifest_health_fail_closed=" + healthFailClosed
                    + " tool_manifest_android13_arm64_verified=" + android13Arm64
                    + " tool_registry_published=false"
                    + " tool_resolver_published=false"
                    + " tool_execution_enabled=false"
                    + " production_tool_artifact_loaded=false"
                    + " effect_dispatch_enabled=false"
                    + " vehicle_readback_accessed=false"
                    + " npu_accessed=false"
                    + " network_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " tool_manifest_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " tool_registry_published=false"
                    + " tool_execution_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static boolean rejects(
            ToolSchemaValidator validator,
            ToolManifest manifest,
            Map<String, ?> values,
            ErrorCode expected) {
        try {
            validator.validateInput(manifest, values);
            return false;
        } catch (ValidationException exception) {
            return exception.getErrorCode() == expected;
        }
    }

    private static ToolManifest manifest() {
        ObjectSchema input = new ObjectSchema(
                "tool.input.hvac-set.v1",
                1,
                512,
                List.of(
                        FieldSchema.sha256DigestField("requestDigest", true),
                        FieldSchema.stringField("targetZone", true, 32),
                        FieldSchema.integerField(
                                "temperatureDeciC", true, 160L, 300L)));
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
}

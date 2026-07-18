package com.centralbrain.runtime.model;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.scenario.ScenarioCatalog;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;
import com.centralbrain.sdk.session.SessionContract;
import com.centralbrain.sdk.session.SessionRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StructuredModelOutputProbeActivity extends Activity {
    private static final String TAG = "CbModelSchemaProbe";
    private static final String TRACE = "a".repeat(64);
    private static final String INPUT = "b".repeat(64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private void runProbe(String nonce) {
        try {
            ScenarioCatalog scenarios = loadScenarios();
            StructuredModelOutput.AcceptedOutput accepted = StructuredModelOutput.validate(
                    request(),
                    bytes(validOutput()),
                    scenarios,
                    CapabilityCatalog.stage2Defaults());
            boolean catalogBindingVerified = accepted.getScenarioId().equals(
                    "scene.comfort.cold.v1")
                    && accepted.getParameters().size() == 2
                    && accepted.getScenarioCatalogDigest().equals(scenarios.getCatalogDigest())
                    && accepted.getCapabilityCatalogDigest().matches("[0-9a-f]{64}");
            boolean unsafeOutputRejected = rejects(
                    scenarios,
                    validOutput().replace(
                            "vehicle.hvac.target_temperature", "vehicle.shell.command"),
                    StructuredModelOutput.ErrorCode.UNKNOWN_CAPABILITY);
            boolean unknownFieldRejected = rejects(
                    scenarios,
                    validOutput().replace(
                            "\"summary\"", "\"command\":\"shell\",\"summary\""),
                    StructuredModelOutput.ErrorCode.UNKNOWN_FIELD);
            boolean pathLikeIdentifierRejected = rejects(
                    scenarios,
                    validOutput().replace("scene.comfort.cold.v1", "../private/model"),
                    StructuredModelOutput.ErrorCode.UNKNOWN_SCENARIO);
            boolean oversizeRejected = rejects(
                    scenarios,
                    new byte[StructuredModelOutput.MAX_OUTPUT_BYTES + 1],
                    StructuredModelOutput.ErrorCode.OVERSIZE);
            boolean sessionOversizeRejected = rejectsSessionOversize();
            boolean authorityDenied = !accepted.isActionAuthorizationGranted()
                    && !accepted.isApprovalDecisionGranted()
                    && !accepted.isEffectDispatchRequested();
            boolean verified = catalogBindingVerified
                    && unsafeOutputRejected
                    && unknownFieldRejected
                    && pathLikeIdentifierRejected
                    && oversizeRejected
                    && sessionOversizeRejected
                    && authorityDenied
                    && accepted.getOutputDigest().matches("[0-9a-f]{64}");

            Log.i(TAG, "nonce=" + nonce
                    + " structured_model_output_probe_complete=true"
                    + " structured_model_output_verified=" + verified
                    + " model_output_catalog_binding_verified=" + catalogBindingVerified
                    + " model_output_unknown_capability_rejected=" + unsafeOutputRejected
                    + " security_boundary_probe_complete=" + verified
                    + " model_output_unknown_field_rejected=" + unknownFieldRejected
                    + " model_output_path_like_identifier_rejected="
                    + pathLikeIdentifierRejected
                    + " model_output_oversize_rejected=" + oversizeRejected
                    + " session_request_oversize_rejected=" + sessionOversizeRejected
                    + " security_android_debug_probe_available=true"
                    + " security_android_debug_probe_executed=true"
                    + " model_output_no_action_authority=" + authorityDenied
                    + " model_output_schema_runtime_wired=false"
                    + " model_invoked=false"
                    + " network_accessed=false"
                    + " npu_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (IOException | RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " structured_model_output_probe_complete=false"
                    + " security_boundary_probe_complete=false"
                    + " security_android_debug_probe_available=true"
                    + " security_android_debug_probe_executed=true"
                    + " error=" + exception.getClass().getSimpleName()
                    + " model_output_schema_runtime_wired=false"
                    + " model_invoked=false"
                    + " network_accessed=false"
                    + " npu_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false", exception);
        }
    }

    private ScenarioCatalog loadScenarios() throws IOException {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        for (String name : getAssets().list("scenarios")) {
            if (name.startsWith("scene.") && name.endsWith(".json")) {
                try (java.io.InputStream stream = getAssets().open("scenarios/" + name)) {
                    assets.put(name, stream.readAllBytes());
                }
            }
        }
        return ScenarioCatalog.load(assets);
    }

    private static boolean rejects(
            ScenarioCatalog scenarios,
            String output,
            StructuredModelOutput.ErrorCode errorCode) {
        return rejects(scenarios, bytes(output), errorCode);
    }

    private static boolean rejects(
            ScenarioCatalog scenarios,
            byte[] output,
            StructuredModelOutput.ErrorCode errorCode) {
        try {
            StructuredModelOutput.validate(
                    request(),
                    output,
                    scenarios,
                    CapabilityCatalog.stage2Defaults());
            return false;
        } catch (StructuredModelOutput.ValidationException exception) {
            return exception.getErrorCode() == errorCode;
        }
    }

    private static boolean rejectsSessionOversize() {
        SessionRequest request = new SessionRequest();
        request.requestId = "8d595630-2255-4f4d-ac0f-26a20ee96f29";
        request.scenarioId = "scene.fatigue.assist.v1";
        request.utterance = "x".repeat(SessionContract.MAX_UTTERANCE_CHARS + 1);
        request.source = ICentralBrainSessionRuntime.SOURCE_HMI_BUTTON;
        request.seatZone = ICentralBrainSessionRuntime.SEAT_ZONE_DRIVER;
        request.locale = "en-US";
        long now = 1_750_000_000_000L;
        request.deadlineEpochMs = now + 60_000L;
        request.clientContextVersion = 4;
        try {
            SessionContract.validateRequest(request, now);
            return false;
        } catch (IllegalArgumentException failure) {
            return failure.getMessage().startsWith("CB_SESSION_CONTRACT:");
        }
    }

    private static ModelContractV2.ModelRequest request() {
        return new ModelContractV2.ModelRequest(
                "schema-probe",
                ModelContractV2.Purpose.SCENARIO_REASONING,
                ModelContractV2.PrivacyClass.INTERNAL,
                new ModelContractV2.LatencyBudget(1_500),
                new ModelContractV2.TokenBudget(256, 256, 512),
                ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE,
                ModelContractV2.FallbackPolicy.NO_FALLBACK,
                TRACE,
                INPUT);
    }

    private static String validOutput() {
        return "{\"schemaVersion\":1,"
                + "\"scenarioId\":\"scene.comfort.cold.v1\","
                + "\"parameters\":["
                + "{\"capabilityId\":\"vehicle.hvac.target_temperature\","
                + "\"area\":\"row1.driver\",\"value\":22.5},"
                + "{\"capabilityId\":\"vehicle.hvac.power\","
                + "\"area\":\"cabin\",\"value\":true}],"
                + "\"summary\":\"scenario proposal ready\"}";
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}

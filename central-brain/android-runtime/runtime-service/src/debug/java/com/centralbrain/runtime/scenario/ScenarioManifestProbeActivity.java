package com.centralbrain.runtime.scenario;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.scenario.ScenarioCatalog.DisabledReason;
import com.centralbrain.runtime.scenario.ScenarioManifest.DrivingPolicy;
import com.centralbrain.runtime.scenario.ScenarioManifestParser.ErrorCode;
import com.centralbrain.runtime.scenario.ScenarioManifestParser.ParseException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ScenarioManifestProbeActivity extends Activity {
    private static final String TAG = "CbScenarioManifest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private void runProbe(String nonce) {
        try {
            Map<String, byte[]> assets = builtInAssets();
            ScenarioCatalog catalog = ScenarioCatalog.load(assets);
            boolean parserVerified = catalog.size() == 4 && catalog.disabled().isEmpty();
            boolean schemaVersionVerified = catalog.all().stream()
                    .allMatch(value -> value.getSchemaVersion() == ScenarioManifest.SCHEMA_VERSION
                            && value.getVersion() == 1);
            boolean catalogDigestVerified = catalog.getCatalogDigest().matches("[0-9a-f]{64}");
            boolean artifactDigestVerified = verifySidecar(
                    assets,
                    readAsset("scenarios/schema/scenario-manifest-v1.schema.json"),
                    sidecar());
            boolean fatiguePolicyVerified = catalog.require("scene.fatigue.assist.v1")
                    .getPlanTemplate().getNodes().stream()
                    .anyMatch(node -> node.getCapabilityId() != null
                            && "vehicle.seat.recline".equals(
                                    node.getCapabilityId().getCanonicalId())
                            && node.getPolicy().getDrivingPolicy()
                                    == DrivingPolicy.PARKED_ONLY
                            && node.getPolicy().isApprovalRequired());
            boolean unknownFieldRejected = unknownFieldRejected(assets);
            boolean oversizeRejected = oversizeRejected();
            boolean duplicateIdRejected = duplicateIdRejected(assets);
            boolean invalidDagRejected = invalidDagRejected(assets);
            boolean isolationVerified = isolationVerified(assets);
            boolean allVerified = parserVerified
                    && schemaVersionVerified
                    && catalogDigestVerified
                    && artifactDigestVerified
                    && fatiguePolicyVerified
                    && unknownFieldRejected
                    && oversizeRejected
                    && duplicateIdRejected
                    && invalidDagRejected
                    && isolationVerified;
            boolean android13Arm64Verified = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");

            Log.i(TAG, "nonce=" + nonce
                    + " scenario_manifest_probe_complete=true"
                    + " scenario_manifest_parser_verified=" + allVerified
                    + " scenario_manifest_schema_version_verified="
                    + schemaVersionVerified
                    + " scenario_catalog_count=" + catalog.size()
                    + " scenario_catalog_digest_verified=" + catalogDigestVerified
                    + " scenario_manifest_artifact_digest_verified="
                    + artifactDigestVerified
                    + " scenario_manifest_fatigue_policy_verified="
                    + fatiguePolicyVerified
                    + " scenario_manifest_unknown_field_rejected="
                    + unknownFieldRejected
                    + " scenario_manifest_oversize_rejected=" + oversizeRejected
                    + " scenario_catalog_duplicate_id_rejected=" + duplicateIdRejected
                    + " scenario_manifest_invalid_dag_rejected=" + invalidDagRejected
                    + " scenario_catalog_isolation_verified=" + isolationVerified
                    + " scenario_manifest_android13_arm64_verified="
                    + android13Arm64Verified
                    + " scenario_manifest_artifact_crypto_verified=false"
                    + " scenario_catalog_production_trusted=false"
                    + " scenario_runtime_wired=false"
                    + " scenario_graph_execution_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " vehicle_signal_provider_wired=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException | IOException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " scenario_manifest_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " scenario_manifest_artifact_crypto_verified=false"
                    + " scenario_catalog_production_trusted=false"
                    + " scenario_runtime_wired=false"
                    + " scenario_graph_execution_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private Map<String, byte[]> builtInAssets() throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        result.put("scene.aios.freeform.v1.json", readAsset(
                "scenarios/scene.aios.freeform.v1.json"));
        result.put("scene.cabin.multimodal.assist.v1.json", readAsset(
                "scenarios/scene.cabin.multimodal.assist.v1.json"));
        result.put("scene.comfort.cold.v1.json", readAsset(
                "scenarios/scene.comfort.cold.v1.json"));
        result.put("scene.fatigue.assist.v1.json", readAsset(
                "scenarios/scene.fatigue.assist.v1.json"));
        result.put("scene.rest.nap.v1.json", readAsset(
                "scenarios/scene.rest.nap.v1.json"));
        return result;
    }

    private String sidecar() throws IOException {
        return new String(
                readAsset("scenarios/scenarios-v1.sha256"),
                StandardCharsets.UTF_8);
    }

    private byte[] readAsset(String path) throws IOException {
        try (InputStream input = getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4_096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static boolean verifySidecar(
            Map<String, byte[]> assets, byte[] schema, String sidecar) {
        Map<String, String> expected = new LinkedHashMap<>();
        for (String line : sidecar.split("\\n")) {
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("  ", 2);
            if (parts.length != 2 || expected.put(parts[1], parts[0]) != null) {
                return false;
            }
        }
        for (Map.Entry<String, byte[]> entry : assets.entrySet()) {
            if (!sha256(entry.getValue()).equals(expected.get(entry.getKey()))) {
                return false;
            }
        }
        return expected.size() == 4
                && sha256(schema).equals(
                        expected.get("schema/scenario-manifest-v1.schema.json"));
    }

    private static boolean unknownFieldRejected(Map<String, byte[]> assets) {
        String source = text(assets.get("scene.comfort.cold.v1.json"));
        try {
            new ScenarioManifestParser().parse(
                    "unknown-field.json",
                    source.replaceFirst("\\{", "{\"unknown\":true,")
                            .getBytes(StandardCharsets.UTF_8));
            return false;
        } catch (ParseException exception) {
            return exception.getErrorCode() == ErrorCode.UNKNOWN_FIELD;
        }
    }

    private static boolean oversizeRejected() {
        byte[] bytes = new byte[ScenarioManifestParser.MAX_MANIFEST_BYTES + 1];
        Arrays.fill(bytes, (byte) ' ');
        try {
            new ScenarioManifestParser().parse("oversize.json", bytes);
            return false;
        } catch (ParseException exception) {
            return exception.getErrorCode() == ErrorCode.OVERSIZE;
        }
    }

    private static boolean duplicateIdRejected(Map<String, byte[]> assets) {
        Map<String, byte[]> duplicate = new LinkedHashMap<>();
        duplicate.put("cold-a.json", assets.get("scene.comfort.cold.v1.json"));
        duplicate.put("cold-b.json", assets.get("scene.comfort.cold.v1.json"));
        duplicate.put("rest.json", assets.get("scene.rest.nap.v1.json"));
        ScenarioCatalog catalog = ScenarioCatalog.load(duplicate);
        return catalog.size() == 1
                && catalog.disabled().size() == 2
                && catalog.disabled().stream().allMatch(
                        value -> value.getReason() == DisabledReason.DUPLICATE_SCENARIO_ID);
    }

    private static boolean invalidDagRejected(Map<String, byte[]> assets) {
        String source = text(assets.get("scene.comfort.cold.v1.json"));
        String cyclic = source.replace(
                "\"prerequisiteNodeId\": \"evaluate_policy\", "
                        + "\"dependentNodeId\": \"set_hvac_power\"",
                "\"prerequisiteNodeId\": \"evaluate_policy\", "
                        + "\"dependentNodeId\": \"capture_context\"");
        try {
            new ScenarioManifestParser().parse(
                    "cyclic.json", cyclic.getBytes(StandardCharsets.UTF_8));
            return false;
        } catch (ParseException exception) {
            return exception.getErrorCode() == ErrorCode.VALIDATION_FAILED;
        }
    }

    private static boolean isolationVerified(Map<String, byte[]> assets) {
        Map<String, byte[]> isolated = new LinkedHashMap<>();
        isolated.put("cold.json", assets.get("scene.comfort.cold.v1.json"));
        isolated.put(
                "invalid.json",
                text(assets.get("scene.rest.nap.v1.json"))
                        .replaceFirst("\\{", "{\"unknown\":true,")
                        .getBytes(StandardCharsets.UTF_8));
        ScenarioCatalog catalog = ScenarioCatalog.load(isolated);
        return catalog.size() == 1
                && catalog.disabled().size() == 1
                && catalog.disabled().get(0).getReason() == DisabledReason.UNKNOWN_FIELD;
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] value = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(value.length * 2);
            for (byte current : value) {
                result.append(String.format("%02x", current & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}

package com.centralbrain.runtime.graph;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.graph.CheckpointSerializer.CheckpointException;
import com.centralbrain.runtime.graph.CheckpointSerializer.ErrorCode;
import com.centralbrain.runtime.graph.CheckpointSerializer.PayloadCodec;
import com.centralbrain.runtime.graph.CheckpointSerializer.Registration;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CheckpointSerializerProbeActivity extends Activity {
    private static final String TAG = "CbCheckpoint";
    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);
    private static final String TYPE = "graph.node.state";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            JsonPrimitiveCheckpointSerializer serializer = serializer();
            ProbeCheckpoint original = new ProbeCheckpoint(
                    SHA_B, Phase.WAITING, 2, new BigDecimal("0.5"), List.of("hvac", "seat"));
            CheckpointEnvelope envelope = serializer.create(
                    TYPE, 1, "set_hvac_power", SHA_A, SHA_B, original, 1_000L);
            byte[] encoded = serializer.serialize(envelope);
            CheckpointEnvelope restored = serializer.deserialize(encoded);
            ProbeCheckpoint decoded = serializer.decodePayload(restored, ProbeCheckpoint.class);
            boolean registeredDtoVerified = decoded.equals(original)
                    && restored.getType().equals(TYPE)
                    && restored.getSchemaVersion() == 1;
            boolean canonicalDigestVerified = java.util.Arrays.equals(
                            encoded, serializer.serialize(restored))
                    && restored.getDigest().length() == 64
                    && new String(encoded, StandardCharsets.UTF_8)
                            .startsWith("{\"schemaVersion\":1,\"type\":\"graph.node.state\"");

            String json = new String(encoded, StandardCharsets.UTF_8);
            boolean malformedUnknownRejected = rejects(
                            serializer,
                            "{".getBytes(StandardCharsets.UTF_8),
                            ErrorCode.MALFORMED_JSON)
                    && rejects(
                            serializer,
                            json.replace(
                                            "\"createdAt\":1000",
                                            "\"unknownField\":true,\"createdAt\":1000")
                                    .getBytes(StandardCharsets.UTF_8),
                            ErrorCode.UNKNOWN_FIELD)
                    && rejects(
                            serializer,
                            json.replace(SHA_A, "d".repeat(64))
                                    .getBytes(StandardCharsets.UTF_8),
                            ErrorCode.DIGEST_MISMATCH);

            boolean sizeDepthVerified = rejects(
                            serializer,
                            new byte[CheckpointSerializer.MAX_CHECKPOINT_BYTES + 1],
                            ErrorCode.OVERSIZE)
                    && depthRejected();
            String metadata = json.replace(
                    "\"attempt\":2",
                    "\"@class\":\"java.lang.Runtime\",\"attempt\":2");
            boolean securityCorpusVerified = rejects(
                            serializer,
                            metadata.getBytes(StandardCharsets.UTF_8),
                            ErrorCode.PAYLOAD_REJECTED)
                    && rejects(
                            serializer,
                            json.replace(
                                            "\"attempt\":2",
                                            "\"filePath\":\"/data/local/tmp/object\",\"attempt\":2")
                                    .getBytes(StandardCharsets.UTF_8),
                            ErrorCode.PAYLOAD_REJECTED)
                    && rejects(
                            serializer,
                            new byte[] {(byte) 0xac, (byte) 0xed, 0x00, 0x05},
                            ErrorCode.MALFORMED_JSON);
            boolean allVerified = registeredDtoVerified
                    && canonicalDigestVerified
                    && malformedUnknownRejected
                    && sizeDepthVerified
                    && securityCorpusVerified;
            boolean android13Arm64Verified = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            Log.i(TAG, "nonce=" + nonce
                    + " checkpoint_serializer_probe_complete=true"
                    + " checkpoint_serializer_defined=" + allVerified
                    + " checkpoint_serializer_registered_dto_verified="
                    + registeredDtoVerified
                    + " checkpoint_serializer_canonical_digest_verified="
                    + canonicalDigestVerified
                    + " checkpoint_serializer_malformed_unknown_rejected="
                    + malformedUnknownRejected
                    + " checkpoint_serializer_size_depth_limit_verified="
                    + sizeDepthVerified
                    + " checkpoint_serializer_security_corpus_verified="
                    + securityCorpusVerified
                    + " checkpoint_serializer_android13_arm64_verified="
                    + android13Arm64Verified
                    + " checkpoint_serializer_java_serialization_enabled=false"
                    + " agent_graph_runtime_persistence_wired=false"
                    + " agent_graph_executor_dispatch_enabled=false"
                    + " effect_dispatch_enabled=false"
                    + " model_invoked=false"
                    + " network_accessed=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " checkpoint_serializer_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " agent_graph_runtime_persistence_wired=false"
                    + " agent_graph_executor_dispatch_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static boolean depthRejected() {
        JsonPrimitiveCheckpointSerializer serializer = new JsonPrimitiveCheckpointSerializer(
                List.of(new Registration<>(
                        "graph.primitive.state",
                        1,
                        CheckpointValue.class,
                        new IdentityCodec())));
        CheckpointValue value = CheckpointValue.string("leaf");
        for (int depth = 0; depth <= CheckpointSerializer.MAX_PAYLOAD_DEPTH; depth++) {
            value = CheckpointValue.list(List.of(value));
        }
        try {
            serializer.create(
                    "graph.primitive.state", 1, "deep_node", SHA_A, SHA_B, value, 1L);
            return false;
        } catch (CheckpointException exception) {
            return exception.getErrorCode() == ErrorCode.DEPTH_EXCEEDED;
        }
    }

    private static boolean rejects(
            JsonPrimitiveCheckpointSerializer serializer,
            byte[] encoded,
            ErrorCode expected) {
        try {
            serializer.deserialize(encoded);
            return false;
        } catch (CheckpointException exception) {
            return exception.getErrorCode() == expected;
        }
    }

    private static JsonPrimitiveCheckpointSerializer serializer() {
        return new JsonPrimitiveCheckpointSerializer(List.of(new Registration<>(
                TYPE, 1, ProbeCheckpoint.class, new ProbeCheckpointCodec())));
    }

    private enum Phase {
        WAITING,
        EXECUTING
    }

    private static final class ProbeCheckpoint {
        private final String stateDigest;
        private final Phase phase;
        private final int attempt;
        private final BigDecimal progress;
        private final List<String> resources;

        private ProbeCheckpoint(
                String stateDigest,
                Phase phase,
                int attempt,
                BigDecimal progress,
                List<String> resources) {
            this.stateDigest = stateDigest;
            this.phase = phase;
            this.attempt = attempt;
            this.progress = progress.stripTrailingZeros();
            this.resources = List.copyOf(resources);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ProbeCheckpoint)) {
                return false;
            }
            ProbeCheckpoint that = (ProbeCheckpoint) other;
            return stateDigest.equals(that.stateDigest)
                    && phase == that.phase
                    && attempt == that.attempt
                    && progress.compareTo(that.progress) == 0
                    && resources.equals(that.resources);
        }

        @Override
        public int hashCode() {
            return stateDigest.hashCode();
        }
    }

    private static final class ProbeCheckpointCodec implements PayloadCodec<ProbeCheckpoint> {
        @Override
        public CheckpointValue encode(ProbeCheckpoint value) {
            Map<String, CheckpointValue> fields = new LinkedHashMap<>();
            fields.put("stateDigest", CheckpointValue.string(value.stateDigest));
            fields.put("phase", CheckpointValue.enumName(value.phase));
            fields.put("attempt", CheckpointValue.integer(value.attempt));
            fields.put("progress", CheckpointValue.decimal(value.progress));
            List<CheckpointValue> resources = new ArrayList<>();
            for (String resource : value.resources) {
                resources.add(CheckpointValue.string(resource));
            }
            fields.put("resources", CheckpointValue.list(resources));
            return CheckpointValue.map(fields);
        }

        @Override
        public ProbeCheckpoint decode(CheckpointValue value) {
            value.requireOnlyFields("stateDigest", "phase", "attempt", "progress", "resources");
            List<String> resources = new ArrayList<>();
            for (CheckpointValue resource : value.requireField("resources").asList()) {
                resources.add(resource.asString());
            }
            return new ProbeCheckpoint(
                    value.requireField("stateDigest").asString(),
                    Phase.valueOf(value.requireField("phase").asString()),
                    Math.toIntExact(value.requireField("attempt").asLong()),
                    value.requireField("progress").asDecimal(),
                    resources);
        }
    }

    private static final class IdentityCodec implements PayloadCodec<CheckpointValue> {
        @Override
        public CheckpointValue encode(CheckpointValue value) {
            return value;
        }

        @Override
        public CheckpointValue decode(CheckpointValue value) {
            return value;
        }
    }
}

package com.centralbrain.runtime.graph;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.graph.CheckpointSerializer.CheckpointException;
import com.centralbrain.runtime.graph.CheckpointSerializer.ErrorCode;
import com.centralbrain.runtime.graph.CheckpointSerializer.PayloadCodec;
import com.centralbrain.runtime.graph.CheckpointSerializer.Registration;

import org.junit.Test;

import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CheckpointSerializerTest {
    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);
    private static final String SHA_C = "c".repeat(64);
    private static final String TYPE = "graph.node.state";

    @Test
    public void registeredDtoRoundTripsThroughImmutableDigestBoundEnvelope() {
        JsonPrimitiveCheckpointSerializer serializer = serializer();
        List<String> resources = new ArrayList<>(List.of("hvac", "seat"));
        GraphCheckpoint original = new GraphCheckpoint(
                SHA_C,
                Phase.WAITING,
                2,
                new BigDecimal("0.500000"),
                true,
                resources);

        CheckpointEnvelope envelope = serializer.create(
                TYPE, 1, "set_hvac_power", SHA_A, SHA_B, original, 1_000L);
        byte[] encoded = serializer.serialize(envelope);
        resources.add("mutated-after-create");
        CheckpointEnvelope restored = serializer.deserialize(encoded);
        GraphCheckpoint decoded = serializer.decodePayload(restored, GraphCheckpoint.class);

        assertEquals(1, restored.getSchemaVersion());
        assertEquals(TYPE, restored.getType());
        assertEquals("set_hvac_power", restored.getNodeId());
        assertEquals(64, restored.getDigest().length());
        assertEquals(original, decoded);
        assertNotSame(original, decoded);
        assertEquals(List.of("hvac", "seat"), decoded.resources);
        assertArrayEquals(encoded, serializer.serialize(restored));
    }

    @Test
    public void canonicalJsonAndDigestAreDeterministicAcrossEquivalentDtos() {
        JsonPrimitiveCheckpointSerializer serializer = serializer();
        GraphCheckpoint first = checkpoint();
        GraphCheckpoint second = new GraphCheckpoint(
                SHA_C,
                Phase.EXECUTING,
                1,
                new BigDecimal("0.250000"),
                false,
                List.of("seat", "hvac"));
        CheckpointEnvelope firstEnvelope = serializer.create(
                TYPE, 1, "move_seat", SHA_A, SHA_B, first, 2_000L);
        CheckpointEnvelope secondEnvelope = serializer.create(
                TYPE, 1, "move_seat", SHA_A, SHA_B, second, 2_000L);

        assertEquals(firstEnvelope.getDigest(), secondEnvelope.getDigest());
        assertArrayEquals(serializer.serialize(firstEnvelope), serializer.serialize(secondEnvelope));
        String json = new String(serializer.serialize(firstEnvelope), StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{\"schemaVersion\":1,\"type\":\"graph.node.state\""));
        assertTrue(json.contains("\"payload\":{\"attempt\":1,\"effectPrepared\":false"));
        assertTrue(json.contains("\"phase\":\"EXECUTING\""));
        assertFalse(json.contains("0.250000"));
        assertFalse(json.contains(GraphCheckpoint.class.getName()));
    }

    @Test
    public void unknownTypeVersionAndPayloadClassFailClosed() {
        JsonPrimitiveCheckpointSerializer serializer = serializer();
        CheckpointException type = assertThrows(
                CheckpointException.class,
                () -> serializer.create(
                        "graph.unknown.state", 1, "node_one", SHA_A, SHA_B, checkpoint(), 1L));
        assertEquals(ErrorCode.TYPE_UNREGISTERED, type.getErrorCode());
        CheckpointException version = assertThrows(
                CheckpointException.class,
                () -> serializer.create(TYPE, 2, "node_one", SHA_A, SHA_B, checkpoint(), 1L));
        assertEquals(ErrorCode.VERSION_UNSUPPORTED, version.getErrorCode());
        CheckpointException wrongClass = assertThrows(
                CheckpointException.class,
                () -> serializer.create(TYPE, 1, "node_one", SHA_A, SHA_B, "payload", 1L));
        assertEquals(ErrorCode.TYPE_MISMATCH, wrongClass.getErrorCode());

        CheckpointEnvelope envelope = serializer.create(
                TYPE, 1, "node_one", SHA_A, SHA_B, checkpoint(), 1L);
        CheckpointException wrongDecodeClass = assertThrows(
                CheckpointException.class,
                () -> serializer.decodePayload(envelope, String.class));
        assertEquals(ErrorCode.TYPE_MISMATCH, wrongDecodeClass.getErrorCode());
    }

    @Test
    public void malformedDuplicateUnknownAndTrailingJsonAreRejected() {
        JsonPrimitiveCheckpointSerializer serializer = serializer();
        byte[] valid = encoded(serializer);
        String json = new String(valid, StandardCharsets.UTF_8);

        assertError(serializer, "{".getBytes(StandardCharsets.UTF_8), ErrorCode.MALFORMED_JSON);
        assertError(
                serializer,
                json.replaceFirst(
                                "\\{",
                                "{\"schemaVersion\":1,")
                        .getBytes(StandardCharsets.UTF_8),
                ErrorCode.DUPLICATE_FIELD);
        assertError(
                serializer,
                json.replace("\"createdAt\":2000", "\"unknownField\":true,\"createdAt\":2000")
                        .getBytes(StandardCharsets.UTF_8),
                ErrorCode.UNKNOWN_FIELD);
        assertError(
                serializer,
                (json + "{}").getBytes(StandardCharsets.UTF_8),
                ErrorCode.MALFORMED_JSON);
        assertError(
                serializer,
                "[]".getBytes(StandardCharsets.UTF_8),
                ErrorCode.TYPE_MISMATCH);
    }

    @Test
    public void oversizeDepthAndTokenBudgetsAreEnforced() {
        JsonPrimitiveCheckpointSerializer serializer = serializer();
        assertError(
                serializer,
                new byte[CheckpointSerializer.MAX_CHECKPOINT_BYTES + 1],
                ErrorCode.OVERSIZE);

        CheckpointValue nested = CheckpointValue.string("leaf");
        for (int depth = 0; depth <= CheckpointSerializer.MAX_PAYLOAD_DEPTH; depth++) {
            nested = CheckpointValue.list(List.of(nested));
        }
        CheckpointValue tooDeep = nested;
        JsonPrimitiveCheckpointSerializer primitiveSerializer = primitiveSerializer();
        CheckpointException depth = assertThrows(
                CheckpointException.class,
                () -> primitiveSerializer.create(
                        "graph.primitive.state", 1, "deep_node", SHA_A, SHA_B, tooDeep, 1L));
        assertEquals(ErrorCode.DEPTH_EXCEEDED, depth.getErrorCode());

        Map<String, CheckpointValue> wide = new LinkedHashMap<>();
        List<CheckpointValue> sixtyFour = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            sixtyFour.add(CheckpointValue.integer(index));
        }
        for (int index = 0; index < 64; index++) {
            wide.put("field" + index, CheckpointValue.list(sixtyFour));
        }
        CheckpointException tokens = assertThrows(
                CheckpointException.class,
                () -> primitiveSerializer.create(
                        "graph.primitive.state",
                        1,
                        "wide_node",
                        SHA_A,
                        SHA_B,
                        CheckpointValue.map(wide),
                        1L));
        assertEquals(ErrorCode.LIMIT_EXCEEDED, tokens.getErrorCode());
    }

    @Test
    public void tamperedDigestAndNonCanonicalJsonAreRejected() {
        JsonPrimitiveCheckpointSerializer serializer = serializer();
        String valid = new String(encoded(serializer), StandardCharsets.UTF_8);
        String tampered = valid.replace(SHA_A, "d".repeat(64));
        assertError(
                serializer,
                tampered.getBytes(StandardCharsets.UTF_8),
                ErrorCode.DIGEST_MISMATCH);
        String spaced = valid.replace("{\"schemaVersion\"", "{ \"schemaVersion\"");
        assertError(
                serializer,
                spaced.getBytes(StandardCharsets.UTF_8),
                ErrorCode.NON_CANONICAL);
    }

    @Test
    public void securityCorpusCannotRequestClassReflectionOrJavaSerialization() {
        JsonPrimitiveCheckpointSerializer serializer = serializer();
        String valid = new String(encoded(serializer), StandardCharsets.UTF_8);
        String metadata = valid.replace(
                "\"attempt\":1",
                "\"@class\":\"java.lang.Runtime\",\"attempt\":1");
        assertError(
                serializer,
                metadata.getBytes(StandardCharsets.UTF_8),
                ErrorCode.PAYLOAD_REJECTED);
        String filePath = valid.replace(
                "\"attempt\":1",
                "\"filePath\":\"/data/local/tmp/object\",\"attempt\":1");
        assertError(
                serializer,
                filePath.getBytes(StandardCharsets.UTF_8),
                ErrorCode.PAYLOAD_REJECTED);
        assertError(
                serializer,
                new byte[] {(byte) 0xac, (byte) 0xed, 0x00, 0x05},
                ErrorCode.MALFORMED_JSON);
        CheckpointException serializable = assertThrows(
                CheckpointException.class,
                () -> serializer.create(
                        TYPE,
                        1,
                        "unsafe_node",
                        SHA_A,
                        SHA_B,
                        new ArbitrarySerializable(),
                        1L));
        assertEquals(ErrorCode.TYPE_MISMATCH, serializable.getErrorCode());
    }

    @Test
    public void codecRejectsUnknownOrWrongTypedDtoFields() {
        JsonPrimitiveCheckpointSerializer serializer = serializer();
        String valid = new String(encoded(serializer), StandardCharsets.UTF_8);
        String unknownPayloadField = valid.replace(
                "\"attempt\":1",
                "\"attempt\":1,\"unexpected\":true");
        assertError(
                serializer,
                unknownPayloadField.getBytes(StandardCharsets.UTF_8),
                ErrorCode.PAYLOAD_REJECTED);
        String wrongType = valid.replace("\"attempt\":1", "\"attempt\":true");
        assertError(
                serializer,
                wrongType.getBytes(StandardCharsets.UTF_8),
                ErrorCode.PAYLOAD_REJECTED);
    }

    private static JsonPrimitiveCheckpointSerializer serializer() {
        return new JsonPrimitiveCheckpointSerializer(List.of(
                new Registration<>(TYPE, 1, GraphCheckpoint.class, new GraphCheckpointCodec())));
    }

    private static JsonPrimitiveCheckpointSerializer primitiveSerializer() {
        return new JsonPrimitiveCheckpointSerializer(List.of(new Registration<>(
                "graph.primitive.state",
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

    private static GraphCheckpoint checkpoint() {
        return new GraphCheckpoint(
                SHA_C,
                Phase.EXECUTING,
                1,
                new BigDecimal("0.25"),
                false,
                List.of("seat", "hvac"));
    }

    private static byte[] encoded(JsonPrimitiveCheckpointSerializer serializer) {
        return serializer.serialize(serializer.create(
                TYPE, 1, "move_seat", SHA_A, SHA_B, checkpoint(), 2_000L));
    }

    private static void assertError(
            JsonPrimitiveCheckpointSerializer serializer,
            byte[] encoded,
            ErrorCode expected) {
        CheckpointException exception = assertThrows(
                CheckpointException.class,
                () -> serializer.deserialize(encoded));
        assertEquals(expected, exception.getErrorCode());
    }

    private enum Phase {
        WAITING,
        EXECUTING
    }

    private static final class GraphCheckpoint {
        private final String stateDigest;
        private final Phase phase;
        private final int attempt;
        private final BigDecimal progress;
        private final boolean effectPrepared;
        private final List<String> resources;

        private GraphCheckpoint(
                String stateDigest,
                Phase phase,
                int attempt,
                BigDecimal progress,
                boolean effectPrepared,
                List<String> resources) {
            this.stateDigest = stateDigest;
            this.phase = phase;
            this.attempt = attempt;
            this.progress = progress.stripTrailingZeros();
            this.effectPrepared = effectPrepared;
            this.resources = List.copyOf(resources);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof GraphCheckpoint)) {
                return false;
            }
            GraphCheckpoint that = (GraphCheckpoint) other;
            return stateDigest.equals(that.stateDigest)
                    && phase == that.phase
                    && attempt == that.attempt
                    && progress.compareTo(that.progress) == 0
                    && effectPrepared == that.effectPrepared
                    && resources.equals(that.resources);
        }

        @Override
        public int hashCode() {
            return stateDigest.hashCode();
        }
    }

    private static final class GraphCheckpointCodec implements PayloadCodec<GraphCheckpoint> {
        @Override
        public CheckpointValue encode(GraphCheckpoint value) {
            Map<String, CheckpointValue> fields = new LinkedHashMap<>();
            fields.put("stateDigest", CheckpointValue.string(value.stateDigest));
            fields.put("phase", CheckpointValue.enumName(value.phase));
            fields.put("attempt", CheckpointValue.integer(value.attempt));
            fields.put("progress", CheckpointValue.decimal(value.progress));
            fields.put("effectPrepared", CheckpointValue.bool(value.effectPrepared));
            List<CheckpointValue> resources = new ArrayList<>();
            for (String resource : value.resources) {
                resources.add(CheckpointValue.string(resource));
            }
            fields.put("resources", CheckpointValue.list(resources));
            return CheckpointValue.map(fields);
        }

        @Override
        public GraphCheckpoint decode(CheckpointValue value) {
            value.requireOnlyFields(
                    "stateDigest", "phase", "attempt", "progress", "effectPrepared", "resources");
            List<String> resources = new ArrayList<>();
            for (CheckpointValue resource : value.requireField("resources").asList()) {
                resources.add(resource.asString());
            }
            return new GraphCheckpoint(
                    requireDigest(value.requireField("stateDigest").asString()),
                    Phase.valueOf(value.requireField("phase").asString()),
                    Math.toIntExact(value.requireField("attempt").asLong()),
                    value.requireField("progress").asDecimal(),
                    value.requireField("effectPrepared").asBoolean(),
                    resources);
        }

        private static String requireDigest(String digest) {
            if (!digest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("stateDigest is invalid");
            }
            return digest;
        }
    }

    private static final class ArbitrarySerializable implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}

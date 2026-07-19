package com.centralbrain.runtime.security;

import com.centralbrain.runtime.graph.CheckpointEnvelope;
import com.centralbrain.runtime.graph.CheckpointSerializer.CheckpointException;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Coverage-guided host target for the three bounded P9-W03 parser surfaces. */
public final class ParserSecurityFuzzTarget {
    private static final int MAX_RAW_BYTES = 64 * 1024 + 1;
    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);
    private static final JsonPrimitiveCheckpointSerializer CHECKPOINT = checkpointSerializer();
    private static final byte[] VALID_CHECKPOINT = validCheckpoint();
    private static final byte[] VALID_SCENARIO = loadScenario();
    private static final ToolManifest TOOL_MANIFEST = toolManifest();
    private static final ToolSchemaValidator TOOL_VALIDATOR = new ToolSchemaValidator();
    private static final LongAdder CHECKPOINT_CALLS = new LongAdder();
    private static final LongAdder SCENARIO_CALLS = new LongAdder();
    private static final LongAdder TOOL_CALLS = new LongAdder();

    static {
        if ("true".equals(System.getenv("CENTRAL_BRAIN_FUZZ_STATS"))) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("central_brain_fuzz_checkpoint_calls=" + CHECKPOINT_CALLS.sum());
                System.out.println("central_brain_fuzz_scenario_calls=" + SCENARIO_CALLS.sum());
                System.out.println("central_brain_fuzz_tool_calls=" + TOOL_CALLS.sum());
                System.out.println("central_brain_fuzz_raw_input_logged=false");
            }, "central-brain-fuzz-stats"));
        }
    }

    private ParserSecurityFuzzTarget() {
    }

    public static void fuzzerTestOneInput(byte[] input) {
        if (input == null || input.length == 0 || input.length > 65_538) {
            return;
        }
        switch (input[0]) {
            case 'C':
                fuzzCheckpoint(slice(input, 1));
                break;
            case 'c':
                fuzzCheckpoint(mutateBaseline(VALID_CHECKPOINT, input, 1));
                break;
            case 'S':
                fuzzScenario("fuzz.json", slice(input, 1));
                break;
            case 's':
                fuzzScenario(sourceName(input), mutateBaseline(VALID_SCENARIO, input, 2));
                break;
            case 'T':
                fuzzTool(structuredToolInput(input, 1));
                break;
            case 't':
                fuzzTool(arbitraryToolInput(input, 1));
                break;
            default:
                int selector = Byte.toUnsignedInt(input[0]) % 3;
                if (selector == 0) {
                    fuzzCheckpoint(slice(input, 1));
                } else if (selector == 1) {
                    fuzzScenario("fuzz.json", slice(input, 1));
                } else {
                    fuzzTool(arbitraryToolInput(input, 1));
                }
        }
    }

    private static void fuzzCheckpoint(byte[] bytes) {
        CHECKPOINT_CALLS.increment();
        try {
            CHECKPOINT.deserialize(bytes);
        } catch (CheckpointException expected) {
            // A typed parser rejection is the expected fail-closed result.
        }
    }

    private static void fuzzScenario(String sourceName, byte[] bytes) {
        SCENARIO_CALLS.increment();
        try {
            new ScenarioManifestParser().parse(sourceName, bytes);
        } catch (ParseException expected) {
            // A typed parser rejection is the expected fail-closed result.
        }
    }

    private static void fuzzTool(Map<String, Object> input) {
        TOOL_CALLS.increment();
        try {
            TOOL_VALIDATOR.validateInput(TOOL_MANIFEST, input);
        } catch (ValidationException expected) {
            // A typed schema rejection is the expected fail-closed result.
        }
    }

    private static String sourceName(byte[] input) {
        if (input.length < 2) {
            return "fuzz.json";
        }
        switch (Byte.toUnsignedInt(input[1]) % 5) {
            case 0:
                return "fuzz.json";
            case 1:
                return "../fuzz.json";
            case 2:
                return "/fuzz.json";
            case 3:
                return "nested/fuzz.json";
            default:
                return "x".repeat(260) + ".json";
        }
    }

    private static Map<String, Object> structuredToolInput(byte[] input, int offset) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("requestDigest", SHA_A);
        values.put("targetZone", "driver");
        values.put("temperatureDeciC", 225L);
        if (offset >= input.length) {
            return values;
        }
        int operationCount = Math.min(8, input.length - offset);
        for (int index = 0; index < operationCount; index++) {
            int choice = Byte.toUnsignedInt(input[offset + index]) % 8;
            switch (choice) {
                case 0:
                    values.remove("requestDigest");
                    break;
                case 1:
                    values.put("requestDigest", visibleString(input, offset, 96));
                    break;
                case 2:
                    values.put("targetZone", null);
                    break;
                case 3:
                    values.put("targetZone", visibleString(input, offset, 96));
                    break;
                case 4:
                    values.put("temperatureDeciC", (long) input[offset + index] * 16L);
                    break;
                case 5:
                    values.put("temperatureDeciC", (int) input[offset + index]);
                    break;
                case 6:
                    values.put("unknown", true);
                    break;
                default:
                    values.clear();
                    break;
            }
        }
        return values;
    }

    private static Map<String, Object> arbitraryToolInput(byte[] input, int offset) {
        Map<String, Object> values = new LinkedHashMap<>();
        int remaining = Math.max(0, input.length - offset);
        int fieldCount = Math.min(8, remaining / 2);
        for (int index = 0; index < fieldCount; index++) {
            int keyChoice = Byte.toUnsignedInt(input[offset + index * 2]) % 4;
            int valueChoice = Byte.toUnsignedInt(input[offset + index * 2 + 1]) % 6;
            String key;
            if (keyChoice == 0) {
                key = "requestDigest";
            } else if (keyChoice == 1) {
                key = "targetZone";
            } else if (keyChoice == 2) {
                key = "temperatureDeciC";
            } else {
                key = "unknown" + index;
            }
            values.put(key, fuzzValue(valueChoice, input, offset + index));
        }
        return values;
    }

    private static Object fuzzValue(int choice, byte[] input, int offset) {
        switch (choice) {
            case 0:
                return visibleString(input, offset, 96);
            case 1:
                return (long) input[offset % input.length] * 32L;
            case 2:
                return (int) input[offset % input.length];
            case 3:
                return (input[offset % input.length] & 1) == 0;
            case 4:
                return null;
            default:
                return slice(input, offset);
        }
    }

    private static String visibleString(byte[] input, int offset, int maxChars) {
        int length = Math.min(maxChars, Math.max(1, input.length - offset));
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            int current = Byte.toUnsignedInt(input[(offset + index) % input.length]);
            value.append((char) (' ' + current % 95));
        }
        return value.toString();
    }

    private static byte[] slice(byte[] input, int offset) {
        if (offset >= input.length) {
            return new byte[0];
        }
        int length = Math.min(MAX_RAW_BYTES, input.length - offset);
        return Arrays.copyOfRange(input, offset, offset + length);
    }

    private static byte[] mutateBaseline(byte[] baseline, byte[] input, int offset) {
        byte[] mutated = Arrays.copyOf(baseline, baseline.length);
        int operationBytes = Math.min(64, Math.max(0, input.length - offset));
        for (int index = 0; index + 1 < operationBytes; index += 2) {
            int position = Byte.toUnsignedInt(input[offset + index]) % mutated.length;
            mutated[position] ^= input[offset + index + 1];
        }
        return mutated;
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

    private static byte[] validCheckpoint() {
        CheckpointEnvelope envelope = CHECKPOINT.create(
                "security.primitive.state",
                1,
                "security_node",
                SHA_A,
                SHA_B,
                CheckpointValue.map(Map.of("value", CheckpointValue.string("ok"))),
                2_000L);
        return CHECKPOINT.serialize(envelope);
    }

    private static byte[] loadScenario() {
        try {
            return Files.readAllBytes(Path.of(
                    "src/main/assets/scenarios/scene.comfort.cold.v1.json"));
        } catch (IOException exception) {
            throw new IllegalStateException("P9-W03f scenario seed is unavailable", exception);
        }
    }

    private static ToolManifest toolManifest() {
        ObjectSchema input = new ObjectSchema(
                "tool.input.security-fuzz.v1",
                1,
                512,
                List.of(
                        FieldSchema.sha256DigestField("requestDigest", true),
                        FieldSchema.stringField("targetZone", true, 32),
                        FieldSchema.integerField("temperatureDeciC", true, 160L, 300L)));
        ObjectSchema output = new ObjectSchema(
                "tool.output.security-fuzz.v1",
                1,
                128,
                List.of(FieldSchema.stringField("status", true, 16)));
        return new ToolManifest(
                1,
                "tool.security.fuzz.v1",
                1,
                "runtime.builtin",
                input,
                output,
                "security.fuzz",
                RiskClass.LOW,
                500L,
                IdempotencyMode.READ_ONLY,
                new HealthContract("health.security.fuzz.v1", 5_000L, true));
    }
}

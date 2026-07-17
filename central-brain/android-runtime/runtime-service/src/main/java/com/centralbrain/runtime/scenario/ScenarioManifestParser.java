package com.centralbrain.runtime.scenario;

import com.centralbrain.runtime.scenario.ScenarioManifest.DependencyCondition;
import com.centralbrain.runtime.scenario.ScenarioManifest.DependencyTemplate;
import com.centralbrain.runtime.scenario.ScenarioManifest.DrivingPolicy;
import com.centralbrain.runtime.scenario.ScenarioManifest.FailureMode;
import com.centralbrain.runtime.scenario.ScenarioManifest.FallbackMode;
import com.centralbrain.runtime.scenario.ScenarioManifest.FallbackPolicy;
import com.centralbrain.runtime.scenario.ScenarioManifest.NodeTemplate;
import com.centralbrain.runtime.scenario.ScenarioManifest.PlanTemplate;
import com.centralbrain.runtime.scenario.ScenarioManifest.PolicyTemplate;
import com.centralbrain.runtime.scenario.ScenarioManifest.RiskClass;
import com.centralbrain.runtime.scenario.ScenarioManifest.Source;
import com.centralbrain.runtime.scenario.ScenarioManifest.UiMetadata;
import com.centralbrain.runtime.scenario.ScenarioManifest.Zone;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict bounded JSON parser for build-owned ScenarioManifest v1 assets. */
public final class ScenarioManifestParser {
    public static final int MAX_MANIFEST_BYTES = 64 * 1024;
    private static final int MAX_DEPTH = 16;
    private static final int MAX_TOKENS = 4_096;
    private static final int MAX_GENERIC_STRING_CHARS = 512;
    private static final Pattern SOURCE_NAME =
            Pattern.compile("[a-z0-9][a-z0-9._-]{2,127}[.]json");

    public enum ErrorCode {
        INVALID_SOURCE,
        OVERSIZE,
        MALFORMED_JSON,
        DUPLICATE_FIELD,
        UNKNOWN_FIELD,
        TYPE_MISMATCH,
        VALIDATION_FAILED
    }

    public static final class ParseException extends IllegalArgumentException {
        private final ErrorCode errorCode;

        private ParseException(ErrorCode errorCode, String message, Throwable cause) {
            super("CB_SCENARIO_PARSE: " + message, cause);
            this.errorCode = errorCode;
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }
    }

    public ScenarioManifest parse(String sourceName, byte[] bytes) {
        if (sourceName == null || !SOURCE_NAME.matcher(sourceName).matches()) {
            throw error(ErrorCode.INVALID_SOURCE, "source name is invalid", null);
        }
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_MANIFEST_BYTES) {
            throw error(ErrorCode.OVERSIZE, "manifest byte size is outside 1..65536", null);
        }
        Object parsed;
        try (JsonReader reader = new JsonReader(new InputStreamReader(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT);
            TokenBudget budget = new TokenBudget();
            parsed = readValue(reader, 0, budget);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw error(ErrorCode.MALFORMED_JSON, "trailing JSON content", null);
            }
        } catch (ParseException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw error(ErrorCode.MALFORMED_JSON, "strict JSON parsing failed", exception);
        }

        try {
            Map<String, Object> root = object(parsed, "manifest");
            int schemaVersion = integer(take(root, "schemaVersion"), "schemaVersion");
            String scenarioId = string(take(root, "scenarioId"), "scenarioId");
            int version = integer(take(root, "version"), "version");
            Set<Source> supportedSources = enumSet(
                    take(root, "supportedSources"), Source.class, "supportedSources");
            Set<Zone> supportedZones = enumSet(
                    take(root, "supportedZones"), Zone.class, "supportedZones");
            String contextPolicyId = string(
                    take(root, "contextPolicyId"), "contextPolicyId");
            List<VehicleSignalPath> requiredContext = contextPaths(
                    take(root, "requiredContext"), "requiredContext");
            List<VehicleSignalPath> optionalContext = contextPaths(
                    take(root, "optionalContext"), "optionalContext");
            List<VehicleCapability.CapabilityId> requiredCapabilities = capabilities(
                    take(root, "requiredCapabilities"), "requiredCapabilities");
            List<VehicleCapability.CapabilityId> optionalCapabilities = capabilities(
                    take(root, "optionalCapabilities"), "optionalCapabilities");
            RiskClass riskClass = enumValue(
                    take(root, "riskClass"), RiskClass.class, "riskClass");
            PlanTemplate planTemplate = planTemplate(take(root, "planTemplate"));
            FallbackPolicy fallback = fallback(take(root, "fallback"));
            UiMetadata ui = ui(take(root, "ui"));
            rejectUnknown(root, "manifest");
            return new ScenarioManifest(
                    schemaVersion,
                    scenarioId,
                    version,
                    sha256(bytes),
                    supportedSources,
                    supportedZones,
                    contextPolicyId,
                    requiredContext,
                    optionalContext,
                    requiredCapabilities,
                    optionalCapabilities,
                    riskClass,
                    planTemplate,
                    fallback,
                    ui);
        } catch (ParseException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw error(ErrorCode.VALIDATION_FAILED, exception.getMessage(), exception);
        }
    }

    private static PlanTemplate planTemplate(Object value) {
        Map<String, Object> object = object(value, "planTemplate");
        List<Object> nodeValues = array(take(object, "nodes"), "planTemplate.nodes");
        List<NodeTemplate> nodes = new ArrayList<>(nodeValues.size());
        for (int index = 0; index < nodeValues.size(); index++) {
            nodes.add(node(nodeValues.get(index), "planTemplate.nodes[" + index + "]"));
        }
        List<Object> dependencyValues = array(
                take(object, "dependencies"), "planTemplate.dependencies");
        List<DependencyTemplate> dependencies = new ArrayList<>(dependencyValues.size());
        for (int index = 0; index < dependencyValues.size(); index++) {
            dependencies.add(dependency(
                    dependencyValues.get(index),
                    "planTemplate.dependencies[" + index + "]"));
        }
        rejectUnknown(object, "planTemplate");
        return new PlanTemplate(nodes, dependencies);
    }

    private static NodeTemplate node(Object value, String field) {
        Map<String, Object> object = object(value, field);
        String nodeId = string(take(object, "nodeId"), field + ".nodeId");
        String nodeType = string(take(object, "nodeType"), field + ".nodeType");
        String capability = string(
                take(object, "capabilityId"), field + ".capabilityId");
        VehicleCapability.CapabilityId capabilityId = capability.isEmpty()
                ? null : capability(capability, field + ".capabilityId");
        boolean required = bool(take(object, "required"), field + ".required");
        long timeoutMs = longInteger(take(object, "timeoutMs"), field + ".timeoutMs");
        int maxAttempts = integer(
                take(object, "maxAttempts"), field + ".maxAttempts");
        String idempotencyKeyTemplate = string(
                take(object, "idempotencyKeyTemplate"),
                field + ".idempotencyKeyTemplate");
        String compensationNodeId = string(
                take(object, "compensationNodeId"), field + ".compensationNodeId");
        PolicyTemplate policy = policy(take(object, "policy"), field + ".policy");
        rejectUnknown(object, field);
        return new NodeTemplate(
                nodeId,
                nodeType,
                capabilityId,
                required,
                timeoutMs,
                maxAttempts,
                idempotencyKeyTemplate,
                compensationNodeId,
                policy);
    }

    private static PolicyTemplate policy(Object value, String field) {
        Map<String, Object> object = object(value, field);
        PolicyTemplate result = new PolicyTemplate(
                string(take(object, "policyId"), field + ".policyId"),
                integer(take(object, "policyVersion"), field + ".policyVersion"),
                enumValue(take(object, "riskClass"), RiskClass.class, field + ".riskClass"),
                enumValue(
                        take(object, "drivingPolicy"),
                        DrivingPolicy.class,
                        field + ".drivingPolicy"),
                bool(take(object, "approvalRequired"), field + ".approvalRequired"),
                enumValue(
                        take(object, "failureMode"),
                        FailureMode.class,
                        field + ".failureMode"));
        rejectUnknown(object, field);
        return result;
    }

    private static DependencyTemplate dependency(Object value, String field) {
        Map<String, Object> object = object(value, field);
        DependencyTemplate result = new DependencyTemplate(
                string(take(object, "prerequisiteNodeId"),
                        field + ".prerequisiteNodeId"),
                string(take(object, "dependentNodeId"), field + ".dependentNodeId"),
                enumValue(
                        take(object, "condition"),
                        DependencyCondition.class,
                        field + ".condition"));
        rejectUnknown(object, field);
        return result;
    }

    private static FallbackPolicy fallback(Object value) {
        Map<String, Object> object = object(value, "fallback");
        FallbackPolicy result = new FallbackPolicy(
                enumValue(take(object, "mode"), FallbackMode.class, "fallback.mode"),
                string(take(object, "messageKey"), "fallback.messageKey"),
                strings(take(object, "optionalNodeIds"), "fallback.optionalNodeIds"));
        rejectUnknown(object, "fallback");
        return result;
    }

    private static UiMetadata ui(Object value) {
        Map<String, Object> object = object(value, "ui");
        UiMetadata result = new UiMetadata(
                string(take(object, "displayKey"), "ui.displayKey"),
                string(take(object, "descriptionKey"), "ui.descriptionKey"),
                string(take(object, "iconKey"), "ui.iconKey"),
                integer(take(object, "sortOrder"), "ui.sortOrder"));
        rejectUnknown(object, "ui");
        return result;
    }

    private static List<VehicleSignalPath> contextPaths(Object value, String field) {
        List<String> paths = strings(value, field);
        List<VehicleSignalPath> result = new ArrayList<>(paths.size());
        for (String path : paths) {
            result.add(VehicleSignalPath.fromCanonicalPath(path));
        }
        return result;
    }

    private static List<VehicleCapability.CapabilityId> capabilities(
            Object value, String field) {
        List<String> ids = strings(value, field);
        List<VehicleCapability.CapabilityId> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            result.add(capability(id, field));
        }
        return result;
    }

    private static VehicleCapability.CapabilityId capability(String id, String field) {
        for (VehicleCapability.CapabilityId candidate
                : VehicleCapability.CapabilityId.values()) {
            if (candidate.getCanonicalId().equals(id)) {
                return candidate;
            }
        }
        throw error(ErrorCode.VALIDATION_FAILED, field + " is not cataloged", null);
    }

    private static Object readValue(JsonReader reader, int depth, TokenBudget budget)
            throws IOException {
        if (depth > MAX_DEPTH) {
            throw error(ErrorCode.MALFORMED_JSON, "JSON nesting exceeds 16", null);
        }
        budget.consume();
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT:
                Map<String, Object> object = new LinkedHashMap<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.length() > 96) {
                        throw error(ErrorCode.MALFORMED_JSON, "field name is too long", null);
                    }
                    if (object.containsKey(name)) {
                        throw error(
                                ErrorCode.DUPLICATE_FIELD,
                                "duplicate field " + name,
                                null);
                    }
                    object.put(name, readValue(reader, depth + 1, budget));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                List<Object> array = new ArrayList<>();
                reader.beginArray();
                while (reader.hasNext()) {
                    if (array.size() >= MAX_DEPENDENCY_ARRAY_ITEMS) {
                        throw error(ErrorCode.MALFORMED_JSON, "array is too large", null);
                    }
                    array.add(readValue(reader, depth + 1, budget));
                }
                reader.endArray();
                return array;
            case STRING:
                String string = reader.nextString();
                if (string.length() > MAX_GENERIC_STRING_CHARS) {
                    throw error(ErrorCode.MALFORMED_JSON, "string is too long", null);
                }
                return string;
            case NUMBER:
                String number = reader.nextString();
                if (number.length() > 32) {
                    throw error(ErrorCode.MALFORMED_JSON, "number is too long", null);
                }
                try {
                    return new BigDecimal(number);
                } catch (NumberFormatException exception) {
                    throw error(ErrorCode.MALFORMED_JSON, "number is invalid", exception);
                }
            case BOOLEAN:
                return reader.nextBoolean();
            case NULL:
                throw error(ErrorCode.TYPE_MISMATCH, "null values are forbidden", null);
            default:
                throw error(ErrorCode.MALFORMED_JSON, "unexpected JSON token", null);
        }
    }

    private static final int MAX_DEPENDENCY_ARRAY_ITEMS = 256;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String field) {
        if (!(value instanceof Map)) {
            throw error(ErrorCode.TYPE_MISMATCH, field + " must be an object", null);
        }
        return new LinkedHashMap<>((Map<String, Object>) value);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value, String field) {
        if (!(value instanceof List)) {
            throw error(ErrorCode.TYPE_MISMATCH, field + " must be an array", null);
        }
        return (List<Object>) value;
    }

    private static Object take(Map<String, Object> object, String name) {
        if (!object.containsKey(name)) {
            throw error(ErrorCode.TYPE_MISMATCH, "required field is missing: " + name, null);
        }
        return object.remove(name);
    }

    private static void rejectUnknown(Map<String, Object> object, String field) {
        if (!object.isEmpty()) {
            throw error(
                    ErrorCode.UNKNOWN_FIELD,
                    field + " contains unknown field " + object.keySet().iterator().next(),
                    null);
        }
    }

    private static String string(Object value, String field) {
        if (!(value instanceof String)) {
            throw error(ErrorCode.TYPE_MISMATCH, field + " must be a string", null);
        }
        return (String) value;
    }

    private static boolean bool(Object value, String field) {
        if (!(value instanceof Boolean)) {
            throw error(ErrorCode.TYPE_MISMATCH, field + " must be a boolean", null);
        }
        return (Boolean) value;
    }

    private static int integer(Object value, String field) {
        long result = longInteger(value, field);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw error(ErrorCode.TYPE_MISMATCH, field + " exceeds integer range", null);
        }
        return (int) result;
    }

    private static long longInteger(Object value, String field) {
        if (!(value instanceof BigDecimal)) {
            throw error(ErrorCode.TYPE_MISMATCH, field + " must be an integer", null);
        }
        try {
            return ((BigDecimal) value).longValueExact();
        } catch (ArithmeticException exception) {
            throw error(ErrorCode.TYPE_MISMATCH, field + " must be an exact integer", exception);
        }
    }

    private static List<String> strings(Object value, String field) {
        List<Object> values = array(value, field);
        List<String> strings = new ArrayList<>(values.size());
        Set<String> unique = new LinkedHashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String string = string(values.get(index), field + '[' + index + ']');
            if (!unique.add(string)) {
                throw error(
                        ErrorCode.VALIDATION_FAILED,
                        field + " contains a duplicate value",
                        null);
            }
            strings.add(string);
        }
        return strings;
    }

    private static <E extends Enum<E>> Set<E> enumSet(
            Object value, Class<E> type, String field) {
        List<String> values = strings(value, field);
        Set<E> result = new LinkedHashSet<>();
        for (String candidate : values) {
            result.add(enumNamed(candidate, type, field));
        }
        return result;
    }

    private static <E extends Enum<E>> E enumValue(
            Object value, Class<E> type, String field) {
        return enumNamed(string(value, field), type, field);
    }

    private static <E extends Enum<E>> E enumNamed(
            String value, Class<E> type, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw error(ErrorCode.VALIDATION_FAILED, field + " has an unknown value", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] value = MessageDigest.getInstance("SHA-256").digest(bytes);
            char[] result = new char[value.length * 2];
            char[] alphabet = "0123456789abcdef".toCharArray();
            for (int index = 0; index < value.length; index++) {
                int current = value[index] & 0xff;
                result[index * 2] = alphabet[current >>> 4];
                result[index * 2 + 1] = alphabet[current & 0x0f];
            }
            return new String(result);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("CB_SCENARIO_PARSE: SHA-256 unavailable", exception);
        }
    }

    private static ParseException error(ErrorCode code, String message, Throwable cause) {
        return new ParseException(code, message, cause);
    }

    private static final class TokenBudget {
        private int tokens;

        private void consume() {
            tokens++;
            if (tokens > MAX_TOKENS) {
                throw error(ErrorCode.MALFORMED_JSON, "JSON token budget exceeded", null);
            }
        }
    }
}

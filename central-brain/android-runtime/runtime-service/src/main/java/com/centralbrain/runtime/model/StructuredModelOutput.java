package com.centralbrain.runtime.model;

import com.centralbrain.runtime.scenario.ScenarioCatalog;
import com.centralbrain.runtime.scenario.ScenarioManifest;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict model-output boundary. Accepted values remain proposals and grant no authority. */
public final class StructuredModelOutput {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROMPT_CONTRACT_ID =
            "centralbrain.model.scenario-prompt.v1";
    public static final String OUTPUT_SCHEMA_ID =
            "centralbrain.model.scenario-output.v1";
    public static final int MAX_OUTPUT_BYTES = 16 * 1024;
    public static final int MAX_PARAMETERS = 16;
    public static final int MAX_SUMMARY_CHARS = 256;

    private static final int MAX_DEPTH = 6;
    private static final int MAX_TOKENS = 128;
    private static final int MAX_FIELD_NAME_CHARS = 64;
    private static final int MAX_GENERIC_STRING_CHARS = 512;

    private StructuredModelOutput() {
    }

    public enum ErrorCode {
        REQUEST_CAPABILITY_MISMATCH,
        OVERSIZE,
        MALFORMED_JSON,
        DUPLICATE_FIELD,
        UNKNOWN_FIELD,
        TYPE_MISMATCH,
        UNKNOWN_SCENARIO,
        UNKNOWN_CAPABILITY,
        CAPABILITY_NOT_REGISTERED_FOR_SCENARIO,
        DUPLICATE_PARAMETER,
        INVALID_AREA,
        VALUE_OUT_OF_RANGE,
        INVALID_SUMMARY
    }

    public static final class ValidationException extends IllegalArgumentException {
        private final ErrorCode errorCode;

        private ValidationException(ErrorCode errorCode, String message, Throwable cause) {
            super("CB_MODEL_OUTPUT: " + message, cause);
            this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }
    }

    /** One cataloged capability target. The scalar type comes from CapabilityCatalog. */
    public static final class Parameter {
        private final VehicleCapability.CapabilityId capabilityId;
        private final String area;
        private final SignalValue.ScalarType scalarType;
        private final boolean booleanValue;
        private final long integerValue;
        private final double decimalValue;
        private final String textValue;
        private final String canonicalValue;

        private Parameter(
                VehicleCapability.CapabilityId capabilityId,
                String area,
                SignalValue.ScalarType scalarType,
                boolean booleanValue,
                long integerValue,
                double decimalValue,
                String textValue,
                String canonicalValue) {
            this.capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
            this.area = Objects.requireNonNull(area, "area");
            this.scalarType = Objects.requireNonNull(scalarType, "scalarType");
            this.booleanValue = booleanValue;
            this.integerValue = integerValue;
            this.decimalValue = decimalValue;
            this.textValue = Objects.requireNonNull(textValue, "textValue");
            this.canonicalValue = Objects.requireNonNull(canonicalValue, "canonicalValue");
        }

        public VehicleCapability.CapabilityId getCapabilityId() {
            return capabilityId;
        }

        public String getArea() {
            return area;
        }

        public SignalValue.ScalarType getScalarType() {
            return scalarType;
        }

        public boolean getBooleanValue() {
            requireType(SignalValue.ScalarType.BOOLEAN);
            return booleanValue;
        }

        public long getIntegerValue() {
            requireType(SignalValue.ScalarType.INTEGER);
            return integerValue;
        }

        public double getDecimalValue() {
            requireType(SignalValue.ScalarType.DECIMAL);
            return decimalValue;
        }

        public String getTextValue() {
            requireType(SignalValue.ScalarType.TEXT);
            return textValue;
        }

        private String canonicalKey() {
            return capabilityId.getCanonicalId() + "|" + area;
        }

        private String canonicalForm() {
            return canonicalKey() + "|" + scalarType.name() + "|" + canonicalValue;
        }

        private void requireType(SignalValue.ScalarType expected) {
            if (scalarType != expected) {
                throw new IllegalStateException("CB_MODEL_OUTPUT: scalar accessor type mismatch");
            }
        }
    }

    /** Validated proposal bound to the request and build-owned catalogs. */
    public static final class AcceptedOutput {
        private final String requestId;
        private final String requestFingerprint;
        private final String traceId;
        private final String scenarioId;
        private final int scenarioVersion;
        private final String scenarioArtifactDigest;
        private final String scenarioCatalogDigest;
        private final String capabilityCatalogDigest;
        private final List<Parameter> parameters;
        private final String summary;
        private final String outputDigest;

        private AcceptedOutput(
                ModelContractV2.ModelRequest request,
                ScenarioManifest manifest,
                String scenarioCatalogDigest,
                String capabilityCatalogDigest,
                List<Parameter> parameters,
                String summary) {
            this.requestId = request.getRequestId();
            this.requestFingerprint = request.getRequestFingerprint();
            this.traceId = request.getTraceId();
            this.scenarioId = manifest.getScenarioId();
            this.scenarioVersion = manifest.getVersion();
            this.scenarioArtifactDigest = manifest.getArtifactDigest();
            this.scenarioCatalogDigest = scenarioCatalogDigest;
            this.capabilityCatalogDigest = capabilityCatalogDigest;
            this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
            this.summary = summary;
            this.outputDigest = digest(canonicalForm());
        }

        public int getSchemaVersion() {
            return SCHEMA_VERSION;
        }

        public String getOutputSchemaId() {
            return OUTPUT_SCHEMA_ID;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getRequestFingerprint() {
            return requestFingerprint;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getScenarioId() {
            return scenarioId;
        }

        public int getScenarioVersion() {
            return scenarioVersion;
        }

        public String getScenarioArtifactDigest() {
            return scenarioArtifactDigest;
        }

        public String getScenarioCatalogDigest() {
            return scenarioCatalogDigest;
        }

        public String getCapabilityCatalogDigest() {
            return capabilityCatalogDigest;
        }

        public List<Parameter> getParameters() {
            return parameters;
        }

        public String getSummary() {
            return summary;
        }

        public String getOutputDigest() {
            return outputDigest;
        }

        public boolean isActionAuthorizationGranted() {
            return false;
        }

        public boolean isApprovalDecisionGranted() {
            return false;
        }

        public boolean isEffectDispatchRequested() {
            return false;
        }

        private String canonicalForm() {
            StringBuilder builder = new StringBuilder()
                    .append(OUTPUT_SCHEMA_ID).append('|')
                    .append(requestId).append('|')
                    .append(requestFingerprint).append('|')
                    .append(traceId).append('|')
                    .append(scenarioId).append('|')
                    .append(scenarioVersion).append('|')
                    .append(scenarioArtifactDigest).append('|')
                    .append(scenarioCatalogDigest).append('|')
                    .append(capabilityCatalogDigest).append('|')
                    .append(parameters.size());
            for (Parameter parameter : parameters) {
                appendLengthPrefixed(builder, parameter.canonicalForm());
            }
            appendLengthPrefixed(builder, summary);
            return builder.toString();
        }
    }

    public static final class ContractSnapshot {
        public String getPromptContractId() {
            return PROMPT_CONTRACT_ID;
        }

        public String getOutputSchemaId() {
            return OUTPUT_SCHEMA_ID;
        }

        public int getMaximumOutputBytes() {
            return MAX_OUTPUT_BYTES;
        }

        public int getMaximumParameters() {
            return MAX_PARAMETERS;
        }

        public int getMaximumSummaryChars() {
            return MAX_SUMMARY_CHARS;
        }

        public boolean isStrictJsonRequired() {
            return true;
        }

        public boolean isRepairPromptEnabled() {
            return false;
        }

        public boolean isRuntimeWired() {
            return false;
        }

        public boolean isModelInvoked() {
            return false;
        }

        public boolean isHardwareAccessed() {
            return false;
        }
    }

    public static ContractSnapshot snapshot() {
        return new ContractSnapshot();
    }

    public static AcceptedOutput validate(
            ModelContractV2.ModelRequest request,
            byte[] encodedOutput,
            ScenarioCatalog scenarioCatalog,
            CapabilityCatalog capabilityCatalog) {
        ModelContractV2.ModelRequest checkedRequest = Objects.requireNonNull(
                request, "request");
        if (checkedRequest.getPurpose() != ModelContractV2.Purpose.SCENARIO_REASONING
                || checkedRequest.getRequiredCapability()
                        != ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE) {
            throw error(
                    ErrorCode.REQUEST_CAPABILITY_MISMATCH,
                    "request does not authorize the structured scenario schema",
                    null);
        }
        Objects.requireNonNull(scenarioCatalog, "scenarioCatalog");
        Objects.requireNonNull(capabilityCatalog, "capabilityCatalog");

        Map<String, Object> root = object(parse(encodedOutput), "output");
        int schemaVersion = integer(take(root, "schemaVersion"), "schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            throw error(ErrorCode.TYPE_MISMATCH, "schemaVersion is unsupported", null);
        }
        String scenarioId = string(take(root, "scenarioId"), "scenarioId");
        ScenarioManifest manifest = scenarioCatalog.find(scenarioId).orElseThrow(
                () -> error(ErrorCode.UNKNOWN_SCENARIO, "scenarioId is not enabled", null));
        List<Object> encodedParameters = array(take(root, "parameters"), "parameters");
        if (encodedParameters.size() > MAX_PARAMETERS) {
            throw error(ErrorCode.OVERSIZE, "parameter count exceeds 16", null);
        }
        List<Parameter> parameters = new ArrayList<>(encodedParameters.size());
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < encodedParameters.size(); index++) {
            Parameter parameter = parameter(
                    encodedParameters.get(index),
                    "parameters[" + index + "]",
                    manifest,
                    capabilityCatalog);
            if (!keys.add(parameter.canonicalKey())) {
                throw error(
                        ErrorCode.DUPLICATE_PARAMETER,
                        "duplicate capability and area parameter",
                        null);
            }
            parameters.add(parameter);
        }
        parameters.sort(Comparator.comparing(Parameter::canonicalKey));
        String summary = validateSummary(string(take(root, "summary"), "summary"));
        rejectUnknown(root, "output");
        return new AcceptedOutput(
                checkedRequest,
                manifest,
                scenarioCatalog.getCatalogDigest(),
                capabilityCatalogDigest(capabilityCatalog),
                parameters,
                summary);
    }

    private static String capabilityCatalogDigest(CapabilityCatalog catalog) {
        List<VehicleCapability> capabilities = new ArrayList<>(catalog.all());
        capabilities.sort(Comparator.comparing(
                capability -> capability.getId().getCanonicalId()));
        StringBuilder builder = new StringBuilder("central-brain-model-capability-catalog-v1")
                .append('|').append(capabilities.size());
        for (VehicleCapability capability : capabilities) {
            VehicleCapability.TargetRange range = capability.getTargetRange();
            appendLengthPrefixed(builder, capability.getId().getCanonicalId());
            builder.append('|').append(capability.getVersion());
            List<String> areas = new ArrayList<>(capability.getAreas());
            Collections.sort(areas);
            builder.append('|').append(areas.size());
            for (String area : areas) {
                appendLengthPrefixed(builder, area);
            }
            appendLengthPrefixed(builder, capability.getUnit());
            builder.append('|').append(range.getScalarType().name());
            switch (range.getScalarType()) {
                case BOOLEAN:
                    break;
                case INTEGER:
                case DECIMAL:
                    builder.append('|').append(range.getMinimumInclusive())
                            .append('|').append(range.getMaximumInclusive())
                            .append('|').append(range.getStep());
                    break;
                case TEXT:
                    builder.append('|').append(range.getMaximumTextChars());
                    List<String> allowedValues = new ArrayList<>(
                            range.getAllowedTextValues());
                    Collections.sort(allowedValues);
                    builder.append('|').append(allowedValues.size());
                    for (String allowedValue : allowedValues) {
                        appendLengthPrefixed(builder, allowedValue);
                    }
                    break;
                default:
                    throw new IllegalStateException(
                            "CB_MODEL_OUTPUT: unsupported capability scalar type");
            }
            builder.append('|').append(capability.getRiskClass().name())
                    .append('|').append(capability.getAvailability().isReadable())
                    .append('|').append(capability.getAvailability().isWritable())
                    .append('|').append(capability.getAvailability().isSimulatable())
                    .append('|').append(capability.getAvailability().isProductionAvailable())
                    .append('|').append(capability.getAvailability().isProductionAuthorized());
            appendLengthPrefixed(
                    builder,
                    capability.getReportedSignalPath()
                            .map(path -> path.getCanonicalPath())
                            .orElse(""));
            List<String> requiredSignals = new ArrayList<>();
            capability.getRequiredFreshSignals().forEach(
                    path -> requiredSignals.add(path.getCanonicalPath()));
            Collections.sort(requiredSignals);
            builder.append('|').append(requiredSignals.size());
            for (String requiredSignal : requiredSignals) {
                appendLengthPrefixed(builder, requiredSignal);
            }
        }
        return digest(builder.toString());
    }

    private static Object parse(byte[] encodedOutput) {
        if (encodedOutput == null
                || encodedOutput.length == 0
                || encodedOutput.length > MAX_OUTPUT_BYTES) {
            throw error(ErrorCode.OVERSIZE, "output byte size is outside 1..16384", null);
        }
        try (JsonReader reader = new JsonReader(new InputStreamReader(
                new ByteArrayInputStream(encodedOutput),
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            reader.setStrictness(Strictness.STRICT);
            Object parsed = readValue(reader, 0, new TokenBudget());
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw error(ErrorCode.MALFORMED_JSON, "trailing JSON content", null);
            }
            return parsed;
        } catch (ValidationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw error(ErrorCode.MALFORMED_JSON, "strict JSON parsing failed", exception);
        }
    }

    private static Object readValue(JsonReader reader, int depth, TokenBudget budget)
            throws IOException {
        if (depth > MAX_DEPTH) {
            throw error(ErrorCode.MALFORMED_JSON, "JSON nesting exceeds 6", null);
        }
        budget.consume();
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                Map<String, Object> object = new LinkedHashMap<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.length() > MAX_FIELD_NAME_CHARS) {
                        throw error(ErrorCode.MALFORMED_JSON, "field name is too long", null);
                    }
                    if (object.containsKey(name)) {
                        throw error(ErrorCode.DUPLICATE_FIELD, "duplicate field " + name, null);
                    }
                    object.put(name, readValue(reader, depth + 1, budget));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                List<Object> array = new ArrayList<>();
                reader.beginArray();
                while (reader.hasNext()) {
                    if (array.size() >= MAX_PARAMETERS) {
                        throw error(ErrorCode.OVERSIZE, "array item count exceeds 16", null);
                    }
                    array.add(readValue(reader, depth + 1, budget));
                }
                reader.endArray();
                return array;
            case STRING:
                String value = reader.nextString();
                if (value.length() > MAX_GENERIC_STRING_CHARS) {
                    throw error(ErrorCode.OVERSIZE, "string exceeds 512 characters", null);
                }
                return value;
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

    private static Parameter parameter(
            Object value,
            String field,
            ScenarioManifest manifest,
            CapabilityCatalog capabilityCatalog) {
        Map<String, Object> object = object(value, field);
        String capabilityName = string(take(object, "capabilityId"), field + ".capabilityId");
        String area = string(take(object, "area"), field + ".area");
        Object scalar = take(object, "value");
        rejectUnknown(object, field);

        VehicleCapability.CapabilityId capabilityId = capabilityId(capabilityName);
        if (!manifest.getRequiredCapabilities().contains(capabilityId)
                && !manifest.getOptionalCapabilities().contains(capabilityId)) {
            throw error(
                    ErrorCode.CAPABILITY_NOT_REGISTERED_FOR_SCENARIO,
                    "capability is outside the selected scenario manifest",
                    null);
        }
        VehicleCapability capability;
        try {
            capability = capabilityCatalog.require(capabilityId);
        } catch (IllegalArgumentException exception) {
            throw error(
                    ErrorCode.UNKNOWN_CAPABILITY,
                    "capabilityId is not present in the active catalog",
                    exception);
        }
        if (!capability.getAreas().contains(area)) {
            throw error(ErrorCode.INVALID_AREA, "area is not cataloged for capability", null);
        }
        return scalarParameter(capability, area, scalar, field + ".value");
    }

    private static Parameter scalarParameter(
            VehicleCapability capability,
            String area,
            Object scalar,
            String field) {
        VehicleCapability.TargetRange range = capability.getTargetRange();
        SignalValue.ScalarType type = range.getScalarType();
        try {
            switch (type) {
                case BOOLEAN:
                    if (!(scalar instanceof Boolean)) {
                        throw typeMismatch(field, "boolean");
                    }
                    boolean booleanValue = (Boolean) scalar;
                    range.validateBoolean(booleanValue);
                    return new Parameter(
                            capability.getId(), area, type, booleanValue, 0, 0, "",
                            Boolean.toString(booleanValue));
                case INTEGER:
                    if (!(scalar instanceof BigDecimal)) {
                        throw typeMismatch(field, "integer");
                    }
                    long integerValue;
                    try {
                        integerValue = ((BigDecimal) scalar).longValueExact();
                    } catch (ArithmeticException exception) {
                        throw typeMismatch(field, "integer");
                    }
                    range.validateInteger(integerValue);
                    return new Parameter(
                            capability.getId(), area, type, false, integerValue, 0, "",
                            Long.toString(integerValue));
                case DECIMAL:
                    if (!(scalar instanceof BigDecimal)) {
                        throw typeMismatch(field, "number");
                    }
                    BigDecimal decimal = ((BigDecimal) scalar).stripTrailingZeros();
                    double decimalValue = decimal.doubleValue();
                    range.validateDecimal(decimalValue);
                    return new Parameter(
                            capability.getId(), area, type, false, 0, decimalValue, "",
                            decimal.toPlainString());
                case TEXT:
                    if (!(scalar instanceof String)) {
                        throw typeMismatch(field, "string");
                    }
                    String textValue = (String) scalar;
                    range.validateText(textValue);
                    return new Parameter(
                            capability.getId(), area, type, false, 0, 0, textValue,
                            textValue);
                default:
                    throw new IllegalStateException("CB_MODEL_OUTPUT: unsupported scalar type");
            }
        } catch (ValidationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw error(ErrorCode.VALUE_OUT_OF_RANGE, field + " is outside catalog bounds", exception);
        }
    }

    private static VehicleCapability.CapabilityId capabilityId(String value) {
        for (VehicleCapability.CapabilityId candidate
                : VehicleCapability.CapabilityId.values()) {
            if (candidate.getCanonicalId().equals(value)) {
                return candidate;
            }
        }
        throw error(ErrorCode.UNKNOWN_CAPABILITY, "capabilityId is unknown", null);
    }

    private static String validateSummary(String summary) {
        if (summary.isEmpty()
                || summary.length() > MAX_SUMMARY_CHARS
                || !summary.equals(summary.trim())) {
            throw error(ErrorCode.INVALID_SUMMARY, "summary is empty, oversized, or padded", null);
        }
        for (int index = 0; index < summary.length(); index++) {
            if (Character.isISOControl(summary.charAt(index))) {
                throw error(ErrorCode.INVALID_SUMMARY, "summary contains a control character", null);
            }
        }
        return summary;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String field) {
        if (!(value instanceof Map)) {
            throw typeMismatch(field, "object");
        }
        return new LinkedHashMap<>((Map<String, Object>) value);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value, String field) {
        if (!(value instanceof List)) {
            throw typeMismatch(field, "array");
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
            throw typeMismatch(field, "string");
        }
        return (String) value;
    }

    private static int integer(Object value, String field) {
        if (!(value instanceof BigDecimal)) {
            throw typeMismatch(field, "integer");
        }
        try {
            return ((BigDecimal) value).intValueExact();
        } catch (ArithmeticException exception) {
            throw typeMismatch(field, "integer");
        }
    }

    private static ValidationException typeMismatch(String field, String expected) {
        return error(ErrorCode.TYPE_MISMATCH, field + " must be a " + expected, null);
    }

    private static ValidationException error(
            ErrorCode code, String message, Throwable cause) {
        return new ValidationException(code, message, cause);
    }

    private static void appendLengthPrefixed(StringBuilder builder, String value) {
        builder.append('|').append(value.length()).append(':').append(value);
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte item : encoded) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("CB_MODEL_OUTPUT: SHA-256 unavailable", exception);
        }
    }

    private static final class TokenBudget {
        private int consumed;

        private void consume() {
            consumed++;
            if (consumed > MAX_TOKENS) {
                throw error(ErrorCode.OVERSIZE, "JSON token count exceeds 128", null);
            }
        }
    }
}

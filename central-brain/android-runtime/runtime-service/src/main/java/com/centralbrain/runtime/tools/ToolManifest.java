package com.centralbrain.runtime.tools;

import com.centralbrain.sdk.plan.PlanContract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Immutable static contract for one versioned Tool. This class does not register or execute Tools. */
public final class ToolManifest {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_FIELDS = 32;
    public static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    public static final long MIN_TIMEOUT_MS = 10L;
    public static final long MAX_TIMEOUT_MS = PlanContract.MAX_NODE_TIMEOUT_MS;
    public static final long MAX_HEALTH_STALENESS_MS = 60_000L;

    private static final Pattern TOOL_ID =
            Pattern.compile("tool[.][a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,6}[.]v[1-9][0-9]*");
    private static final Pattern QUALIFIED_ID =
            Pattern.compile("[a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,7}");
    private static final Pattern FIELD_NAME = Pattern.compile("[a-z][A-Za-z0-9]{0,47}");

    public enum ScalarType {
        STRING,
        BOOLEAN,
        INTEGER,
        SHA256_DIGEST
    }

    public enum RiskClass {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum IdempotencyMode {
        READ_ONLY,
        TOKEN_REQUIRED
    }

    public static final class FieldSchema {
        private final String name;
        private final ScalarType type;
        private final boolean required;
        private final int maxUtf8Bytes;
        private final long minimumInteger;
        private final long maximumInteger;

        private FieldSchema(
                String name,
                ScalarType type,
                boolean required,
                int maxUtf8Bytes,
                long minimumInteger,
                long maximumInteger) {
            this.name = requireIdentifier(name, 48, FIELD_NAME, "field.name");
            this.type = Objects.requireNonNull(type, "type");
            this.required = required;
            this.maxUtf8Bytes = maxUtf8Bytes;
            this.minimumInteger = minimumInteger;
            this.maximumInteger = maximumInteger;
        }

        public static FieldSchema stringField(
                String name, boolean required, int maxUtf8Bytes) {
            if (maxUtf8Bytes < 1 || maxUtf8Bytes > MAX_PAYLOAD_BYTES) {
                throw violation("string field maxUtf8Bytes is outside the payload bound");
            }
            return new FieldSchema(
                    name, ScalarType.STRING, required, maxUtf8Bytes, 0L, 0L);
        }

        public static FieldSchema booleanField(String name, boolean required) {
            return new FieldSchema(name, ScalarType.BOOLEAN, required, 0, 0L, 0L);
        }

        public static FieldSchema integerField(
                String name, boolean required, long minimum, long maximum) {
            if (minimum > maximum) {
                throw violation("integer field minimum exceeds maximum");
            }
            return new FieldSchema(
                    name, ScalarType.INTEGER, required, 0, minimum, maximum);
        }

        public static FieldSchema sha256DigestField(String name, boolean required) {
            return new FieldSchema(
                    name, ScalarType.SHA256_DIGEST, required, 64, 0L, 0L);
        }

        public String getName() {
            return name;
        }

        public ScalarType getType() {
            return type;
        }

        public boolean isRequired() {
            return required;
        }

        public int getMaxUtf8Bytes() {
            return maxUtf8Bytes;
        }

        public long getMinimumInteger() {
            return minimumInteger;
        }

        public long getMaximumInteger() {
            return maximumInteger;
        }

        private String canonicalForm() {
            return name + ':' + type.name() + ':' + required + ':' + maxUtf8Bytes
                    + ':' + minimumInteger + ':' + maximumInteger;
        }
    }

    public static final class ObjectSchema {
        private final String schemaId;
        private final int version;
        private final int maxEncodedBytes;
        private final List<FieldSchema> fields;
        private final Map<String, FieldSchema> fieldsByName;

        public ObjectSchema(
                String schemaId,
                int version,
                int maxEncodedBytes,
                List<FieldSchema> fields) {
            this.schemaId = requireIdentifier(
                    schemaId, 96, QUALIFIED_ID, "objectSchema.schemaId");
            if (version < 1) {
                throw violation("objectSchema.version must be positive");
            }
            this.version = version;
            if (maxEncodedBytes < 2 || maxEncodedBytes > MAX_PAYLOAD_BYTES) {
                throw violation("objectSchema.maxEncodedBytes is outside the payload bound");
            }
            this.maxEncodedBytes = maxEncodedBytes;
            if (fields == null || fields.isEmpty() || fields.size() > MAX_FIELDS) {
                throw violation("objectSchema.fields is outside 1..MAX_FIELDS");
            }
            TreeMap<String, FieldSchema> sorted = new TreeMap<>();
            for (FieldSchema field : fields) {
                FieldSchema present = Objects.requireNonNull(field, "field");
                if (sorted.put(present.name, present) != null) {
                    throw violation("objectSchema.fields contains a duplicate name");
                }
            }
            this.fieldsByName = Collections.unmodifiableMap(sorted);
            this.fields = Collections.unmodifiableList(new ArrayList<>(sorted.values()));
        }

        public String getSchemaId() {
            return schemaId;
        }

        public int getVersion() {
            return version;
        }

        public int getMaxEncodedBytes() {
            return maxEncodedBytes;
        }

        public List<FieldSchema> getFields() {
            return fields;
        }

        FieldSchema field(String name) {
            return fieldsByName.get(name);
        }

        private String canonicalForm() {
            StringBuilder value = new StringBuilder()
                    .append(schemaId).append(':').append(version).append(':')
                    .append(maxEncodedBytes);
            for (FieldSchema field : fields) {
                value.append('|').append(field.canonicalForm());
            }
            return value.toString();
        }
    }

    /** Static freshness contract; dynamic health state belongs to P5-W02. */
    public static final class HealthContract {
        private final String checkId;
        private final long maximumStalenessMs;
        private final boolean requiredBeforeUse;

        public HealthContract(
                String checkId, long maximumStalenessMs, boolean requiredBeforeUse) {
            this.checkId = requireIdentifier(
                    checkId, 96, QUALIFIED_ID, "health.checkId");
            if (maximumStalenessMs < 1
                    || maximumStalenessMs > MAX_HEALTH_STALENESS_MS) {
                throw violation("health.maximumStalenessMs is outside the health bound");
            }
            if (!requiredBeforeUse) {
                throw violation("health must fail closed before Tool use");
            }
            this.maximumStalenessMs = maximumStalenessMs;
            this.requiredBeforeUse = true;
        }

        public String getCheckId() {
            return checkId;
        }

        public long getMaximumStalenessMs() {
            return maximumStalenessMs;
        }

        public boolean isRequiredBeforeUse() {
            return requiredBeforeUse;
        }
    }

    private final int schemaVersion;
    private final String toolId;
    private final int version;
    private final String ownerId;
    private final ObjectSchema inputSchema;
    private final ObjectSchema outputSchema;
    private final String capabilityId;
    private final RiskClass riskClass;
    private final long timeoutMs;
    private final IdempotencyMode idempotencyMode;
    private final HealthContract healthContract;
    private final String contractDigest;

    public ToolManifest(
            int schemaVersion,
            String toolId,
            int version,
            String ownerId,
            ObjectSchema inputSchema,
            ObjectSchema outputSchema,
            String capabilityId,
            RiskClass riskClass,
            long timeoutMs,
            IdempotencyMode idempotencyMode,
            HealthContract healthContract) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw violation("unsupported Tool manifest schemaVersion");
        }
        this.schemaVersion = schemaVersion;
        this.toolId = requireIdentifier(toolId, 128, TOOL_ID, "toolId");
        if (version < 1 || !toolId.endsWith(".v" + version)) {
            throw violation("Tool ID suffix and version must match");
        }
        this.version = version;
        this.ownerId = requireIdentifier(ownerId, 96, QUALIFIED_ID, "ownerId");
        this.inputSchema = Objects.requireNonNull(inputSchema, "inputSchema");
        this.outputSchema = Objects.requireNonNull(outputSchema, "outputSchema");
        if (inputSchema.schemaId.equals(outputSchema.schemaId)) {
            throw violation("input and output schema IDs must be distinct");
        }
        this.capabilityId = requireIdentifier(
                capabilityId, 96, QUALIFIED_ID, "capabilityId");
        this.riskClass = Objects.requireNonNull(riskClass, "riskClass");
        if (timeoutMs < MIN_TIMEOUT_MS || timeoutMs > MAX_TIMEOUT_MS) {
            throw violation("timeoutMs is outside the Tool contract");
        }
        this.timeoutMs = timeoutMs;
        this.idempotencyMode = Objects.requireNonNull(idempotencyMode, "idempotencyMode");
        this.healthContract = Objects.requireNonNull(healthContract, "healthContract");
        this.contractDigest = digest(canonicalForm());
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getToolId() {
        return toolId;
    }

    public int getVersion() {
        return version;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public ObjectSchema getInputSchema() {
        return inputSchema;
    }

    public ObjectSchema getOutputSchema() {
        return outputSchema;
    }

    public String getCapabilityId() {
        return capabilityId;
    }

    public RiskClass getRiskClass() {
        return riskClass;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public IdempotencyMode getIdempotencyMode() {
        return idempotencyMode;
    }

    public HealthContract getHealthContract() {
        return healthContract;
    }

    public String getContractDigest() {
        return contractDigest;
    }

    private String canonicalForm() {
        return schemaVersion + "|" + toolId + "|" + version + "|" + ownerId
                + "|" + inputSchema.canonicalForm() + "|" + outputSchema.canonicalForm()
                + "|" + capabilityId + "|" + riskClass.name() + "|" + timeoutMs
                + "|" + idempotencyMode.name() + "|" + healthContract.checkId
                + "|" + healthContract.maximumStalenessMs + "|true";
    }

    private static String requireIdentifier(
            String value, int maxLength, Pattern pattern, String field) {
        if (value == null
                || value.length() > maxLength
                || !pattern.matcher(value).matches()) {
            throw violation(field + " is not canonical");
        }
        return value;
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                result.append(Character.forDigit((current >>> 4) & 0xf, 16));
                result.append(Character.forDigit(current & 0xf, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_TOOL_MANIFEST: " + message);
    }
}

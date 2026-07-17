package com.centralbrain.runtime.tools;

import java.util.regex.Pattern;

/** Immutable digest-only context for one admitted built-in Tool invocation. */
public final class ToolInvocationContext {
    public static final int SCHEMA_VERSION = 1;

    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern QUALIFIED_ID = Pattern.compile(
            "[a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,7}");

    private final int schemaVersion;
    private final String invocationDigest;
    private final String sessionDigest;
    private final String planDigest;
    private final String nodeDigest;
    private final String auditCorrelationDigest;
    private final String toolFamilyId;
    private final String toolContractDigest;
    private final String requiredCapabilityId;
    private final String idempotencyTokenDigest;
    private final long issuedAtElapsedRealtimeMs;
    private final long deadlineElapsedRealtimeMs;
    private final int maximumOutputBytes;

    public ToolInvocationContext(
            int schemaVersion,
            String invocationDigest,
            String sessionDigest,
            String planDigest,
            String nodeDigest,
            String auditCorrelationDigest,
            String toolFamilyId,
            String toolContractDigest,
            String requiredCapabilityId,
            String idempotencyTokenDigest,
            long issuedAtElapsedRealtimeMs,
            long deadlineElapsedRealtimeMs,
            int maximumOutputBytes) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "CB_TOOL_INVOCATION: unsupported schema version");
        }
        this.schemaVersion = schemaVersion;
        this.invocationDigest = requireDigest(invocationDigest, "invocationDigest");
        this.sessionDigest = requireDigest(sessionDigest, "sessionDigest");
        this.planDigest = requireDigest(planDigest, "planDigest");
        this.nodeDigest = requireDigest(nodeDigest, "nodeDigest");
        this.auditCorrelationDigest = requireDigest(
                auditCorrelationDigest, "auditCorrelationDigest");
        this.toolFamilyId = ToolRegistry.requireFamilyId(toolFamilyId);
        this.toolContractDigest = requireDigest(toolContractDigest, "toolContractDigest");
        if (requiredCapabilityId == null
                || !QUALIFIED_ID.matcher(requiredCapabilityId).matches()) {
            throw new IllegalArgumentException(
                    "CB_TOOL_INVOCATION: capabilityId is not canonical");
        }
        this.requiredCapabilityId = requiredCapabilityId;
        this.idempotencyTokenDigest = idempotencyTokenDigest == null
                ? null : requireDigest(idempotencyTokenDigest, "idempotencyTokenDigest");
        if (issuedAtElapsedRealtimeMs < 0L
                || deadlineElapsedRealtimeMs <= issuedAtElapsedRealtimeMs) {
            throw new IllegalArgumentException(
                    "CB_TOOL_INVOCATION: invalid elapsed-realtime deadline");
        }
        this.issuedAtElapsedRealtimeMs = issuedAtElapsedRealtimeMs;
        this.deadlineElapsedRealtimeMs = deadlineElapsedRealtimeMs;
        if (maximumOutputBytes < 1
                || maximumOutputBytes > ToolManifest.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "CB_TOOL_INVOCATION: output limit is invalid");
        }
        this.maximumOutputBytes = maximumOutputBytes;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getInvocationDigest() {
        return invocationDigest;
    }

    public String getSessionDigest() {
        return sessionDigest;
    }

    public String getPlanDigest() {
        return planDigest;
    }

    public String getNodeDigest() {
        return nodeDigest;
    }

    public String getAuditCorrelationDigest() {
        return auditCorrelationDigest;
    }

    public String getToolFamilyId() {
        return toolFamilyId;
    }

    public String getToolContractDigest() {
        return toolContractDigest;
    }

    public String getRequiredCapabilityId() {
        return requiredCapabilityId;
    }

    public String getIdempotencyTokenDigest() {
        return idempotencyTokenDigest;
    }

    public long getIssuedAtElapsedRealtimeMs() {
        return issuedAtElapsedRealtimeMs;
    }

    public long getDeadlineElapsedRealtimeMs() {
        return deadlineElapsedRealtimeMs;
    }

    public int getMaximumOutputBytes() {
        return maximumOutputBytes;
    }

    /** This value object cannot authorize production execution. */
    public boolean isProductionAuthority() {
        return false;
    }

    static String requireDigest(String value, String label) {
        if (value == null || !DIGEST.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "CB_TOOL_INVOCATION: " + label + " is not canonical");
        }
        return value;
    }
}

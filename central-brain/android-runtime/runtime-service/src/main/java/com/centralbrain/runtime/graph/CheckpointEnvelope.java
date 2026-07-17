package com.centralbrain.runtime.graph;

import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable digest-bound checkpoint envelope. */
public final class CheckpointEnvelope {
    private static final Pattern NODE_ID = Pattern.compile("[a-z][a-z0-9_]{2,63}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    private final int schemaVersion;
    private final String type;
    private final String nodeId;
    private final String planDigest;
    private final String contextDigest;
    private final CheckpointValue payload;
    private final String digest;
    private final long createdAtEpochMs;

    CheckpointEnvelope(
            int schemaVersion,
            String type,
            String nodeId,
            String planDigest,
            String contextDigest,
            CheckpointValue payload,
            String digest,
            long createdAtEpochMs) {
        if (schemaVersion < 1 || schemaVersion > 32) {
            throw CheckpointSerializer.error(
                    CheckpointSerializer.ErrorCode.INVALID_ARGUMENT,
                    "checkpoint schema version is outside 1..32",
                    null);
        }
        if (type == null || type.length() > 96) {
            throw CheckpointSerializer.error(
                    CheckpointSerializer.ErrorCode.INVALID_ARGUMENT,
                    "checkpoint type is invalid",
                    null);
        }
        if (nodeId == null || !NODE_ID.matcher(nodeId).matches()) {
            throw CheckpointSerializer.error(
                    CheckpointSerializer.ErrorCode.INVALID_ARGUMENT,
                    "checkpoint nodeId is invalid",
                    null);
        }
        this.schemaVersion = schemaVersion;
        this.type = type;
        this.nodeId = nodeId;
        this.planDigest = requireDigest(planDigest, "planDigest");
        this.contextDigest = requireDigest(contextDigest, "contextDigest");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.digest = requireDigest(digest, "digest");
        if (createdAtEpochMs <= 0) {
            throw CheckpointSerializer.error(
                    CheckpointSerializer.ErrorCode.INVALID_ARGUMENT,
                    "createdAtEpochMs must be positive",
                    null);
        }
        this.createdAtEpochMs = createdAtEpochMs;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getType() {
        return type;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getPlanDigest() {
        return planDigest;
    }

    public String getContextDigest() {
        return contextDigest;
    }

    public CheckpointValue getPayload() {
        return payload;
    }

    public String getDigest() {
        return digest;
    }

    public long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }

    private static String requireDigest(String digest, String label) {
        if (digest == null || !DIGEST.matcher(digest).matches()) {
            throw CheckpointSerializer.error(
                    CheckpointSerializer.ErrorCode.INVALID_ARGUMENT,
                    label + " must be lowercase SHA-256",
                    null);
        }
        return digest;
    }
}

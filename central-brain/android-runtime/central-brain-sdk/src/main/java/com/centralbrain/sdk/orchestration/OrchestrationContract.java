package com.centralbrain.sdk.orchestration;

import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.ScenarioPlan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Validation and canonical digest rules for the independent orchestration Binder V1. */
public final class OrchestrationContract {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_EFFECTS = 16;
    public static final int MAX_DETAIL_CODE_CHARS = 64;
    public static final long MAX_REQUEST_AGE_MS = 5 * 60 * 1000L;
    public static final long MAX_FUTURE_SKEW_MS = 60 * 1000L;

    private static final Pattern SCENARIO_ID =
            Pattern.compile("[a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,7}");
    private static final Pattern NODE_ID =
            Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Pattern CAPABILITY_ID =
            Pattern.compile("[a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,7}");
    private static final Pattern SOURCE_ID = CAPABILITY_ID;
    private static final Pattern DETAIL_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private OrchestrationContract() {}

    public static void validateStartRequest(
            OrchestrationStartRequest request,
            long nowEpochMs) {
        Objects.requireNonNull(request, "request");
        requireVersion(request.schemaVersion, "request.schemaVersion");
        requireUuid(request.requestId, "request.requestId");
        requireUuid(request.sessionId, "request.sessionId");
        requireIdentifier(request.scenarioId, 96, SCENARIO_ID, "request.scenarioId");
        if (request.executionProfile != ICentralBrainOrchestration.PROFILE_PRODUCTION
                && request.executionProfile
                        != ICentralBrainOrchestration.PROFILE_DEBUG_SIMULATION) {
            throw violation("request.executionProfile is unknown");
        }
        if (request.simulationMotionState < ICentralBrainOrchestration.MOTION_UNKNOWN
                || request.simulationMotionState > ICentralBrainOrchestration.MOTION_MOVING) {
            throw violation("request.simulationMotionState is unknown");
        }
        if (request.executionProfile == ICentralBrainOrchestration.PROFILE_PRODUCTION
                && request.simulationMotionState != ICentralBrainOrchestration.MOTION_UNKNOWN) {
            throw violation("production request must not carry simulated motion");
        }
        validateRequestTime(request.requestedAtEpochMs, nowEpochMs, "request");
    }

    public static void validateApprovalResponse(ApprovalResponse response, long nowEpochMs) {
        Objects.requireNonNull(response, "response");
        requireVersion(response.schemaVersion, "response.schemaVersion");
        requireUuid(response.requestId, "response.requestId");
        requireUuid(response.sessionId, "response.sessionId");
        requireUuid(response.approvalId, "response.approvalId");
        requireDigest(response.expectedProjectionDigest, "response.expectedProjectionDigest");
        if (response.decision != ICentralBrainOrchestration.DECISION_APPROVE
                && response.decision != ICentralBrainOrchestration.DECISION_REJECT) {
            throw violation("response.decision is unknown");
        }
        validateRequestTime(response.respondedAtEpochMs, nowEpochMs, "response");
    }

    public static void validateUndoRequest(UndoRequest request, long nowEpochMs) {
        Objects.requireNonNull(request, "request");
        requireVersion(request.schemaVersion, "undo.schemaVersion");
        requireUuid(request.requestId, "undo.requestId");
        requireUuid(request.sessionId, "undo.sessionId");
        requireUuid(request.undoId, "undo.undoId");
        requireDigest(request.expectedProjectionDigest, "undo.expectedProjectionDigest");
        validateRequestTime(request.requestedAtEpochMs, nowEpochMs, "undo");
    }

    public static void validateSnapshot(OrchestrationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        requireVersion(snapshot.schemaVersion, "snapshot.schemaVersion");
        requireUuid(snapshot.sessionId, "snapshot.sessionId");
        requireIdentifier(snapshot.scenarioId, 96, SCENARIO_ID, "snapshot.scenarioId");
        requireState(snapshot.state);
        requirePendingStage(snapshot.pendingStage);
        requireIdentifier(snapshot.detailCode, MAX_DETAIL_CODE_CHARS, DETAIL_CODE,
                "snapshot.detailCode");
        requireDigest(snapshot.projectionDigest, "snapshot.projectionDigest");
        if (snapshot.updatedAtEpochMs <= 0L) {
            throw violation("snapshot.updatedAtEpochMs must be positive");
        }
        OrchestrationNode[] nodes = snapshot.nodes == null
                ? new OrchestrationNode[0] : snapshot.nodes;
        OrchestrationEffect[] effects = snapshot.effects == null
                ? new OrchestrationEffect[0] : snapshot.effects;
        if (nodes.length > PlanContract.MAX_NODES || effects.length > MAX_EFFECTS) {
            throw violation("snapshot projection count exceeds bound");
        }
        boolean planPublished = !isEmpty(snapshot.planId);
        if (planPublished) {
            requireUuid(snapshot.planId, "snapshot.planId");
            if (snapshot.planRevision < 1 || snapshot.graphRevision < 1L) {
                throw violation("published plan revision must be positive");
            }
            requireDigest(snapshot.planDigest, "snapshot.planDigest");
            if (nodes.length == 0) {
                throw violation("published plan requires node projections");
            }
        } else if (snapshot.planRevision != 0
                || snapshot.graphRevision != 0L
                || !isEmpty(snapshot.planDigest)
                || nodes.length != 0
                || effects.length != 0
                || snapshot.pendingStage != ICentralBrainOrchestration.PENDING_NONE
                || !isEmpty(snapshot.pendingNodeId)
                || !isEmpty(snapshot.pendingCapabilityId)
                || !isEmpty(snapshot.approvalId)
                || !isEmpty(snapshot.undoId)) {
            throw violation("unpublished plan carries execution projection");
        }

        Set<String> nodeIds = new HashSet<>();
        for (OrchestrationNode node : nodes) {
            validateNode(node);
            if (!nodeIds.add(node.nodeId)) {
                throw violation("snapshot contains duplicate nodeId");
            }
        }
        Set<String> effectIds = new HashSet<>();
        for (OrchestrationEffect effect : effects) {
            validateEffect(effect);
            if (!nodeIds.contains(effect.nodeId) || !effectIds.add(effect.effectId)) {
                throw violation("effect projection binding or identity is invalid");
            }
            if (effect.simulated != snapshot.simulated) {
                throw violation("effect and snapshot simulation marker differ");
            }
        }
        validatePending(snapshot, nodeIds);
        if (snapshot.simulated && snapshot.hardwareAccessed) {
            throw violation("simulated orchestration must not claim hardware access");
        }
        if (snapshot.approvalAuthorityTrusted && !snapshot.approvalResponseAvailable) {
            throw violation("trusted approval authority requires response availability");
        }
        if (snapshot.productionReady && (!snapshot.hardwareAccessed
                || !snapshot.targetHardwareValidated
                || snapshot.simulated)) {
            throw violation("production-ready projection lacks target evidence");
        }
        String expected = calculateProjectionDigest(snapshot);
        if (!expected.equals(snapshot.projectionDigest)) {
            throw violation("snapshot.projectionDigest is invalid");
        }
    }

    public static void validatePlanForSnapshot(
            ScenarioPlan plan,
            OrchestrationSnapshot snapshot) {
        validateSnapshot(snapshot);
        if (plan == null) {
            if (!isEmpty(snapshot.planId)) {
                throw violation("published snapshot has no typed plan");
            }
            return;
        }
        PlanContract.validatePlan(plan);
        if (!plan.planId.equals(snapshot.planId)
                || !plan.sessionId.equals(snapshot.sessionId)
                || !plan.scenarioId.equals(snapshot.scenarioId)
                || plan.revision != snapshot.planRevision
                || !plan.planDigest.equals(snapshot.planDigest)) {
            throw violation("typed plan differs from orchestration projection");
        }
    }

    public static String calculateProjectionDigest(OrchestrationSnapshot snapshot) {
        MessageDigest digest = sha256();
        update(digest, "central-brain-orchestration-projection-v1");
        update(digest, Integer.toString(snapshot.schemaVersion));
        update(digest, nullToEmpty(snapshot.sessionId));
        update(digest, nullToEmpty(snapshot.scenarioId));
        update(digest, nullToEmpty(snapshot.planId));
        update(digest, Integer.toString(snapshot.planRevision));
        update(digest, nullToEmpty(snapshot.planDigest));
        update(digest, Integer.toString(snapshot.state));
        update(digest, Long.toString(snapshot.graphRevision));
        OrchestrationNode[] nodes = snapshot.nodes == null
                ? new OrchestrationNode[0] : snapshot.nodes;
        update(digest, Integer.toString(nodes.length));
        for (OrchestrationNode node : nodes) {
            update(digest, nullToEmpty(node.nodeId));
            update(digest, nullToEmpty(node.nodeType));
            update(digest, nullToEmpty(node.capabilityId));
            update(digest, Integer.toString(node.state));
            update(digest, Integer.toString(node.attemptCount));
            update(digest, Boolean.toString(node.required));
            update(digest, nullToEmpty(node.evidenceDigest));
        }
        OrchestrationEffect[] effects = snapshot.effects == null
                ? new OrchestrationEffect[0] : snapshot.effects;
        update(digest, Integer.toString(effects.length));
        for (OrchestrationEffect effect : effects) {
            update(digest, nullToEmpty(effect.effectId));
            update(digest, nullToEmpty(effect.nodeId));
            update(digest, nullToEmpty(effect.capabilityId));
            update(digest, Integer.toString(effect.state));
            update(digest, Integer.toString(effect.attemptCount));
            update(digest, Boolean.toString(effect.simulated));
            update(digest, nullToEmpty(effect.sourceId));
            update(digest, nullToEmpty(effect.evidenceDigest));
        }
        update(digest, Integer.toString(snapshot.pendingStage));
        update(digest, nullToEmpty(snapshot.pendingNodeId));
        update(digest, nullToEmpty(snapshot.pendingCapabilityId));
        update(digest, nullToEmpty(snapshot.approvalId));
        update(digest, nullToEmpty(snapshot.undoId));
        update(digest, nullToEmpty(snapshot.detailCode));
        update(digest, Boolean.toString(snapshot.simulated));
        update(digest, Boolean.toString(snapshot.effectDispatchEnabled));
        update(digest, Boolean.toString(snapshot.readbackAvailable));
        update(digest, Boolean.toString(snapshot.approvalResponseAvailable));
        update(digest, Boolean.toString(snapshot.approvalAuthorityTrusted));
        update(digest, Boolean.toString(snapshot.undoAvailable));
        update(digest, Boolean.toString(snapshot.hardwareAccessed));
        update(digest, Boolean.toString(snapshot.productionReady));
        update(digest, Boolean.toString(snapshot.targetHardwareValidated));
        update(digest, Long.toString(snapshot.updatedAtEpochMs));
        return toHex(digest.digest());
    }

    private static void validateNode(OrchestrationNode node) {
        Objects.requireNonNull(node, "node");
        requireVersion(node.schemaVersion, "node.schemaVersion");
        requireIdentifier(node.nodeId, PlanContract.MAX_NODE_ID_CHARS, NODE_ID, "node.nodeId");
        if (!PlanContract.allowedNodeTypes().contains(node.nodeType)) {
            throw violation("node.nodeType is not allowed");
        }
        if (!isEmpty(node.capabilityId)) {
            requireIdentifier(node.capabilityId, 96, CAPABILITY_ID, "node.capabilityId");
        }
        if (node.state < ICentralBrainOrchestration.NODE_PENDING
                || node.state > ICentralBrainOrchestration.NODE_STUCK
                || node.attemptCount < 0
                || node.attemptCount > PlanContract.MAX_ATTEMPTS) {
            throw violation("node state or attempt is outside bound");
        }
        requireDigest(node.evidenceDigest, "node.evidenceDigest");
    }

    private static void validateEffect(OrchestrationEffect effect) {
        Objects.requireNonNull(effect, "effect");
        requireVersion(effect.schemaVersion, "effect.schemaVersion");
        requireUuid(effect.effectId, "effect.effectId");
        requireIdentifier(effect.nodeId, PlanContract.MAX_NODE_ID_CHARS, NODE_ID,
                "effect.nodeId");
        requireIdentifier(effect.capabilityId, 96, CAPABILITY_ID, "effect.capabilityId");
        if (effect.state < EffectContract.STATE_PROPOSED
                || effect.state > EffectContract.STATE_CANCELLED
                || effect.attemptCount < 1
                || effect.attemptCount > PlanContract.MAX_ATTEMPTS) {
            throw violation("effect state or attempt is outside bound");
        }
        requireIdentifier(effect.sourceId, 96, SOURCE_ID, "effect.sourceId");
        requireDigest(effect.evidenceDigest, "effect.evidenceDigest");
    }

    private static void validatePending(
            OrchestrationSnapshot snapshot,
            Set<String> nodeIds) {
        if (snapshot.pendingStage == ICentralBrainOrchestration.PENDING_NONE) {
            if (!isEmpty(snapshot.pendingNodeId)
                    || !isEmpty(snapshot.pendingCapabilityId)
                    || !isEmpty(snapshot.approvalId)) {
                throw violation("non-pending snapshot carries pending identity");
            }
        } else {
            requireIdentifier(snapshot.pendingNodeId, PlanContract.MAX_NODE_ID_CHARS,
                    NODE_ID, "snapshot.pendingNodeId");
            if (!nodeIds.contains(snapshot.pendingNodeId)) {
                throw violation("pending node is absent from projection");
            }
            if (!isEmpty(snapshot.pendingCapabilityId)) {
                requireIdentifier(snapshot.pendingCapabilityId, 96, CAPABILITY_ID,
                        "snapshot.pendingCapabilityId");
            }
        }
        if (snapshot.pendingStage == ICentralBrainOrchestration.PENDING_APPROVAL) {
            requireUuid(snapshot.approvalId, "snapshot.approvalId");
            if (!snapshot.approvalResponseAvailable) {
                throw violation("approval pending without response surface");
            }
        } else if (!isEmpty(snapshot.approvalId)
                || snapshot.approvalResponseAvailable
                || snapshot.approvalAuthorityTrusted) {
            throw violation("non-approval state carries approval projection");
        }
        if (snapshot.undoAvailable) {
            requireUuid(snapshot.undoId, "snapshot.undoId");
        } else if (!isEmpty(snapshot.undoId)) {
            throw violation("unavailable Undo carries an identifier");
        }
    }

    private static void requireVersion(int value, String field) {
        if (value != SCHEMA_VERSION) {
            throw violation(field + " is unsupported");
        }
    }

    private static void requireState(int state) {
        if (state < ICentralBrainOrchestration.STATE_UNAVAILABLE
                || state > ICentralBrainOrchestration.STATE_STUCK) {
            throw violation("snapshot.state is unknown");
        }
    }

    private static void requirePendingStage(int stage) {
        if (stage < ICentralBrainOrchestration.PENDING_NONE
                || stage > ICentralBrainOrchestration.PENDING_UNDO) {
            throw violation("snapshot.pendingStage is unknown");
        }
    }

    private static void validateRequestTime(long value, long nowEpochMs, String field) {
        if (nowEpochMs <= 0L
                || value <= 0L
                || value < nowEpochMs - MAX_REQUEST_AGE_MS
                || value > nowEpochMs + MAX_FUTURE_SKEW_MS) {
            throw violation(field + " timestamp is outside the accepted window");
        }
    }

    private static void requireUuid(String value, String field) {
        try {
            if (!UUID.fromString(Objects.requireNonNull(value, field)).toString().equals(value)) {
                throw violation(field + " is not canonical lowercase UUID");
            }
        } catch (IllegalArgumentException exception) {
            throw violation(field + " is not canonical lowercase UUID");
        }
    }

    private static void requireDigest(String value, String field) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw violation(field + " is not a SHA-256 digest");
        }
    }

    private static void requireIdentifier(
            String value,
            int maxChars,
            Pattern pattern,
            String field) {
        if (value == null || value.length() > maxChars || !pattern.matcher(value).matches()) {
            throw violation(field + " is invalid");
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = digits[value >>> 4];
            result[index * 2 + 1] = digits[value & 0x0f];
        }
        return new String(result);
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_ORCHESTRATION_CONTRACT: " + message);
    }
}

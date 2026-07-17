package com.centralbrain.runtime.graph;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.graph.ApprovalInterruptRecord.Decision;
import com.centralbrain.runtime.graph.ApprovalResumeValidator.Reason;
import com.centralbrain.runtime.graph.ApprovalResumeValidator.ResumeContext;
import com.centralbrain.runtime.graph.ApprovalResumeValidator.ResumeResult;
import com.centralbrain.runtime.graph.ApprovalResumeValidator.SafetyState;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ApprovalInterruptExecutorTest {
    private static final long NOW = 1_700_000_000_000L;
    private static final String APPROVAL_ID = "10000000-0000-4000-8000-000000000001";
    private static final String SESSION_ID = "20000000-0000-4000-8000-000000000002";
    private static final String PLAN_ID = "30000000-0000-4000-8000-000000000003";
    private static final String OWNER = "a".repeat(64);
    private static final String ACTION = "b".repeat(64);
    private static final String PLAN = "c".repeat(64);
    private static final String CONTEXT = "d".repeat(64);
    private static final String POLICY = "e".repeat(64);
    private static final String SAFETY = "f".repeat(64);
    private static final String AUTHORITY = "1".repeat(64);

    @Test
    public void pendingInterruptBindsCallerPlanContextPolicyAndClampsExpiry() {
        ApprovalInterruptRecord record = ApprovalInterruptExecutor.createPending(
                request(120_000L, NOW + 45_000L), NOW);

        assertEquals(Decision.PENDING, record.getDecision());
        assertEquals(OWNER, record.getOwnerFingerprint());
        assertEquals(SESSION_ID, record.getSessionId());
        assertEquals(PLAN_ID, record.getPlanId());
        assertEquals("approval_gate", record.getNodeId());
        assertEquals(ACTION, record.getActionDigest());
        assertEquals(PLAN, record.getPlanDigest());
        assertEquals(CONTEXT, record.getContextDigest());
        assertEquals(POLICY, record.getPolicyDigest());
        assertEquals(SAFETY, record.getSafetyStateDigest());
        assertEquals(NOW + 45_000L, record.getExpiresAtEpochMs());
        assertEquals(record.getExpiresAtEpochMs(), record.getPlanDeadlineEpochMs());
        assertEquals(64, record.getRecordDigest().length());
    }

    @Test
    public void checkpointCodecRoundTripsCanonicalEpochStringsAndDigest() {
        ApprovalInterruptRecord original = pending();
        JsonPrimitiveCheckpointSerializer serializer = serializer();
        CheckpointEnvelope envelope = serializer.create(
                ApprovalInterruptExecutor.CHECKPOINT_TYPE,
                ApprovalInterruptExecutor.CHECKPOINT_SCHEMA_VERSION,
                original.getNodeId(),
                original.getPlanDigest(),
                original.getContextDigest(),
                original,
                NOW);
        byte[] encoded = serializer.serialize(envelope);
        CheckpointEnvelope restoredEnvelope = serializer.deserialize(encoded);
        ApprovalInterruptRecord restored = serializer.decodePayload(
                restoredEnvelope, ApprovalInterruptRecord.class);

        assertEquals(original, restored);
        assertEquals(original.getRecordDigest(), restored.getRecordDigest());
        assertArrayEquals(encoded, serializer.serialize(restoredEnvelope));
        String json = new String(encoded, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"createdAtEpochMs\":\"1700000000000\""));
        assertFalse(json.contains("javaClass"));
        assertFalse(json.contains("binderObject"));
    }

    @Test
    public void trustedApprovalResumesOnlyWithCurrentValidBindings() {
        ApprovalInterruptRecord approved = approve(pending());
        ResumeResult result = ApprovalResumeValidator.validate(
                approved, context(NOW + 2_000L));

        assertTrue(result.isAllowed());
        assertEquals(Reason.VALID, result.getReason());
        assertEquals(64, result.getResultDigest().length());
        assertEquals(
                result.getResultDigest(),
                ApprovalResumeValidator.validate(approved, context(NOW + 2_000L))
                        .getResultDigest());
    }

    @Test
    public void ownerPlanAndActionBindingMismatchFailClosed() {
        ApprovalInterruptRecord approved = approve(pending());

        assertReason(approved, context("2".repeat(64), SESSION_ID, PLAN_ID, ACTION,
                PLAN, CONTEXT, POLICY, SAFETY, NOW + 2_000L,
                true, true, true, true, SafetyState.SAFE), Reason.OWNER_MISMATCH);
        assertReason(approved, context(OWNER, SESSION_ID,
                "40000000-0000-4000-8000-000000000004", ACTION,
                PLAN, CONTEXT, POLICY, SAFETY, NOW + 2_000L,
                true, true, true, true, SafetyState.SAFE), Reason.BINDING_MISMATCH);
        assertReason(approved, context(OWNER, SESSION_ID, PLAN_ID, "3".repeat(64),
                PLAN, CONTEXT, POLICY, SAFETY, NOW + 2_000L,
                true, true, true, true, SafetyState.SAFE), Reason.BINDING_MISMATCH);
    }

    @Test
    public void contextAndPolicyMustRemainFreshAuthorizedAndDigestBound() {
        ApprovalInterruptRecord approved = approve(pending());

        assertReason(approved, context(OWNER, SESSION_ID, PLAN_ID, ACTION,
                PLAN, CONTEXT, POLICY, SAFETY, NOW + 2_000L,
                false, true, true, true, SafetyState.SAFE), Reason.CONTEXT_STALE);
        assertReason(approved, context(OWNER, SESSION_ID, PLAN_ID, ACTION,
                PLAN, "4".repeat(64), POLICY, SAFETY, NOW + 2_000L,
                true, true, true, true, SafetyState.SAFE), Reason.CONTEXT_CHANGED);
        assertReason(approved, context(OWNER, SESSION_ID, PLAN_ID, ACTION,
                PLAN, CONTEXT, POLICY, SAFETY, NOW + 2_000L,
                true, false, true, true, SafetyState.SAFE), Reason.POLICY_DENIED);
        assertReason(approved, context(OWNER, SESSION_ID, PLAN_ID, ACTION,
                PLAN, CONTEXT, "5".repeat(64), SAFETY, NOW + 2_000L,
                true, true, true, true, SafetyState.SAFE), Reason.POLICY_CHANGED);
        assertReason(approved, context(OWNER, SESSION_ID, PLAN_ID, ACTION,
                PLAN, CONTEXT, POLICY, SAFETY, NOW + 2_000L,
                true, true, false, true, SafetyState.SAFE), Reason.CAPABILITY_DENIED);
    }

    @Test
    public void resumeRevalidatesTrustedSafeAndUnchangedSafetyState() {
        ApprovalInterruptRecord approved = approve(pending());

        assertReason(approved, context(OWNER, SESSION_ID, PLAN_ID, ACTION,
                PLAN, CONTEXT, POLICY, SAFETY, NOW + 2_000L,
                true, true, true, false, SafetyState.SAFE), Reason.SAFETY_UNTRUSTED);
        assertReason(approved, context(OWNER, SESSION_ID, PLAN_ID, ACTION,
                PLAN, CONTEXT, POLICY, SAFETY, NOW + 2_000L,
                true, true, true, true, SafetyState.UNSAFE), Reason.SAFETY_UNSAFE);
        assertReason(approved, context(OWNER, SESSION_ID, PLAN_ID, ACTION,
                PLAN, CONTEXT, POLICY, SAFETY, NOW + 2_000L,
                true, true, true, true, SafetyState.UNKNOWN), Reason.SAFETY_UNSAFE);
        assertReason(approved, context(OWNER, SESSION_ID, PLAN_ID, ACTION,
                PLAN, CONTEXT, POLICY, "6".repeat(64), NOW + 2_000L,
                true, true, true, true, SafetyState.SAFE), Reason.SAFETY_CHANGED);
    }

    @Test
    public void pendingRejectedAndExpiredApprovalCannotResume() {
        ApprovalInterruptRecord pending = pending();
        assertReason(pending, context(NOW + 2_000L), Reason.NOT_APPROVED);

        ApprovalInterruptRecord rejected = ApprovalInterruptExecutor.recordDecision(
                pending, Decision.REJECTED, AUTHORITY, true, NOW + 1_000L);
        assertReason(rejected, context(NOW + 2_000L), Reason.NOT_APPROVED);

        ApprovalInterruptRecord approved = approve(pending);
        assertReason(approved, context(approved.getExpiresAtEpochMs()), Reason.EXPIRED);
        ApprovalInterruptRecord expired = ApprovalInterruptExecutor.expire(
                pending, AUTHORITY, true, pending.getExpiresAtEpochMs());
        assertEquals(Decision.EXPIRED, expired.getDecision());
        assertReason(expired, context(expired.getExpiresAtEpochMs()), Reason.NOT_APPROVED);
    }

    @Test
    public void untrustedAuthorityReplayAndMalformedRequestsFailClosed() {
        ApprovalInterruptRecord pending = pending();
        assertThrows(IllegalArgumentException.class, () ->
                ApprovalInterruptExecutor.recordDecision(
                        pending, Decision.APPROVED, AUTHORITY, false, NOW + 1_000L));
        assertThrows(IllegalArgumentException.class, () ->
                ApprovalInterruptExecutor.recordDecision(
                        pending, Decision.EXPIRED, AUTHORITY, true, NOW + 1_000L));
        assertThrows(IllegalArgumentException.class, () ->
                ApprovalInterruptExecutor.expire(pending, AUTHORITY, true, NOW + 1_000L));
        ApprovalInterruptRecord approved = approve(pending);
        assertThrows(IllegalArgumentException.class, () ->
                ApprovalInterruptExecutor.recordDecision(
                        approved, Decision.REJECTED, AUTHORITY, true, NOW + 2_000L));
        assertThrows(IllegalArgumentException.class, () ->
                request(ApprovalInterruptExecutor.MAX_APPROVAL_TTL_MS + 1L, NOW + 1_000_000L));
        assertThrows(IllegalArgumentException.class, () ->
                ApprovalInterruptExecutor.createPending(request(1_000L, NOW), NOW));
    }

    private static ApprovalInterruptRecord pending() {
        return ApprovalInterruptExecutor.createPending(
                request(60_000L, NOW + 120_000L), NOW);
    }

    private static ApprovalInterruptRecord approve(ApprovalInterruptRecord pending) {
        return ApprovalInterruptExecutor.recordDecision(
                pending, Decision.APPROVED, AUTHORITY, true, NOW + 1_000L);
    }

    private static ApprovalInterruptExecutor.Request request(long ttlMs, long deadline) {
        return new ApprovalInterruptExecutor.Request(
                APPROVAL_ID,
                OWNER,
                SESSION_ID,
                PLAN_ID,
                "approval_gate",
                ACTION,
                PLAN,
                CONTEXT,
                POLICY,
                SAFETY,
                ttlMs,
                deadline);
    }

    private static JsonPrimitiveCheckpointSerializer serializer() {
        return new JsonPrimitiveCheckpointSerializer(List.of(
                ApprovalInterruptExecutor.checkpointRegistration()));
    }

    private static ResumeContext context(long nowEpochMs) {
        return context(OWNER, SESSION_ID, PLAN_ID, ACTION, PLAN, CONTEXT, POLICY, SAFETY,
                nowEpochMs, true, true, true, true, SafetyState.SAFE);
    }

    private static ResumeContext context(
            String owner,
            String sessionId,
            String planId,
            String actionDigest,
            String planDigest,
            String contextDigest,
            String policyDigest,
            String safetyDigest,
            long nowEpochMs,
            boolean contextFresh,
            boolean policyAuthorized,
            boolean capabilityAllowed,
            boolean safetyTrusted,
            SafetyState safetyState) {
        return new ResumeContext(
                owner,
                sessionId,
                planId,
                "approval_gate",
                actionDigest,
                planDigest,
                contextDigest,
                policyDigest,
                safetyDigest,
                nowEpochMs,
                contextFresh,
                policyAuthorized,
                capabilityAllowed,
                safetyTrusted,
                safetyState);
    }

    private static void assertReason(
            ApprovalInterruptRecord record,
            ResumeContext context,
            Reason reason) {
        ResumeResult result = ApprovalResumeValidator.validate(record, context);
        assertFalse(result.isAllowed());
        assertEquals(reason, result.getReason());
    }
}

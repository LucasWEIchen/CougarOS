package com.centralbrain.runtime.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.identity.CallerIdentitySnapshot;
import com.centralbrain.runtime.identity.DurablePrincipalFingerprint;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy.Capability;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy.DecisionReason;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy.PrincipalRule;
import com.centralbrain.runtime.session.TransientSessionRegistry;
import com.centralbrain.runtime.skills.SkillSignerPolicy;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;
import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionRequest;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;

public final class IdentityReplaySecurityCorpusContractTest {
    private static final long NOW = 1_750_000_000_000L;
    private static final String SIGNER_A = "a".repeat(64);
    private static final String SIGNER_B = "b".repeat(64);
    private static final String SIGNER_C = "c".repeat(64);
    private static final String SIGNER_D = "d".repeat(64);
    private static final String OWNER_A = "e".repeat(64);
    private static final String OWNER_B = "f".repeat(64);
    private static final String REQUEST_ID = "8d595630-2255-4f4d-ac0f-26a20ee96f29";

    @Test
    public void fixedCatalogHasThreeSurfacesAndEighteenUniqueCases() {
        assertEquals(3, IdentityReplaySecurityCorpusContract.SURFACE_COUNT);
        assertEquals(6, IdentityReplaySecurityCorpusContract.CASES_PER_SURFACE);
        assertEquals(18, IdentityReplaySecurityCorpusContract.CASE_COUNT);
        assertEquals(18, IdentityReplaySecurityCorpusContract.cases().size());
        assertEquals(
                18,
                IdentityReplaySecurityCorpusContract.cases().stream()
                        .map(IdentityReplaySecurityCorpusContract.CorpusCase::getCaseId)
                        .distinct()
                        .count());
        assertTrue(IdentityReplaySecurityCorpusContract.corpusDigest()
                .matches("[0-9a-f]{64}"));
    }

    @Test
    public void callerPolicyCorpusRejectsSixIdentityAndCapabilityAttacks() {
        CallerCapabilityPolicy policy = policy("com.centralbrain.allowed", SIGNER_A);

        assertDecision(
                "caller.unresolved_identity.v1",
                DecisionReason.IDENTITY_UNRESOLVED,
                policy.evaluate(
                        CallerIdentitySnapshot.unresolved(11001, -1, "unknown user"),
                        Capability.PROTOCOL_READ).getReason());
        assertDecision(
                "caller.package_spoof.v1",
                DecisionReason.PACKAGE_NOT_CONFIGURED,
                policy.evaluate(
                        identity("com.centralbrain.spoof", SIGNER_A),
                        Capability.PROTOCOL_READ).getReason());
        assertDecision(
                "caller.current_signer_spoof.v1",
                DecisionReason.CURRENT_SIGNER_MISMATCH,
                policy.evaluate(
                        identity("com.centralbrain.allowed", SIGNER_B),
                        Capability.PROTOCOL_READ).getReason());
        assertDecision(
                "caller.capability_escalation.v1",
                DecisionReason.CAPABILITY_NOT_GRANTED,
                policy.evaluate(
                        identity("com.centralbrain.allowed", SIGNER_A),
                        Capability.SIMULATION_CONTROL).getReason());

        CallerCapabilityPolicy sharedUidPolicy = new CallerCapabilityPolicy(List.of(
                rule("com.centralbrain.allowed", SIGNER_A),
                rule("com.centralbrain.shared", SIGNER_A)));
        CallerIdentitySnapshot confusedSharedUid = CallerIdentitySnapshot.resolved(
                11001,
                7,
                List.of(
                        packageIdentity("com.centralbrain.allowed", SIGNER_A),
                        packageIdentity("com.centralbrain.shared", SIGNER_B)));
        assertDecision(
                "caller.shared_uid_signer_confusion.v1",
                DecisionReason.CURRENT_SIGNER_MISMATCH,
                sharedUidPolicy.evaluate(
                        confusedSharedUid,
                        Capability.PROTOCOL_READ).getReason());

        String original = DurablePrincipalFingerprint.from(
                identity("com.centralbrain.allowed", SIGNER_A));
        String rotated = DurablePrincipalFingerprint.from(
                identity("com.centralbrain.allowed", SIGNER_B));
        assertEquals(
                "FINGERPRINT_CHANGED",
                outcome("caller.principal_signer_rotation.v1"));
        assertNotEquals(original, rotated);
    }

    @Test
    public void sessionCorpusEnforcesReplayDigestAndOwnerIsolation() {
        TransientSessionRegistry registry = registry();
        SessionRequest request = request(REQUEST_ID, "I feel tired");
        SessionHandle first = registry.openOwned(OWNER_A, request);

        assertEquals("SAME_HANDLE", outcome("session.same_digest_replay.v1"));
        assertEquals(first.sessionId, registry.openOwned(OWNER_A, request).sessionId);

        IllegalArgumentException conflict = assertThrows(
                IllegalArgumentException.class,
                () -> registry.openOwned(
                        OWNER_A,
                        request(REQUEST_ID, "changed private input")));
        assertEquals("IDEMPOTENCY_CONFLICT", outcome("session.request_digest_conflict.v1"));
        assertTrue(conflict.getMessage().contains("idempotency conflict"));

        assertEquals("NULL_SNAPSHOT", outcome("session.cross_owner_find.v1"));
        assertNull(registry.findOwned(OWNER_B, first));

        IllegalArgumentException crossOwnerEvents = assertThrows(
                IllegalArgumentException.class,
                () -> registry.eventsOwned(OWNER_B, first.sessionId, "", 10));
        assertEquals("SESSION_NOT_FOUND", outcome("session.cross_owner_events.v1"));
        assertTrue(crossOwnerEvents.getMessage().contains("session not found"));

        assertEquals("NO_CHANGE", outcome("session.cross_owner_cancel.v1"));
        assertFalse(registry.cancelOwned(
                OWNER_B,
                first,
                ICentralBrainSessionRuntime.CANCEL_REASON_USER).isChanged());

        assertEquals("SECURITY_EXCEPTION", outcome("session.malformed_owner.v1"));
        assertThrows(SecurityException.class, () -> registry.findOwned("spoof", first));
    }

    @Test
    public void signerPolicyCorpusRejectsSixSignerAndEpochAttacks() {
        SkillSignerPolicy policy = signerPolicy();

        assertSigner("signer.unknown.v1", policy.evaluate(SIGNER_D, 12L));
        assertSigner("signer.not_yet_active.v1", policy.evaluate(SIGNER_A, 9L));
        assertSigner("signer.retired.v1", policy.evaluate(SIGNER_B, 12L));
        assertSigner("signer.revoked.v1", policy.evaluate(SIGNER_C, 12L));

        assertEquals("POLICY_VIOLATION", outcome("signer.malformed_digest.v1"));
        IllegalArgumentException malformed = assertThrows(
                IllegalArgumentException.class,
                () -> policy.evaluate("not-a-digest", 12L));
        assertTrue(malformed.getMessage().startsWith("Skill signer policy violation:"));

        assertEquals("POLICY_VIOLATION", outcome("signer.nonpositive_epoch.v1"));
        IllegalArgumentException epoch = assertThrows(
                IllegalArgumentException.class,
                () -> policy.evaluate(SIGNER_A, 0L));
        assertTrue(epoch.getMessage().startsWith("Skill signer policy violation:"));
    }

    @Test
    public void hostCorpusDoesNotClaimBinderCryptoAndroidOrProductionQualification() {
        assertTrue(IdentityReplaySecurityCorpusContract.isCallerPolicyHostVerified());
        assertTrue(IdentityReplaySecurityCorpusContract
                .isSessionReplayOwnerPolicyHostVerified());
        assertTrue(IdentityReplaySecurityCorpusContract.isSignerPolicyHostVerified());
        assertFalse(IdentityReplaySecurityCorpusContract
                .isBinderCallingUidSpoofAndroidVerified());
        assertFalse(IdentityReplaySecurityCorpusContract
                .isPackageSignatureCryptographicallyVerified());
        assertFalse(IdentityReplaySecurityCorpusContract.isCoverageGuidedFuzzComplete());
        assertFalse(IdentityReplaySecurityCorpusContract.isAndroid13Arm64Verified());
        assertFalse(IdentityReplaySecurityCorpusContract.isRuntimeWired());
        assertFalse(IdentityReplaySecurityCorpusContract.isHardwareAccessed());
        assertFalse(IdentityReplaySecurityCorpusContract.isProductionReady());
        assertFalse(IdentityReplaySecurityCorpusContract.isTargetHardwareValidated());
    }

    private static CallerCapabilityPolicy policy(String packageName, String signer) {
        return new CallerCapabilityPolicy(Collections.singletonList(rule(packageName, signer)));
    }

    private static PrincipalRule rule(String packageName, String signer) {
        return new PrincipalRule(
                packageName,
                Collections.singletonList(signer),
                EnumSet.of(Capability.PROTOCOL_READ));
    }

    private static CallerIdentitySnapshot identity(String packageName, String signer) {
        return CallerIdentitySnapshot.resolved(
                11001,
                7,
                Collections.singletonList(packageIdentity(packageName, signer)));
    }

    private static CallerIdentitySnapshot.PackageIdentity packageIdentity(
            String packageName,
            String signer) {
        return new CallerIdentitySnapshot.PackageIdentity(
                packageName,
                Collections.singletonList(signer));
    }

    private static TransientSessionRegistry registry() {
        Queue<String> ids = new ArrayDeque<>(Arrays.asList(
                "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26",
                "37b459a6-4373-4b0b-b2a4-44df3adb2aef",
                "6af0f2f1-67d6-4f10-adb1-f93af51e7709",
                "24ab900d-aa8b-41e7-a053-39351c41f001"));
        return TransientSessionRegistry.createForContractTest(
                4,
                4,
                () -> NOW,
                ids::remove);
    }

    private static SessionRequest request(String requestId, String utterance) {
        SessionRequest request = new SessionRequest();
        request.requestId = requestId;
        request.scenarioId = "scene.fatigue.assist.v1";
        request.utterance = utterance;
        request.source = ICentralBrainSessionRuntime.SOURCE_HMI_BUTTON;
        request.seatZone = ICentralBrainSessionRuntime.SEAT_ZONE_DRIVER;
        request.locale = "en-US";
        request.deadlineEpochMs = NOW + 60_000L;
        return request;
    }

    private static SkillSignerPolicy signerPolicy() {
        return new SkillSignerPolicy(
                SkillSignerPolicy.SCHEMA_VERSION,
                List.of(
                        new SkillSignerPolicy.Entry(
                                SIGNER_A,
                                SkillSignerPolicy.SignerState.ACTIVE,
                                10L,
                                0L),
                        new SkillSignerPolicy.Entry(
                                SIGNER_B,
                                SkillSignerPolicy.SignerState.RETIRED,
                                1L,
                                0L),
                        new SkillSignerPolicy.Entry(
                                SIGNER_C,
                                SkillSignerPolicy.SignerState.REVOKED,
                                1L,
                                8L)));
    }

    private static void assertDecision(
            String caseId,
            DecisionReason expected,
            DecisionReason actual) {
        assertEquals(expected.name(), outcome(caseId));
        assertEquals(caseId, expected, actual);
    }

    private static void assertSigner(String caseId, SkillSignerPolicy.Decision decision) {
        assertEquals(outcome(caseId), decision.getCode().name());
        assertFalse(decision.isAccepted());
    }

    private static String outcome(String caseId) {
        return IdentityReplaySecurityCorpusContract.requireCase(caseId)
                .getExpectedOutcomeCode();
    }
}

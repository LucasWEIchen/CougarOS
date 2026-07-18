package com.centralbrain.runtime.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.scenario.ScenarioManifest;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public final class ProactiveConsentPolicyTest {
    private static final String OWNER_A = "a".repeat(64);
    private static final String OWNER_B = "b".repeat(64);
    private static final String SCENARIO_DIGEST = "c".repeat(64);
    private static final String SUGGESTION = "d".repeat(64);
    private static final String RECEIPT = "e".repeat(64);
    private static final String PRIVACY = "f".repeat(64);

    @Test
    public void exactGrantBindingMakesLowRiskCandidatePolicyEligibleOnly() {
        Harness harness = harness(4, allowed());
        ProactiveConsentPolicy.ConsentMutation grant = grant(
                "grant.cold.driver.v1", OWNER_A, ProactiveConsentPolicy.RiskClass.MEDIUM, 100);

        ProactiveConsentPolicy.MutationResult applied = harness.mutate(
                grant, "request.grant.cold.v1", ProactiveConsentPolicy.DrivingState.PARKED);
        assertEquals(ProactiveConsentPolicy.MutationCode.APPLIED, applied.getCode());
        assertNotNull(applied.getGrantDigest());
        assertEquals(1_100, applied.getGrantExpiresAtElapsedMs());

        ProactiveConsentPolicy.AdmissionDecision decision = harness.policy.evaluate(
                candidate(OWNER_A, VehicleCapability.CapabilityId.HVAC_POWER,
                        ScenarioManifest.Zone.ROW1_DRIVER,
                        ProactiveConsentPolicy.RiskClass.LOW));
        assertEquals(ProactiveConsentPolicy.AdmissionCode.POLICY_ELIGIBLE,
                decision.getCode());
        assertTrue(decision.isPolicyEligible());
        assertFalse(decision.isEffectDispatchAuthorized());
        assertTrue(decision.isSafetyRevalidationRequired());
    }

    @Test
    public void highAndCriticalNeverReceiveGenericGrantOrAdmission() {
        Harness harness = harness(4, allowed());
        for (ProactiveConsentPolicy.RiskClass risk : new ProactiveConsentPolicy.RiskClass[] {
                ProactiveConsentPolicy.RiskClass.HIGH,
                ProactiveConsentPolicy.RiskClass.CRITICAL}) {
            ProactiveConsentPolicy.ConsentMutation grant = grant(
                    "grant.high." + risk.name().toLowerCase(), OWNER_A, risk, 100);
            assertEquals(
                    ProactiveConsentPolicy.MutationCode.HIGH_RISK_GENERIC_GRANT_FORBIDDEN,
                    harness.mutate(grant, "request.high." + risk.name().toLowerCase(),
                            ProactiveConsentPolicy.DrivingState.PARKED).getCode());
            assertEquals(ProactiveConsentPolicy.AdmissionCode.EXPLICIT_APPROVAL_REQUIRED,
                    harness.policy.evaluate(candidate(
                            OWNER_A,
                            VehicleCapability.CapabilityId.HVAC_POWER,
                            ScenarioManifest.Zone.ROW1_DRIVER,
                            risk)).getCode());
        }
        assertEquals(0, harness.policy.snapshot().getActiveGrantCount());
    }

    @Test
    public void ownerScenarioCapabilityZoneDigestAndRiskMismatchFailClosed() {
        Harness harness = harness(4, allowed());
        ProactiveConsentPolicy.ConsentMutation grant = grant(
                "grant.cold.driver.v1", OWNER_A, ProactiveConsentPolicy.RiskClass.LOW, 100);
        harness.mutate(grant, "request.grant.cold.v1",
                ProactiveConsentPolicy.DrivingState.PARKED);

        assertNoGrant(harness, candidate(OWNER_B,
                VehicleCapability.CapabilityId.HVAC_POWER,
                ScenarioManifest.Zone.ROW1_DRIVER,
                ProactiveConsentPolicy.RiskClass.LOW));
        assertNoGrant(harness, candidate(OWNER_A,
                VehicleCapability.CapabilityId.HVAC_FAN_LEVEL,
                ScenarioManifest.Zone.ROW1_DRIVER,
                ProactiveConsentPolicy.RiskClass.LOW));
        assertNoGrant(harness, candidate(OWNER_A,
                VehicleCapability.CapabilityId.HVAC_POWER,
                ScenarioManifest.Zone.CABIN,
                ProactiveConsentPolicy.RiskClass.LOW));
        assertEquals(ProactiveConsentPolicy.AdmissionCode.RISK_EXCEEDS_GRANT,
                harness.policy.evaluate(candidate(
                        OWNER_A,
                        VehicleCapability.CapabilityId.HVAC_POWER,
                        ScenarioManifest.Zone.ROW1_DRIVER,
                        ProactiveConsentPolicy.RiskClass.MEDIUM)).getCode());

        ProactiveConsentPolicy.AutoExecutionCandidate wrongDigest =
                new ProactiveConsentPolicy.AutoExecutionCandidate(
                        SUGGESTION,
                        OWNER_A,
                        "scene.comfort.cold.v1",
                        "9".repeat(64),
                        VehicleCapability.CapabilityId.HVAC_POWER,
                        ScenarioManifest.Zone.ROW1_DRIVER,
                        ProactiveConsentPolicy.RiskClass.LOW);
        assertNoGrant(harness, wrongDigest);
    }

    @Test
    public void ttlRevocationReplayConflictAndCapacityAreDeterministic() {
        Harness harness = harness(1, allowed());
        ProactiveConsentPolicy.ConsentMutation grant = grant(
                "grant.cold.driver.v1", OWNER_A, ProactiveConsentPolicy.RiskClass.MEDIUM, 100);
        ProactiveConsentPolicy.MutationResult first = harness.mutate(
                grant, "request.grant.cold.v1", ProactiveConsentPolicy.DrivingState.PARKED);
        ProactiveConsentPolicy.MutationResult replay = harness.mutate(
                grant, "request.grant.cold.v1", ProactiveConsentPolicy.DrivingState.PARKED);
        assertEquals(ProactiveConsentPolicy.MutationCode.APPLIED, first.getCode());
        assertEquals(ProactiveConsentPolicy.MutationCode.REPLAYED, replay.getCode());
        assertEquals(first.getGrantDigest(), replay.getGrantDigest());

        ProactiveConsentPolicy.ConsentMutation conflicting = grant(
                "grant.other.driver.v1", OWNER_A, ProactiveConsentPolicy.RiskClass.LOW, 100);
        assertEquals(ProactiveConsentPolicy.MutationCode.REQUEST_CONFLICT,
                harness.mutate(conflicting, "request.grant.cold.v1",
                        ProactiveConsentPolicy.DrivingState.PARKED).getCode());
        assertEquals(ProactiveConsentPolicy.MutationCode.CAPACITY_EXCEEDED,
                harness.mutate(conflicting, "request.grant.other.v1",
                        ProactiveConsentPolicy.DrivingState.PARKED).getCode());

        ProactiveConsentPolicy.ConsentMutation revoke =
                ProactiveConsentPolicy.ConsentMutation.revoke(
                        "grant.cold.driver.v1", OWNER_A);
        assertEquals(ProactiveConsentPolicy.MutationCode.APPLIED,
                harness.mutate(revoke, "request.revoke.cold.v1",
                        ProactiveConsentPolicy.DrivingState.PARKED).getCode());
        assertNoGrant(harness, candidate(OWNER_A,
                VehicleCapability.CapabilityId.HVAC_POWER,
                ScenarioManifest.Zone.ROW1_DRIVER,
                ProactiveConsentPolicy.RiskClass.LOW));

        harness.now.set(1_200);
        ProactiveConsentPolicy.ConsentMutation expiring = grant(
                "grant.expiring.driver.v1", OWNER_A,
                ProactiveConsentPolicy.RiskClass.LOW, 10);
        harness.mutate(expiring, "request.expiring.v1",
                ProactiveConsentPolicy.DrivingState.PARKED);
        harness.now.set(1_210);
        assertEquals(0, harness.policy.snapshot().getActiveGrantCount());
    }

    @Test
    public void mutationRequiresParkedFreshEvidenceAndAvailableAuthority() {
        Harness harness = harness(4, allowed());
        ProactiveConsentPolicy.ConsentMutation grant = grant(
                "grant.cold.driver.v1", OWNER_A, ProactiveConsentPolicy.RiskClass.LOW, 100);
        assertEquals(ProactiveConsentPolicy.MutationCode.DRIVING_RESTRICTED,
                harness.mutate(grant, "request.moving.v1",
                        ProactiveConsentPolicy.DrivingState.MOVING).getCode());
        assertEquals(0, harness.policy.snapshot().getActiveGrantCount());

        ProactiveConsentPolicy.ConsentEvidence mismatch = new ProactiveConsentPolicy.ConsentEvidence(
                "request.mismatch.v1", "9".repeat(64), RECEIPT, PRIVACY, 1_000, 1_010);
        assertEquals(ProactiveConsentPolicy.MutationCode.INVALID_EVIDENCE,
                harness.policy.mutate(grant, ProactiveConsentPolicy.DrivingState.PARKED,
                        mismatch).getCode());

        Harness denied = harness(4, (mutation, evidence) ->
                ProactiveConsentPolicy.AuthorityDecision.DENIED);
        assertEquals(ProactiveConsentPolicy.MutationCode.AUTHORITY_DENIED,
                denied.mutate(grant, "request.denied.v1",
                        ProactiveConsentPolicy.DrivingState.PARKED).getCode());
        Harness unavailable = harness(4, (mutation, evidence) -> {
            throw new IllegalStateException("authority unavailable");
        });
        assertEquals(ProactiveConsentPolicy.MutationCode.AUTHORITY_UNAVAILABLE,
                unavailable.mutate(grant, "request.unavailable.v1",
                        ProactiveConsentPolicy.DrivingState.PARKED).getCode());
    }

    @Test
    public void productionAndExecutionBoundariesRemainClosed() {
        Harness harness = harness(4, allowed());
        ProactiveConsentPolicy.Snapshot snapshot = harness.policy.snapshot();
        assertTrue(snapshot.isPolicyOnly());
        assertTrue(snapshot.isProcessLocal());
        assertFalse(snapshot.isGrantPersistenceWired());
        assertFalse(snapshot.isProductionConsentAuthorityWired());
        assertFalse(snapshot.isAutoExecutionEnabled());
        assertFalse(snapshot.isEffectDispatchEnabled());
        assertFalse(snapshot.isRuntimeWired());
        assertFalse(snapshot.isHardwareAccessed());
        assertTrue(harness.policy.activeGrantDigests().isEmpty());
        assertNull(harness.policy.evaluate(candidate(
                OWNER_A,
                VehicleCapability.CapabilityId.HVAC_POWER,
                ScenarioManifest.Zone.ROW1_DRIVER,
                ProactiveConsentPolicy.RiskClass.LOW)).getGrantDigest());
    }

    private static void assertNoGrant(
            Harness harness,
            ProactiveConsentPolicy.AutoExecutionCandidate candidate) {
        assertEquals(ProactiveConsentPolicy.AdmissionCode.NO_ACTIVE_GRANT,
                harness.policy.evaluate(candidate).getCode());
    }

    private static ProactiveConsentPolicy.ConsentAuthority allowed() {
        return (mutation, evidence) -> ProactiveConsentPolicy.AuthorityDecision.ALLOWED;
    }

    private static ProactiveConsentPolicy.ConsentMutation grant(
            String grantId,
            String owner,
            ProactiveConsentPolicy.RiskClass risk,
            long ttlMs) {
        return ProactiveConsentPolicy.ConsentMutation.grant(
                grantId,
                owner,
                "scene.comfort.cold.v1",
                SCENARIO_DIGEST,
                VehicleCapability.CapabilityId.HVAC_POWER,
                ScenarioManifest.Zone.ROW1_DRIVER,
                risk,
                ttlMs);
    }

    private static ProactiveConsentPolicy.AutoExecutionCandidate candidate(
            String owner,
            VehicleCapability.CapabilityId capability,
            ScenarioManifest.Zone zone,
            ProactiveConsentPolicy.RiskClass risk) {
        return new ProactiveConsentPolicy.AutoExecutionCandidate(
                SUGGESTION,
                owner,
                "scene.comfort.cold.v1",
                SCENARIO_DIGEST,
                capability,
                zone,
                risk);
    }

    private static Harness harness(
            int capacity,
            ProactiveConsentPolicy.ConsentAuthority authority) {
        AtomicLong now = new AtomicLong(1_000);
        return new Harness(
                ProactiveConsentPolicy.createForContractTest(
                        capacity,
                        now::get,
                        authority),
                now);
    }

    private static final class Harness {
        final ProactiveConsentPolicy policy;
        final AtomicLong now;

        Harness(ProactiveConsentPolicy policy, AtomicLong now) {
            this.policy = policy;
            this.now = now;
        }

        ProactiveConsentPolicy.MutationResult mutate(
                ProactiveConsentPolicy.ConsentMutation mutation,
                String requestId,
                ProactiveConsentPolicy.DrivingState drivingState) {
            return policy.mutate(
                    mutation,
                    drivingState,
                    new ProactiveConsentPolicy.ConsentEvidence(
                            requestId,
                            mutation.getMutationDigest(),
                            RECEIPT,
                            PRIVACY,
                            now.get(),
                            now.get() + 10));
        }
    }
}

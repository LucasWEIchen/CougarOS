package com.centralbrain.runtime.skills;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class BoundedBuiltInSkillRuntimeTest {
    private static final String OWNER_A = repeat("a", 64);
    private static final String OWNER_B = repeat("b", 64);
    private static final String DIGEST_A = repeat("c", 64);
    private static final String DIGEST_B = repeat("d", 64);

    @Test
    public void compiledCatalogIsImmutableAndSignerBound() {
        BoundedBuiltInSkillRuntime runtime = runtime(4, 2, 4);
        List<BoundedBuiltInSkillRuntime.SkillManifest> manifests = runtime.listManifests();
        assertEquals(3, manifests.size());
        assertEquals(BoundedBuiltInSkillRuntime.SKILL_VEHICLE_STATE_QUERY,
                manifests.get(0).getSkillId());
        assertEquals(BoundedBuiltInSkillRuntime.SKILL_CABIN_PRECONDITION,
                manifests.get(1).getSkillId());
        assertEquals(BoundedBuiltInSkillRuntime.SKILL_CABIN_SCENE_NAP,
                manifests.get(2).getSkillId());
        assertEquals(BoundedBuiltInSkillRuntime.RiskClass.READ_ONLY,
                manifests.get(0).getRiskClass());
        assertFalse(manifests.get(0).getRequiredCapabilities().contains(
                BoundedBuiltInSkillRuntime.Capability.VEHICLE_CONTROL));
        assertEquals(BoundedBuiltInSkillRuntime.RiskClass.COMFORT_CONTROL,
                manifests.get(1).getRiskClass());
        assertTrue(manifests.get(1).getRequiredCapabilities().contains(
                BoundedBuiltInSkillRuntime.Capability.VEHICLE_CONTROL));
        assertEquals(EnumSet.of(BoundedBuiltInSkillRuntime.SafetyState.NORMAL),
                manifests.get(1).getAllowedSafetyStates());
        for (BoundedBuiltInSkillRuntime.SkillManifest manifest : manifests) {
            assertEquals("0.1.0", manifest.getVersion());
            assertTrue(manifest.isCompiledIn());
            assertTrue(manifest.isSignerAllowlistMatched());
            assertTrue(manifest.isArtifactDigestBound());
            assertFalse(manifest.isCryptographicArtifactVerificationPerformed());
            assertFalse(manifest.isDynamicLoadingAllowed());
        }
        try {
            manifests.clear();
            fail("catalog must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
        try {
            manifests.get(0).getRequiredCapabilities().clear();
            fail("capability set must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void invocationAdmissionIsIdempotentAndSchemaBound() {
        BoundedBuiltInSkillRuntime runtime = runtime(4, 2, 4);
        BoundedBuiltInSkillRuntime.TrustedInvocation request = invocation(
                OWNER_A,
                "client-a",
                BoundedBuiltInSkillRuntime.SKILL_VEHICLE_STATE_QUERY,
                "0.1.0",
                "skill.vehicle.state.query.input.v1",
                DIGEST_A,
                EnumSet.of(BoundedBuiltInSkillRuntime.Capability.VEHICLE_READ),
                BoundedBuiltInSkillRuntime.SafetyState.DEGRADED);
        BoundedBuiltInSkillRuntime.AdmissionResult admitted = runtime.admit(request);
        assertEquals(BoundedBuiltInSkillRuntime.AdmissionOutcome.ADMITTED,
                admitted.getOutcome());
        assertEquals(BoundedBuiltInSkillRuntime.AdmissionOutcome.REPLAYED,
                runtime.admit(request).getOutcome());
        assertFalse(admitted.getInvocation().isDispatchAllowed());
        BoundedBuiltInSkillRuntime.SkillManifest manifest = runtime.findManifest(
                BoundedBuiltInSkillRuntime.SKILL_VEHICLE_STATE_QUERY);
        assertEquals(manifest.getRouteTarget(), admitted.getInvocation().getRouteTarget());
        assertEquals(manifest.getArtifactDigest(), admitted.getInvocation().getArtifactDigest());
        assertEquals(manifest.getSignerDigest(), admitted.getInvocation().getSignerDigest());
        assertEquals(BoundedBuiltInSkillRuntime.AdmissionOutcome.CONFLICT,
                runtime.admit(invocation(
                        OWNER_A,
                        "client-a",
                        BoundedBuiltInSkillRuntime.SKILL_VEHICLE_STATE_QUERY,
                        "0.1.0",
                        "skill.vehicle.state.query.input.v1",
                        DIGEST_B,
                        EnumSet.of(BoundedBuiltInSkillRuntime.Capability.VEHICLE_READ),
                        BoundedBuiltInSkillRuntime.SafetyState.DEGRADED)).getOutcome());
        assertEquals(BoundedBuiltInSkillRuntime.AdmissionOutcome.UNKNOWN_SKILL,
                runtime.admit(invocation(
                        OWNER_B,
                        "unknown",
                        "unknown.skill",
                        "0.1.0",
                        "skill.unknown.input.v1",
                        DIGEST_A,
                        EnumSet.of(BoundedBuiltInSkillRuntime.Capability.VEHICLE_READ),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL)).getOutcome());
        assertEquals(BoundedBuiltInSkillRuntime.AdmissionOutcome.VERSION_MISMATCH,
                runtime.admit(invocation(
                        OWNER_B,
                        "version",
                        BoundedBuiltInSkillRuntime.SKILL_VEHICLE_STATE_QUERY,
                        "0.2.0",
                        "skill.vehicle.state.query.input.v1",
                        DIGEST_A,
                        EnumSet.of(BoundedBuiltInSkillRuntime.Capability.VEHICLE_READ),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL)).getOutcome());
        assertEquals(BoundedBuiltInSkillRuntime.AdmissionOutcome.INPUT_SCHEMA_MISMATCH,
                runtime.admit(invocation(
                        OWNER_B,
                        "schema",
                        BoundedBuiltInSkillRuntime.SKILL_VEHICLE_STATE_QUERY,
                        "0.1.0",
                        "skill.vehicle.state.query.input.v2",
                        DIGEST_A,
                        EnumSet.of(BoundedBuiltInSkillRuntime.Capability.VEHICLE_READ),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL)).getOutcome());
    }

    @Test
    public void capabilityAndSafetyStateFailClosed() {
        BoundedBuiltInSkillRuntime runtime = runtime(4, 4, 4);
        assertEquals(BoundedBuiltInSkillRuntime.AdmissionOutcome.CAPABILITY_DENIED,
                runtime.admit(invocation(
                        OWNER_A,
                        "capability",
                        BoundedBuiltInSkillRuntime.SKILL_CABIN_PRECONDITION,
                        "0.1.0",
                        "skill.cabin.precondition.input.v1",
                        DIGEST_A,
                        EnumSet.of(BoundedBuiltInSkillRuntime.Capability.VEHICLE_READ),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL)).getOutcome());
        assertEquals(BoundedBuiltInSkillRuntime.AdmissionOutcome.SAFETY_STATE_DENIED,
                runtime.admit(invocation(
                        OWNER_A,
                        "safety",
                        BoundedBuiltInSkillRuntime.SKILL_CABIN_PRECONDITION,
                        "0.1.0",
                        "skill.cabin.precondition.input.v1",
                        DIGEST_A,
                        EnumSet.of(
                                BoundedBuiltInSkillRuntime.Capability.VEHICLE_READ,
                                BoundedBuiltInSkillRuntime.Capability.VEHICLE_CONTROL),
                        BoundedBuiltInSkillRuntime.SafetyState.DEGRADED)).getOutcome());
        assertEquals(BoundedBuiltInSkillRuntime.AdmissionOutcome.ADMITTED,
                runtime.admit(invocation(
                        OWNER_A,
                        "allowed",
                        BoundedBuiltInSkillRuntime.SKILL_CABIN_PRECONDITION,
                        "0.1.0",
                        "skill.cabin.precondition.input.v1",
                        DIGEST_A,
                        EnumSet.of(
                                BoundedBuiltInSkillRuntime.Capability.VEHICLE_READ,
                                BoundedBuiltInSkillRuntime.Capability.VEHICLE_CONTROL),
                        BoundedBuiltInSkillRuntime.SafetyState.NORMAL)).getOutcome());
    }

    @Test
    public void ownerIsolationCancellationAndBoundsAreEnforced() {
        BoundedBuiltInSkillRuntime runtime = runtime(2, 1, 1);
        BoundedBuiltInSkillRuntime.AdmissionResult ownerA = runtime.admit(query(
                OWNER_A,
                "owner-a",
                DIGEST_A));
        assertEquals(BoundedBuiltInSkillRuntime.AdmissionOutcome.OWNER_LIMIT,
                runtime.admit(query(OWNER_A, "owner-a-2", DIGEST_B)).getOutcome());
        BoundedBuiltInSkillRuntime.AdmissionResult ownerB = runtime.admit(query(
                OWNER_B,
                "owner-b",
                DIGEST_B));
        assertEquals(BoundedBuiltInSkillRuntime.AdmissionOutcome.GLOBAL_LIMIT,
                runtime.admit(query(repeat("e", 64), "owner-c", DIGEST_A)).getOutcome());
        String ownerAId = ownerA.getInvocation().getInvocationId();
        assertNull(runtime.findOwned(ownerAId, OWNER_B));
        assertEquals(BoundedBuiltInSkillRuntime.CancelOutcome.NOT_FOUND,
                runtime.cancelOwned(ownerAId, OWNER_B));
        assertEquals(BoundedBuiltInSkillRuntime.CancelOutcome.APPLIED,
                runtime.cancelOwned(ownerAId, OWNER_A));
        assertEquals(BoundedBuiltInSkillRuntime.CancelOutcome.REPLAYED,
                runtime.cancelOwned(ownerAId, OWNER_A));
        BoundedBuiltInSkillRuntime.AdmissionResult replacement = runtime.admit(query(
                OWNER_A,
                "replacement",
                DIGEST_B));
        assertEquals(BoundedBuiltInSkillRuntime.AdmissionOutcome.ADMITTED,
                replacement.getOutcome());
        assertEquals(BoundedBuiltInSkillRuntime.CancelOutcome.APPLIED,
                runtime.cancelOwned(ownerB.getInvocation().getInvocationId(), OWNER_B));
        assertNull(runtime.findOwned(ownerAId, OWNER_A));
        BoundedBuiltInSkillRuntime.Snapshot snapshot = runtime.snapshot();
        assertEquals(1, snapshot.getCancelledInvocationCount());
        assertEquals(1, snapshot.getCancelledEvictionCount());
    }

    @Test
    public void snapshotKeepsExecutionAndHardwareDisabled() {
        BoundedBuiltInSkillRuntime.Snapshot snapshot = runtime(4, 2, 2).snapshot();
        assertEquals(3, snapshot.getManifestCount());
        assertTrue(snapshot.areAllManifestsCompiledIn());
        assertTrue(snapshot.areAllSignerAllowlistsMatched());
        assertFalse(snapshot.isCryptographicArtifactVerificationPerformed());
        assertFalse(snapshot.isDynamicCodeLoadingEnabled());
        assertFalse(snapshot.isNetworkAccessEnabled());
        assertFalse(snapshot.isProductionServiceWired());
        assertFalse(snapshot.isHardwareAccessed());
    }

    private static BoundedBuiltInSkillRuntime runtime(
            int maxActive,
            int maxPerOwner,
            int maxCancelled) {
        AtomicInteger ids = new AtomicInteger();
        return BoundedBuiltInSkillRuntime.createForContractTest(
                new BoundedBuiltInSkillRuntime.Limits(
                        maxActive,
                        maxPerOwner,
                        maxCancelled),
                () -> "test-" + ids.incrementAndGet());
    }

    private static BoundedBuiltInSkillRuntime.TrustedInvocation query(
            String owner,
            String clientId,
            String digest) {
        return invocation(
                owner,
                clientId,
                BoundedBuiltInSkillRuntime.SKILL_VEHICLE_STATE_QUERY,
                "0.1.0",
                "skill.vehicle.state.query.input.v1",
                digest,
                EnumSet.of(BoundedBuiltInSkillRuntime.Capability.VEHICLE_READ),
                BoundedBuiltInSkillRuntime.SafetyState.NORMAL);
    }

    private static BoundedBuiltInSkillRuntime.TrustedInvocation invocation(
            String owner,
            String clientId,
            String skillId,
            String version,
            String inputSchema,
            String inputDigest,
            Set<BoundedBuiltInSkillRuntime.Capability> capabilities,
            BoundedBuiltInSkillRuntime.SafetyState safetyState) {
        return BoundedBuiltInSkillRuntime.TrustedInvocation.fromRuntimePolicy(
                owner,
                clientId,
                skillId,
                version,
                inputSchema,
                inputDigest,
                capabilities,
                safetyState);
    }

    private static String repeat(String value, int count) {
        StringBuilder output = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }
}

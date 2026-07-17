package com.centralbrain.runtime.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.tools.ToolHealthSnapshot.Eligibility;
import com.centralbrain.runtime.tools.ToolHealthSnapshot.Observation;
import com.centralbrain.runtime.tools.ToolHealthSnapshot.State;
import com.centralbrain.runtime.tools.ToolManifest.FieldSchema;
import com.centralbrain.runtime.tools.ToolManifest.HealthContract;
import com.centralbrain.runtime.tools.ToolManifest.IdempotencyMode;
import com.centralbrain.runtime.tools.ToolManifest.ObjectSchema;
import com.centralbrain.runtime.tools.ToolManifest.RiskClass;
import com.centralbrain.runtime.tools.ToolRegistry.ErrorCode;
import com.centralbrain.runtime.tools.ToolRegistry.RegistrationException;
import com.centralbrain.runtime.tools.ToolResolver.FailureCode;
import com.centralbrain.runtime.tools.ToolResolver.Query;
import com.centralbrain.runtime.tools.ToolResolver.RegistrationState;
import com.centralbrain.runtime.tools.ToolResolver.Resolution;
import com.centralbrain.runtime.tools.ToolResolver.ResolutionState;
import com.centralbrain.runtime.tools.ToolResolver.UsabilityState;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public final class ToolRegistryResolverTest {
    private static final String FAMILY = "tool.cockpit.hvac.set";
    private static final String CAPABILITY = "vehicle.hvac.temperature";

    @Test
    public void registryIsBoundedImmutableDeduplicatedAndOrderIndependent() {
        ToolManifest first = manifest(1, RiskClass.MEDIUM);
        ToolManifest second = manifest(2, RiskClass.MEDIUM);
        ToolRegistry ascending = new ToolRegistry(List.of(first, second, first));
        ToolRegistry descending = new ToolRegistry(List.of(second, first));

        assertEquals(2, ascending.size());
        assertTrue(ascending.isRegistered(FAMILY));
        assertTrue(ascending.isRegistered(FAMILY, 2));
        assertEquals(1, ascending.manifestsFor(FAMILY).get(0).getVersion());
        assertEquals(2, ascending.manifestsFor(FAMILY).get(1).getVersion());
        assertEquals(ascending.getRegistryDigest(), descending.getRegistryDigest());
        assertTrue(ascending.getRegistryDigest().matches("[0-9a-f]{64}"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> ascending.manifestsFor(FAMILY).clear());
        assertRegistryCode(
                ErrorCode.REGISTRATION_LIMIT_EXCEEDED,
                () -> new ToolRegistry(Collections.nCopies(129, first)));
    }

    @Test
    public void sameIdentityVersionWithDifferentDigestConflictsDeterministically() {
        ToolManifest medium = manifest(1, RiskClass.MEDIUM);
        ToolManifest high = manifest(1, RiskClass.HIGH);

        assertRegistryCode(
                ErrorCode.CONTRACT_CONFLICT,
                () -> new ToolRegistry(List.of(medium, high)));
        assertRegistryCode(
                ErrorCode.CONTRACT_CONFLICT,
                () -> new ToolRegistry(List.of(high, medium)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Query(FAMILY, 2, 1, CAPABILITY, null));
    }

    @Test
    public void resolverSelectsHighestCompatibleHealthyVersionWithoutExecution() {
        ToolManifest first = manifest(1, RiskClass.MEDIUM);
        ToolManifest second = manifest(2, RiskClass.MEDIUM);
        ToolResolver resolver = new ToolResolver(new ToolRegistry(List.of(first, second)));
        ToolHealthSnapshot health = new ToolHealthSnapshot(List.of(
                healthy(first, 9_000L, 1L),
                healthy(second, 9_500L, 2L)));

        Resolution resolution = resolver.resolve(
                new Query(FAMILY, 1, 2, CAPABILITY, null), health, 10_000L);

        assertEquals(RegistrationState.REGISTERED, resolution.getRegistrationState());
        assertEquals(ResolutionState.RESOLVED, resolution.getResolutionState());
        assertEquals(UsabilityState.USABLE, resolution.getUsabilityState());
        assertEquals(FailureCode.NONE, resolution.getFailureCode());
        assertEquals(2, resolution.getManifest().getVersion());
        assertFalse(resolution.isExecutionEnabled());
    }

    @Test
    public void registeredResolvedAndUsableStatesRemainSeparate() {
        ToolManifest first = manifest(1, RiskClass.MEDIUM);
        ToolResolver resolver = new ToolResolver(new ToolRegistry(List.of(first)));

        Resolution absent = resolver.resolve(
                new Query("tool.cockpit.seat.set", 1, 1, "vehicle.seat.position", null),
                ToolHealthSnapshot.empty(),
                10_000L);
        assertEquals(RegistrationState.NOT_REGISTERED, absent.getRegistrationState());
        assertEquals(ResolutionState.NOT_RESOLVED, absent.getResolutionState());
        assertEquals(FailureCode.TOOL_NOT_REGISTERED, absent.getFailureCode());
        assertThrows(IllegalStateException.class, absent::getManifest);

        Resolution incompatible = resolver.resolve(
                new Query(FAMILY, 2, 3, CAPABILITY, null),
                ToolHealthSnapshot.empty(),
                10_000L);
        assertEquals(RegistrationState.REGISTERED, incompatible.getRegistrationState());
        assertEquals(ResolutionState.NOT_RESOLVED, incompatible.getResolutionState());
        assertEquals(FailureCode.NO_COMPATIBLE_VERSION, incompatible.getFailureCode());

        Resolution capabilityMismatch = resolver.resolve(
                new Query(FAMILY, 1, 1, "vehicle.hvac.fan", null),
                ToolHealthSnapshot.empty(),
                10_000L);
        assertEquals(FailureCode.CAPABILITY_MISMATCH, capabilityMismatch.getFailureCode());

        Resolution digestMismatch = resolver.resolve(
                new Query(FAMILY, 1, 1, CAPABILITY, "f".repeat(64)),
                ToolHealthSnapshot.empty(),
                10_000L);
        assertEquals(FailureCode.CONTRACT_DIGEST_MISMATCH, digestMismatch.getFailureCode());
    }

    @Test
    public void dynamicHealthFailsClosedAndNeverFallsBackToOlderVersion() {
        ToolManifest first = manifest(1, RiskClass.MEDIUM);
        ToolManifest second = manifest(2, RiskClass.MEDIUM);
        ToolResolver resolver = new ToolResolver(new ToolRegistry(List.of(first, second)));
        Query exactFirst = new Query(FAMILY, 1, 1, CAPABILITY, null);

        assertHealthFailure(
                resolver.resolve(exactFirst, ToolHealthSnapshot.empty(), 10_000L),
                FailureCode.HEALTH_MISSING);
        assertHealthFailure(
                resolver.resolve(
                        exactFirst,
                        new ToolHealthSnapshot(List.of(new Observation(
                                first.getHealthContract().getCheckId(),
                                State.UNKNOWN,
                                9_000L,
                                1L))),
                        10_000L),
                FailureCode.HEALTH_UNKNOWN);
        assertHealthFailure(
                resolver.resolve(
                        exactFirst,
                        new ToolHealthSnapshot(List.of(healthy(first, 1_000L, 1L))),
                        10_000L),
                FailureCode.HEALTH_STALE);
        assertEquals(
                Eligibility.CLOCK_INVALID,
                new ToolHealthSnapshot(List.of(healthy(first, 11_000L, 1L)))
                        .eligibility(first, 10_000L));
        assertEquals(
                Eligibility.CLOCK_INVALID,
                new ToolHealthSnapshot(List.of(new Observation(
                        first.getHealthContract().getCheckId(),
                        State.UNKNOWN,
                        11_000L,
                        1L))).eligibility(first, 10_000L));
        assertHealthFailure(
                resolver.resolve(
                        exactFirst,
                        new ToolHealthSnapshot(List.of(healthy(first, 9_000L, 1L))),
                        -1L),
                FailureCode.HEALTH_CLOCK_INVALID);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolHealthSnapshot(List.of(
                        healthy(first, 9_000L, 1L),
                        healthy(first, 9_500L, 2L))));

        ToolHealthSnapshot mixed = new ToolHealthSnapshot(List.of(
                healthy(first, 9_500L, 1L),
                new Observation(
                        second.getHealthContract().getCheckId(),
                        State.UNHEALTHY,
                        9_500L,
                        2L)));
        Resolution selectedHighest = resolver.resolve(
                new Query(FAMILY, 1, 2, CAPABILITY, null), mixed, 10_000L);
        assertEquals(2, selectedHighest.getManifest().getVersion());
        assertHealthFailure(selectedHighest, FailureCode.HEALTH_UNHEALTHY);
        assertFalse(selectedHighest.isExecutionEnabled());
    }

    private static void assertHealthFailure(Resolution resolution, FailureCode code) {
        assertEquals(RegistrationState.REGISTERED, resolution.getRegistrationState());
        assertEquals(ResolutionState.RESOLVED, resolution.getResolutionState());
        assertEquals(UsabilityState.NOT_USABLE, resolution.getUsabilityState());
        assertEquals(code, resolution.getFailureCode());
        assertFalse(resolution.isExecutionEnabled());
    }

    private static void assertRegistryCode(
            ErrorCode code, org.junit.function.ThrowingRunnable runnable) {
        RegistrationException exception = assertThrows(RegistrationException.class, runnable);
        assertEquals(code, exception.getErrorCode());
    }

    private static Observation healthy(
            ToolManifest manifest, long observedAtElapsedRealtimeMs, long revision) {
        return new Observation(
                manifest.getHealthContract().getCheckId(),
                State.HEALTHY,
                observedAtElapsedRealtimeMs,
                revision);
    }

    private static ToolManifest manifest(int version, RiskClass riskClass) {
        ObjectSchema input = new ObjectSchema(
                "tool.input.hvac-set.v" + version,
                version,
                512,
                List.of(
                        FieldSchema.sha256DigestField("requestDigest", true),
                        FieldSchema.integerField(
                                "temperatureDeciC", true, 160L, 300L)));
        ObjectSchema output = new ObjectSchema(
                "tool.output.hvac-set.v" + version,
                version,
                256,
                List.of(FieldSchema.stringField("status", true, 16)));
        return new ToolManifest(
                ToolManifest.SCHEMA_VERSION,
                FAMILY + ".v" + version,
                version,
                "runtime.builtin",
                input,
                output,
                CAPABILITY,
                riskClass,
                2_500L,
                IdempotencyMode.TOKEN_REQUIRED,
                new HealthContract(
                        "health.vehicle.hvac.v" + version, 5_000L, true));
    }
}

package com.centralbrain.runtime.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.identity.CallerIdentitySnapshot;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy.Capability;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy.Decision;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy.DecisionReason;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy.PrincipalRule;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

public final class CallerCapabilityPolicyTest {
    private static final String RUNTIME_SIGNER = repeat("a", 64);
    private static final String OTHER_SIGNER = repeat("b", 64);

    private final CallerCapabilityPolicy policy = new CallerCapabilityPolicy(
            Collections.singletonList(new PrincipalRule(
                    "com.centralbrain.allowed",
                    Collections.singletonList(RUNTIME_SIGNER),
                    EnumSet.of(Capability.PROTOCOL_READ, Capability.TASK_SUBMIT))));

    @Test
    public void allowsOnlyConfiguredCapabilityForExactPackageSignerPair() {
        CallerIdentitySnapshot caller = identity(
                "com.centralbrain.allowed",
                RUNTIME_SIGNER);
        assertTrue(policy.evaluate(caller, Capability.PROTOCOL_READ).isAllowed());
        assertTrue(policy.evaluate(caller, Capability.TASK_SUBMIT).isAllowed());

        Decision missingCapability = policy.evaluate(caller, Capability.TASK_CANCEL_OWN);
        assertFalse(missingCapability.isAllowed());
        assertEquals(DecisionReason.CAPABILITY_NOT_GRANTED, missingCapability.getReason());
    }

    @Test
    public void deniesUnknownPackageEvenWhenSignerMatches() {
        Decision decision = policy.evaluate(
                identity("com.centralbrain.unknown", RUNTIME_SIGNER),
                Capability.PROTOCOL_READ);
        assertFalse(decision.isAllowed());
        assertEquals(DecisionReason.PACKAGE_NOT_CONFIGURED, decision.getReason());
    }

    @Test
    public void deniesConfiguredPackageWhenCurrentSignerSetDiffers() {
        Decision decision = policy.evaluate(
                identity("com.centralbrain.allowed", OTHER_SIGNER),
                Capability.PROTOCOL_READ);
        assertFalse(decision.isAllowed());
        assertEquals(DecisionReason.CURRENT_SIGNER_MISMATCH, decision.getReason());
    }

    @Test
    public void deniesUnresolvedIdentity() {
        Decision decision = policy.evaluate(
                CallerIdentitySnapshot.unresolved(11001, -1, "test"),
                Capability.PROTOCOL_READ);
        assertFalse(decision.isAllowed());
        assertEquals(DecisionReason.IDENTITY_UNRESOLVED, decision.getReason());
    }

    @Test
    public void rejectsWildcardAndMalformedPrincipalNames() {
        assertThrows(IllegalArgumentException.class, () -> new PrincipalRule(
                "com.centralbrain.*",
                Collections.singletonList(RUNTIME_SIGNER),
                EnumSet.of(Capability.PROTOCOL_READ)));
        assertThrows(IllegalArgumentException.class, () -> new PrincipalRule(
                "not a package",
                Collections.singletonList(RUNTIME_SIGNER),
                EnumSet.of(Capability.PROTOCOL_READ)));
    }

    @Test
    public void sharedUidCombinesConfiguredCapabilitiesButFailsAnySignerMismatch() {
        CallerCapabilityPolicy sharedUidPolicy = new CallerCapabilityPolicy(Arrays.asList(
                new PrincipalRule(
                        "com.centralbrain.first",
                        Collections.singletonList(RUNTIME_SIGNER),
                        EnumSet.of(Capability.PROTOCOL_READ)),
                new PrincipalRule(
                        "com.centralbrain.second",
                        Collections.singletonList(RUNTIME_SIGNER),
                        EnumSet.of(Capability.TASK_SUBMIT))));
        CallerIdentitySnapshot validSharedUid = CallerIdentitySnapshot.resolved(
                11001,
                0,
                Arrays.asList(
                        packageIdentity("com.centralbrain.first", RUNTIME_SIGNER),
                        packageIdentity("com.centralbrain.second", RUNTIME_SIGNER)));
        CallerIdentitySnapshot mismatchedSharedUid = CallerIdentitySnapshot.resolved(
                11001,
                0,
                Arrays.asList(
                        packageIdentity("com.centralbrain.first", RUNTIME_SIGNER),
                        packageIdentity("com.centralbrain.second", OTHER_SIGNER)));

        assertTrue(sharedUidPolicy.evaluate(
                validSharedUid,
                Capability.PROTOCOL_READ).isAllowed());
        assertTrue(sharedUidPolicy.evaluate(
                validSharedUid,
                Capability.TASK_SUBMIT).isAllowed());
        assertEquals(
                DecisionReason.CURRENT_SIGNER_MISMATCH,
                sharedUidPolicy.evaluate(
                        mismatchedSharedUid,
                        Capability.PROTOCOL_READ).getReason());
    }

    private static CallerIdentitySnapshot identity(String packageName, String signer) {
        return CallerIdentitySnapshot.resolved(
                11001,
                0,
                Collections.singletonList(packageIdentity(packageName, signer)));
    }

    private static CallerIdentitySnapshot.PackageIdentity packageIdentity(
            String packageName,
            String signer) {
        return new CallerIdentitySnapshot.PackageIdentity(
                packageName,
                Collections.singletonList(signer));
    }

    private static String repeat(String value, int count) {
        return String.join("", Collections.nCopies(count, value));
    }
}

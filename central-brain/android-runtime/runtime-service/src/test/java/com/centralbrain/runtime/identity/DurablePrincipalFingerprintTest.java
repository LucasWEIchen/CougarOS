package com.centralbrain.runtime.identity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class DurablePrincipalFingerprintTest {
    private static final String DIGEST_A = repeat("a", 64);
    private static final String DIGEST_B = repeat("b", 64);

    @Test
    public void isCanonicalAndStableAcrossUidReassignment() {
        CallerIdentitySnapshot first = resolved(
                11001,
                7,
                packageIdentity("com.example.b", DIGEST_B),
                packageIdentity("com.example.a", DIGEST_A));
        CallerIdentitySnapshot reassignedUid = resolved(
                12002,
                7,
                packageIdentity("com.example.a", DIGEST_A),
                packageIdentity("com.example.b", DIGEST_B));

        String firstFingerprint = DurablePrincipalFingerprint.from(first);
        assertEquals(64, firstFingerprint.length());
        assertEquals(firstFingerprint, DurablePrincipalFingerprint.from(reassignedUid));
    }

    @Test
    public void bindsAndroidUserPackageAndSignerPairs() {
        CallerIdentitySnapshot baseline = resolved(
                11001,
                7,
                packageIdentity("com.example.a", DIGEST_A),
                packageIdentity("com.example.b", DIGEST_B));
        CallerIdentitySnapshot differentUser = resolved(
                11001,
                8,
                packageIdentity("com.example.a", DIGEST_A),
                packageIdentity("com.example.b", DIGEST_B));
        CallerIdentitySnapshot swappedSigners = resolved(
                11001,
                7,
                packageIdentity("com.example.a", DIGEST_B),
                packageIdentity("com.example.b", DIGEST_A));

        assertNotEquals(
                DurablePrincipalFingerprint.from(baseline),
                DurablePrincipalFingerprint.from(differentUser));
        assertNotEquals(
                DurablePrincipalFingerprint.from(baseline),
                DurablePrincipalFingerprint.from(swappedSigners));
    }

    @Test
    public void rejectsUnresolvedIdentity() {
        assertThrows(
                SecurityException.class,
                () -> DurablePrincipalFingerprint.from(
                        CallerIdentitySnapshot.unresolved(11001, -1, "missing user")));
    }

    private static CallerIdentitySnapshot resolved(
            int uid,
            long userSerial,
            CallerIdentitySnapshot.PackageIdentity... packages) {
        return CallerIdentitySnapshot.resolved(uid, userSerial, Arrays.asList(packages));
    }

    private static CallerIdentitySnapshot.PackageIdentity packageIdentity(
            String packageName,
            String signerDigest) {
        return new CallerIdentitySnapshot.PackageIdentity(
                packageName,
                Collections.singletonList(signerDigest));
    }

    private static String repeat(String value, int count) {
        return String.join("", Collections.nCopies(count, value));
    }
}

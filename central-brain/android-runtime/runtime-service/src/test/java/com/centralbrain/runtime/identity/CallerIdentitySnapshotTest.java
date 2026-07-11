package com.centralbrain.runtime.identity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class CallerIdentitySnapshotTest {
    private static final String DIGEST_A = repeat("a", 64);
    private static final String DIGEST_B = repeat("b", 64);

    @Test
    public void canonicalizesEvidenceWithoutFlatteningPackageSignerPairs() {
        CallerIdentitySnapshot first = CallerIdentitySnapshot.resolved(
                11001,
                7,
                Arrays.asList(packageIdentity("com.example.b", DIGEST_B.toUpperCase()),
                        packageIdentity("com.example.a", DIGEST_A)));
        CallerIdentitySnapshot equivalent = CallerIdentitySnapshot.resolved(
                11001,
                7,
                Arrays.asList(packageIdentity("com.example.a", DIGEST_A),
                        packageIdentity("com.example.b", DIGEST_B)));
        CallerIdentitySnapshot swappedSigners = CallerIdentitySnapshot.resolved(
                11001,
                7,
                Arrays.asList(packageIdentity("com.example.a", DIGEST_B),
                        packageIdentity("com.example.b", DIGEST_A)));

        assertEquals("com.example.a", first.getPackages().get(0).getPackageName());
        assertEquals(DIGEST_B, first.getPackages().get(1).getCurrentSignerSha256().get(0));
        assertTrue(first.samePrincipal(equivalent));
        assertFalse(first.samePrincipal(swappedSigners));
    }

    @Test
    public void unresolvedAndMalformedEvidenceFailClosed() {
        CallerIdentitySnapshot unresolved = CallerIdentitySnapshot.unresolved(
                11001,
                -1,
                "missing user");
        assertFalse(unresolved.samePrincipal(unresolved));
        assertThrows(
                IllegalArgumentException.class,
                () -> packageIdentity("com.example.invalid", "not-a-digest"));
        assertThrows(
                IllegalArgumentException.class,
                () -> CallerIdentitySnapshot.resolved(
                        11001,
                        0,
                        Arrays.asList(
                                packageIdentity("com.example.duplicate", DIGEST_A),
                                packageIdentity("com.example.duplicate", DIGEST_B))));
    }

    private static CallerIdentitySnapshot.PackageIdentity packageIdentity(
            String packageName,
            String digest) {
        return new CallerIdentitySnapshot.PackageIdentity(
                packageName,
                Collections.singletonList(digest));
    }

    private static String repeat(String value, int count) {
        return String.join("", Collections.nCopies(count, value));
    }
}

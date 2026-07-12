package com.centralbrain.runtime.blackbox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BlackBoxEnvironmentSnapshotTest {
    private static final String SIGNER =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void acceptsOrdinaryApi33ApplicationAndKeepsHardwareFalse() {
        BlackBoxEnvironmentSnapshot snapshot = snapshot(33, true, true, false);

        assertTrue(snapshot.isAccepted());
        assertFalse(snapshot.isAutomotiveFeatureAdvertised());
        assertTrue(snapshot.logFields().contains("ordinary_data_app=true"));
        assertTrue(snapshot.logFields().contains("private_vendor_api_probed=false"));
        assertTrue(snapshot.logFields().contains("hardware_accessed=false"));
    }

    @Test
    public void rejectsNonApi33EvidenceWithoutRewritingIdentity() {
        BlackBoxEnvironmentSnapshot snapshot = snapshot(34, true, true, false);

        assertFalse(snapshot.isAccepted());
        assertTrue(snapshot.logFields().contains("android_api=34"));
    }

    @Test
    public void rejectsMissing64BitTargetAbi() {
        BlackBoxEnvironmentSnapshot snapshot = snapshot(33, true, false, false);

        assertFalse(snapshot.isAccepted());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsHardwareAccessClaim() {
        snapshot(33, true, true, true);
    }

    private static BlackBoxEnvironmentSnapshot snapshot(
            int apiLevel,
            boolean process64Bit,
            boolean targetAbiSupported,
            boolean hardwareAccessed) {
        return BlackBoxEnvironmentSnapshot.create(
                apiLevel,
                process64Bit,
                targetAbiSupported,
                true,
                true,
                false,
                10123,
                2L,
                "0.3.0-b3",
                "x86_64",
                "x86_64",
                "/data/app/example/base.apk",
                "/data/app/example/lib/x86_64",
                "/data/user/0/com.centralbrain.runtime",
                SIGNER,
                "example/fingerprint",
                true,
                true,
                false,
                false,
                hardwareAccessed);
    }
}

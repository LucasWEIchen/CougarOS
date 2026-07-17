package com.centralbrain.runtime.skills;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.skills.SkillArtifactVerifier.FailureCode;
import com.centralbrain.runtime.skills.SkillArtifactVerifier.SkillPackageManifest;
import com.centralbrain.runtime.skills.SkillArtifactVerifier.VerificationEvidence;
import com.centralbrain.runtime.skills.SkillArtifactVerifier.VerificationResult;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SkillArtifactVerifierTest {
    private static final String ACTIVE_SIGNER = "a".repeat(64);
    private static final String RETIRED_SIGNER = "b".repeat(64);
    private static final String REVOKED_SIGNER = "c".repeat(64);
    private static final String ARTIFACT = "d".repeat(64);
    private static final String OTHER = "e".repeat(64);
    private static final String SKILL = "cabin.scene.nap";
    private static final String CAPABILITY = "vehicle.cabin.comfort";

    @Test
    public void policiesAreBoundedImmutableCanonicalAndDigestStable() {
        SkillSignerPolicy firstSigner = signerPolicy(List.of(
                signer(ACTIVE_SIGNER, SkillSignerPolicy.SignerState.ACTIVE, 10L, 0L),
                signer(RETIRED_SIGNER, SkillSignerPolicy.SignerState.RETIRED, 1L, 0L),
                signer(REVOKED_SIGNER, SkillSignerPolicy.SignerState.REVOKED, 1L, 8L)));
        SkillSignerPolicy secondSigner = signerPolicy(List.of(
                signer(REVOKED_SIGNER, SkillSignerPolicy.SignerState.REVOKED, 1L, 8L),
                signer(ACTIVE_SIGNER, SkillSignerPolicy.SignerState.ACTIVE, 10L, 0L),
                signer(RETIRED_SIGNER, SkillSignerPolicy.SignerState.RETIRED, 1L, 0L)));
        assertEquals(firstSigner.getPolicyDigest(), secondSigner.getPolicyDigest());
        assertFalse(firstSigner.isTrustedSignerEvidenceSourceConfigured());
        assertFalse(firstSigner.isHardwareBackedAttestationRequired());
        assertThrows(UnsupportedOperationException.class, () -> firstSigner.getEntries().clear());

        SkillVersionPolicy firstVersion = versionPolicy(List.of(versionEntry(SKILL)));
        SkillVersionPolicy secondVersion = versionPolicy(List.of(versionEntry(SKILL)));
        assertEquals(firstVersion.getPolicyDigest(), secondVersion.getPolicyDigest());
        assertEquals("1.4.0", firstVersion.getCurrentRuntimeVersion());
        assertFalse(firstVersion.isProductionRollbackAuthorityConfigured());
        assertThrows(UnsupportedOperationException.class, () -> firstVersion.getEntries().clear());

        SkillArtifactVerifier verifier = verifier(firstSigner, firstVersion);
        SkillArtifactVerifier reordered = new SkillArtifactVerifier(
                secondSigner,
                secondVersion,
                Map.of(SKILL, Set.of("vehicle.cabin.read", CAPABILITY)));
        assertEquals(verifier.getPolicyDigest(), reordered.getPolicyDigest());
        assertFalse(verifier.isTrustedEvidenceSourceConfigured());
        assertFalse(verifier.isPackageSignatureCryptographicallyVerified());
        assertFalse(verifier.isDynamicLoadingEnabled());
        assertFalse(verifier.isExecutionEnabled());
        assertFalse(verifier.isProductionWired());
    }

    @Test
    public void exactDigestSignerManifestRuntimeAndCapabilityVerifyStatically() {
        SkillArtifactVerifier verifier = verifier(signerPolicy(), versionPolicy());
        SkillPackageManifest manifest = manifest(
                "1.2.0", ACTIVE_SIGNER, ARTIFACT, "1.0.0", "2.0.0", Set.of(CAPABILITY));
        VerificationResult result = verifier.verify(evidence(
                manifest, manifest.getManifestDigest(), ARTIFACT, ACTIVE_SIGNER, 12L, "1.1.0"));

        assertTrue(result.isVerified());
        assertEquals(FailureCode.NONE, result.getFailureCode());
        assertEquals(SKILL, result.getVerifiedPackage().getSkillId());
        assertEquals("1.2.0", result.getVerifiedPackage().getVersion());
        assertEquals(manifest.getManifestDigest(), result.getVerifiedPackage().getManifestDigest());
        assertEquals(verifier.getPolicyDigest(), result.getVerifiedPackage().getPolicyDigest());
        assertEquals(Set.of(CAPABILITY), result.getVerifiedPackage().getCapabilities());
        assertFalse(result.isDynamicLoadAllowed());
        assertFalse(result.isExecutionAllowed());
        assertFalse(result.getVerifiedPackage().isDynamicLoadAllowed());
        assertFalse(result.getVerifiedPackage().isExecutionAllowed());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.getVerifiedPackage().getCapabilities().clear());

        SkillPackageManifest reordered = manifest(
                "1.2.0",
                ACTIVE_SIGNER,
                ARTIFACT,
                "1.0.0",
                "2.0.0",
                Set.of(CAPABILITY));
        assertEquals(manifest.getManifestDigest(), reordered.getManifestDigest());
        SkillPackageManifest changed = manifest(
                "1.2.1", ACTIVE_SIGNER, ARTIFACT, "1.0.0", "2.0.0", Set.of(CAPABILITY));
        assertNotEquals(manifest.getManifestDigest(), changed.getManifestDigest());
    }

    @Test
    public void manifestArtifactAndSignerEvidenceMismatchFailClosed() {
        SkillArtifactVerifier verifier = verifier(signerPolicy(), versionPolicy());
        SkillPackageManifest manifest = manifest(
                "1.2.0", ACTIVE_SIGNER, ARTIFACT, "1.0.0", "2.0.0", Set.of(CAPABILITY));

        assertRejected(
                FailureCode.MANIFEST_DIGEST_MISMATCH,
                verifier.verify(evidence(manifest, OTHER, ARTIFACT, ACTIVE_SIGNER, 12L, null)));
        assertRejected(
                FailureCode.ARTIFACT_DIGEST_MISMATCH,
                verifier.verify(evidence(
                        manifest, manifest.getManifestDigest(), OTHER, ACTIVE_SIGNER, 12L, null)));
        assertRejected(
                FailureCode.SIGNER_EVIDENCE_MISMATCH,
                verifier.verify(evidence(
                        manifest, manifest.getManifestDigest(), ARTIFACT, OTHER, 12L, null)));

        SkillPackageManifest retired = manifest(
                "1.2.0", RETIRED_SIGNER, ARTIFACT, "1.0.0", "2.0.0", Set.of(CAPABILITY));
        assertRejected(
                FailureCode.RETIRED_SIGNER,
                verifier.verify(evidence(
                        retired,
                        retired.getManifestDigest(),
                        ARTIFACT,
                        RETIRED_SIGNER,
                        12L,
                        null)));
        SkillPackageManifest revoked = manifest(
                "1.2.0", REVOKED_SIGNER, ARTIFACT, "1.0.0", "2.0.0", Set.of(CAPABILITY));
        assertRejected(
                FailureCode.REVOKED_SIGNER,
                verifier.verify(evidence(
                        revoked,
                        revoked.getManifestDigest(),
                        ARTIFACT,
                        REVOKED_SIGNER,
                        12L,
                        null)));
    }

    @Test
    public void runtimeVersionEpochDowngradeAndCapabilityFailClosed() {
        SkillArtifactVerifier verifier = verifier(signerPolicy(), versionPolicy());
        assertRejected(
                FailureCode.VERSION_BELOW_MINIMUM,
                verify(verifier, manifest(
                        "0.9.9", ACTIVE_SIGNER, ARTIFACT, "1.0.0", "2.0.0", Set.of(CAPABILITY)), 12L, null));
        assertRejected(
                FailureCode.VERSION_ABOVE_MAXIMUM,
                verify(verifier, manifest(
                        "2.1.0", ACTIVE_SIGNER, ARTIFACT, "1.0.0", "2.0.0", Set.of(CAPABILITY)), 12L, null));
        assertRejected(
                FailureCode.ARTIFACT_EPOCH_TOO_OLD,
                verify(verifier, manifest(
                        "1.2.0", ACTIVE_SIGNER, ARTIFACT, "1.0.0", "2.0.0", Set.of(CAPABILITY)), 10L, null));
        assertRejected(
                FailureCode.RUNTIME_TOO_OLD,
                verify(verifier, manifest(
                        "1.2.0", ACTIVE_SIGNER, ARTIFACT, "1.5.0", "2.0.0", Set.of(CAPABILITY)), 12L, null));
        assertRejected(
                FailureCode.RUNTIME_TOO_NEW,
                verify(verifier, manifest(
                        "1.2.0", ACTIVE_SIGNER, ARTIFACT, "1.0.0", "1.3.9", Set.of(CAPABILITY)), 12L, null));
        assertRejected(
                FailureCode.DOWNGRADE_DENIED,
                verify(verifier, manifest(
                        "1.2.0", ACTIVE_SIGNER, ARTIFACT, "1.0.0", "2.0.0", Set.of(CAPABILITY)), 12L, "1.3.0"));
        assertRejected(
                FailureCode.CAPABILITY_DENIED,
                verify(verifier, manifest(
                        "1.2.0",
                        ACTIVE_SIGNER,
                        ARTIFACT,
                        "1.0.0",
                        "2.0.0",
                        Set.of(CAPABILITY, "vehicle.powertrain.control")),
                        12L,
                        null));
    }

    @Test
    public void malformedAndUnboundedPoliciesAndEvidenceAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> signerPolicy(List.of(signer(
                        ACTIVE_SIGNER, SkillSignerPolicy.SignerState.REVOKED, 10L, 9L))));
        assertThrows(
                IllegalArgumentException.class,
                () -> signerPolicy(List.of(
                        signer(RETIRED_SIGNER, SkillSignerPolicy.SignerState.RETIRED, 1L, 0L))));
        assertThrows(
                IllegalArgumentException.class,
                () -> versionPolicy(List.of(new SkillVersionPolicy.Entry(
                        SKILL, "2.0.0", "1.0.0", 1L, false))));
        assertThrows(
                IllegalArgumentException.class,
                () -> manifest(
                        "01.0.0", ACTIVE_SIGNER, ARTIFACT, "1.0.0", "2.0.0", Set.of(CAPABILITY)));
        assertThrows(
                IllegalArgumentException.class,
                () -> manifest(
                        "1.0.0", ACTIVE_SIGNER, ARTIFACT, "2.0.0", "1.0.0", Set.of(CAPABILITY)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillArtifactVerifier(
                        signerPolicy(), versionPolicy(), Map.of(SKILL, Set.of())));

        SkillPackageManifest manifest = manifest(
                "1.2.0", ACTIVE_SIGNER, ARTIFACT, "1.0.0", "2.0.0", Set.of(CAPABILITY));
        assertThrows(
                IllegalArgumentException.class,
                () -> evidence(
                        manifest, manifest.getManifestDigest(), ARTIFACT, ACTIVE_SIGNER, 0L, null));
    }

    private static VerificationResult verify(
            SkillArtifactVerifier verifier,
            SkillPackageManifest manifest,
            long artifactEpoch,
            String highestAcceptedVersion) {
        return verifier.verify(evidence(
                manifest,
                manifest.getManifestDigest(),
                manifest.getArtifactDigest(),
                manifest.getSignerDigest(),
                artifactEpoch,
                highestAcceptedVersion));
    }

    private static SkillArtifactVerifier verifier(
            SkillSignerPolicy signerPolicy, SkillVersionPolicy versionPolicy) {
        return new SkillArtifactVerifier(
                signerPolicy,
                versionPolicy,
                Map.of(SKILL, Set.of(CAPABILITY, "vehicle.cabin.read")));
    }

    private static SkillSignerPolicy signerPolicy() {
        return signerPolicy(List.of(
                signer(ACTIVE_SIGNER, SkillSignerPolicy.SignerState.ACTIVE, 10L, 0L),
                signer(RETIRED_SIGNER, SkillSignerPolicy.SignerState.RETIRED, 1L, 0L),
                signer(REVOKED_SIGNER, SkillSignerPolicy.SignerState.REVOKED, 1L, 8L)));
    }

    private static SkillSignerPolicy signerPolicy(List<SkillSignerPolicy.Entry> entries) {
        return new SkillSignerPolicy(SkillSignerPolicy.SCHEMA_VERSION, entries);
    }

    private static SkillSignerPolicy.Entry signer(
            String digest,
            SkillSignerPolicy.SignerState state,
            long activationEpoch,
            long revocationEpoch) {
        return new SkillSignerPolicy.Entry(digest, state, activationEpoch, revocationEpoch);
    }

    private static SkillVersionPolicy versionPolicy() {
        return versionPolicy(List.of(versionEntry(SKILL)));
    }

    private static SkillVersionPolicy versionPolicy(List<SkillVersionPolicy.Entry> entries) {
        return new SkillVersionPolicy(SkillVersionPolicy.SCHEMA_VERSION, "1.4.0", entries);
    }

    private static SkillVersionPolicy.Entry versionEntry(String skillId) {
        return new SkillVersionPolicy.Entry(skillId, "1.0.0", "2.0.0", 11L, false);
    }

    private static SkillPackageManifest manifest(
            String version,
            String signerDigest,
            String artifactDigest,
            String minimumRuntime,
            String maximumRuntime,
            Set<String> capabilities) {
        return new SkillPackageManifest(
                SkillArtifactVerifier.MANIFEST_SCHEMA_VERSION,
                SKILL,
                version,
                artifactDigest,
                signerDigest,
                minimumRuntime,
                maximumRuntime,
                capabilities);
    }

    private static VerificationEvidence evidence(
            SkillPackageManifest manifest,
            String declaredManifestDigest,
            String measuredArtifactDigest,
            String observedSignerDigest,
            long artifactEpoch,
            String highestAcceptedVersion) {
        return new VerificationEvidence(
                manifest,
                declaredManifestDigest,
                measuredArtifactDigest,
                observedSignerDigest,
                artifactEpoch,
                highestAcceptedVersion);
    }

    private static void assertRejected(FailureCode code, VerificationResult result) {
        assertFalse(result.isVerified());
        assertEquals(FailureCode.class, result.getFailureCode().getClass());
        assertEquals(code, result.getFailureCode());
        assertNull(result.getVerifiedPackage());
        assertFalse(result.isDynamicLoadAllowed());
        assertFalse(result.isExecutionAllowed());
    }
}

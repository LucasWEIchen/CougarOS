package com.centralbrain.runtime.skills;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.skills.SkillArtifactVerifier.FailureCode;
import com.centralbrain.runtime.skills.SkillArtifactVerifier.SkillPackageManifest;
import com.centralbrain.runtime.skills.SkillArtifactVerifier.VerificationEvidence;
import com.centralbrain.runtime.skills.SkillArtifactVerifier.VerificationResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SkillArtifactVerifierProbeActivity extends Activity {
    private static final String TAG = "CbSkillVerifier";
    private static final String SKILL = "cabin.scene.nap";
    private static final String CAPABILITY = "vehicle.cabin.comfort";
    private static final String ACTIVE_SIGNER = "a".repeat(64);
    private static final String REVOKED_SIGNER = "b".repeat(64);
    private static final String ARTIFACT = "c".repeat(64);
    private static final String OTHER = "d".repeat(64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            SkillSignerPolicy signerPolicy = new SkillSignerPolicy(
                    SkillSignerPolicy.SCHEMA_VERSION,
                    List.of(
                            new SkillSignerPolicy.Entry(
                                    ACTIVE_SIGNER,
                                    SkillSignerPolicy.SignerState.ACTIVE,
                                    10L,
                                    0L),
                            new SkillSignerPolicy.Entry(
                                    REVOKED_SIGNER,
                                    SkillSignerPolicy.SignerState.REVOKED,
                                    1L,
                                    8L)));
            SkillVersionPolicy versionPolicy = new SkillVersionPolicy(
                    SkillVersionPolicy.SCHEMA_VERSION,
                    "1.4.0",
                    List.of(new SkillVersionPolicy.Entry(
                            SKILL, "1.0.0", "2.0.0", 11L, false)));
            SkillArtifactVerifier verifier = new SkillArtifactVerifier(
                    signerPolicy,
                    versionPolicy,
                    Map.of(SKILL, Set.of(CAPABILITY)));
            SkillPackageManifest manifest = manifest(ACTIVE_SIGNER, Set.of(CAPABILITY));
            VerificationResult accepted = verifier.verify(evidence(
                    manifest,
                    manifest.getManifestDigest(),
                    ARTIFACT,
                    ACTIVE_SIGNER,
                    "1.1.0"));

            boolean artifactHash = accepted.isVerified()
                    && accepted.getVerifiedPackage() != null
                    && ARTIFACT.equals(accepted.getVerifiedPackage().getArtifactDigest());
            boolean manifestDigest = accepted.isVerified()
                    && manifest.getManifestDigest().matches("[0-9a-f]{64}")
                    && manifest.getManifestDigest().equals(
                            accepted.getVerifiedPackage().getManifestDigest());
            boolean signer = accepted.isVerified()
                    && signerPolicy.evaluate(ACTIVE_SIGNER, 12L).isAccepted()
                    && !signerPolicy.isTrustedSignerEvidenceSourceConfigured();
            boolean runtimeVersion = accepted.isVerified()
                    && versionPolicy.evaluate(
                            SKILL, "1.2.0", "1.0.0", "2.0.0", 12L, "1.1.0")
                            .isAccepted();
            boolean capability = accepted.isVerified()
                    && accepted.getVerifiedPackage().getCapabilities().equals(Set.of(CAPABILITY));

            SkillPackageManifest revoked = manifest(REVOKED_SIGNER, Set.of(CAPABILITY));
            VerificationResult revokedResult = verifier.verify(evidence(
                    revoked,
                    revoked.getManifestDigest(),
                    ARTIFACT,
                    REVOKED_SIGNER,
                    null));
            VerificationResult hashMismatch = verifier.verify(evidence(
                    manifest,
                    manifest.getManifestDigest(),
                    OTHER,
                    ACTIVE_SIGNER,
                    null));
            VerificationResult downgrade = verifier.verify(evidence(
                    manifest,
                    manifest.getManifestDigest(),
                    ARTIFACT,
                    ACTIVE_SIGNER,
                    "1.3.0"));
            SkillPackageManifest deniedCapability = manifest(
                    ACTIVE_SIGNER,
                    Set.of(CAPABILITY, "vehicle.powertrain.control"));
            VerificationResult capabilityDenied = verifier.verify(evidence(
                    deniedCapability,
                    deniedCapability.getManifestDigest(),
                    ARTIFACT,
                    ACTIVE_SIGNER,
                    null));
            boolean failClosed = revokedResult.getFailureCode() == FailureCode.REVOKED_SIGNER
                    && hashMismatch.getFailureCode() == FailureCode.ARTIFACT_DIGEST_MISMATCH
                    && downgrade.getFailureCode() == FailureCode.DOWNGRADE_DENIED
                    && capabilityDenied.getFailureCode() == FailureCode.CAPABILITY_DENIED
                    && revokedResult.getVerifiedPackage() == null
                    && !revokedResult.isDynamicLoadAllowed()
                    && !revokedResult.isExecutionAllowed();
            boolean android13Arm64 = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            boolean complete = artifactHash
                    && manifestDigest
                    && signer
                    && runtimeVersion
                    && capability
                    && failClosed
                    && !verifier.isTrustedEvidenceSourceConfigured()
                    && !verifier.isPackageSignatureCryptographicallyVerified()
                    && !verifier.isDynamicLoadingEnabled()
                    && !verifier.isExecutionEnabled()
                    && !verifier.isProductionWired()
                    && android13Arm64;

            Log.i(TAG, "nonce=" + nonce
                    + " skill_package_verifier_probe_complete=" + complete
                    + " skill_artifact_hash_verified=" + artifactHash
                    + " skill_manifest_digest_verified=" + manifestDigest
                    + " skill_signer_policy_verified=" + signer
                    + " skill_runtime_version_verified=" + runtimeVersion
                    + " skill_capability_policy_verified=" + capability
                    + " skill_revocation_downgrade_fail_closed=" + failClosed
                    + " skill_package_verifier_android13_arm64_verified=" + android13Arm64
                    + " trusted_skill_evidence_source_configured=false"
                    + " package_signature_cryptographically_verified=false"
                    + " dynamic_skill_loading_enabled=false"
                    + " skill_execution_enabled=false"
                    + " skill_package_verifier_runtime_wired=false"
                    + " effect_dispatch_enabled=false"
                    + " vehicle_readback_accessed=false"
                    + " model_invoked=false"
                    + " npu_accessed=false"
                    + " network_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " skill_package_verifier_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " dynamic_skill_loading_enabled=false"
                    + " skill_execution_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static SkillPackageManifest manifest(
            String signerDigest, Set<String> capabilities) {
        return new SkillPackageManifest(
                SkillArtifactVerifier.MANIFEST_SCHEMA_VERSION,
                SKILL,
                "1.2.0",
                ARTIFACT,
                signerDigest,
                "1.0.0",
                "2.0.0",
                capabilities);
    }

    private static VerificationEvidence evidence(
            SkillPackageManifest manifest,
            String declaredManifestDigest,
            String measuredArtifactDigest,
            String observedSignerDigest,
            String highestAcceptedVersion) {
        return new VerificationEvidence(
                manifest,
                declaredManifestDigest,
                measuredArtifactDigest,
                observedSignerDigest,
                12L,
                highestAcceptedVersion);
    }
}

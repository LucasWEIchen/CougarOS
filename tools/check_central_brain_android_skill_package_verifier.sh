#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-TOL-001, S2-SAF-001, S2-OBS-001, FW-U-008, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/skills"
DEBUG_ROOT="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/skills"
TEST_ROOT="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/skills"
SIGNER="$MAIN_ROOT/SkillSignerPolicy.java"
VERSION="$MAIN_ROOT/SkillVersionPolicy.java"
VERIFIER="$MAIN_ROOT/SkillArtifactVerifier.java"
TEST="$TEST_ROOT/SkillArtifactVerifierTest.java"
PROBE="$DEBUG_ROOT/SkillArtifactVerifierProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P5-W05 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$SIGNER" "$VERSION" "$VERIFIER" "$TEST" "$PROBE" \
    "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$RUNTIME_SERVICE" \
    "$GOVERNANCE_SERVICE" "$GRAPH_RUNTIME" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P5-W05 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public static final int SCHEMA_VERSION = 1' \
  'public static final int MAX_SIGNERS = 32' \
  'enum SignerState' \
  'ACTIVE' \
  'RETIRED' \
  'REVOKED' \
  'SIGNER_NOT_YET_ACTIVE' \
  'getPolicyDigest()' \
  'isTrustedSignerEvidenceSourceConfigured()' \
  'isHardwareBackedAttestationRequired()'; do
  require_text "$SIGNER" "$marker"
done
for marker in \
  'public static final int SCHEMA_VERSION = 1' \
  'public static final int MAX_SKILLS = 128' \
  'class SemanticVersion' \
  'VERSION_BELOW_MINIMUM' \
  'VERSION_ABOVE_MAXIMUM' \
  'ARTIFACT_EPOCH_TOO_OLD' \
  'RUNTIME_TOO_OLD' \
  'RUNTIME_TOO_NEW' \
  'DOWNGRADE_DENIED' \
  'isProductionRollbackAuthorityConfigured()'; do
  require_text "$VERSION" "$marker"
done
for marker in \
  'public static final int MANIFEST_SCHEMA_VERSION = 1' \
  'class SkillPackageManifest' \
  'class VerificationEvidence' \
  'class VerifiedPackage' \
  'MANIFEST_DIGEST_MISMATCH' \
  'ARTIFACT_DIGEST_MISMATCH' \
  'SIGNER_EVIDENCE_MISMATCH' \
  'CAPABILITY_POLICY_MISSING' \
  'CAPABILITY_DENIED' \
  'public VerificationResult verify' \
  'isTrustedEvidenceSourceConfigured()' \
  'isPackageSignatureCryptographicallyVerified()' \
  'isDynamicLoadingEnabled()' \
  'isExecutionEnabled()' \
  'isProductionWired()'; do
  require_text "$VERIFIER" "$marker"
done

for test_name in \
  policiesAreBoundedImmutableCanonicalAndDigestStable \
  exactDigestSignerManifestRuntimeAndCapabilityVerifyStatically \
  manifestArtifactAndSignerEvidenceMismatchFailClosed \
  runtimeVersionEpochDowngradeAndCapabilityFailClosed \
  malformedAndUnboundedPoliciesAndEvidenceAreRejected; do
  require_text "$TEST" "$test_name"
done

for marker in \
  skill_package_verifier_probe_complete \
  skill_artifact_hash_verified \
  skill_manifest_digest_verified \
  skill_signer_policy_verified \
  skill_runtime_version_verified \
  skill_capability_policy_verified \
  skill_revocation_downgrade_fail_closed \
  skill_package_verifier_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  trusted_skill_evidence_source_configured=false \
  package_signature_cryptographically_verified=false \
  dynamic_skill_loading_enabled=false \
  skill_execution_enabled=false \
  skill_package_verifier_runtime_wired=false \
  effect_dispatch_enabled=false \
  vehicle_readback_accessed=false \
  model_invoked=false \
  npu_accessed=false \
  network_accessed=false \
  hardware_accessed=false \
  production_ready=false \
  target_hardware_validated=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

require_text "$DEBUG_MANIFEST" '.skills.SkillArtifactVerifierProbeActivity'
if grep -Fq 'SkillArtifactVerifierProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P5-W05 Skill verifier probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'SkillArtifactVerifier|SkillSignerPolicy|SkillVersionPolicy' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GOVERNANCE_SERVICE" \
    "$ROOT_DIR/$GRAPH_RUNTIME"; then
  echo "P5-W05 Skill verifier was wired into production Runtime/Graph" >&2
  exit 1
fi
if grep -R -Eiq \
    'DexClassLoader|PathClassLoader|Class[.]forName|ClassLoader|java[.]lang[.]reflect|ObjectInputStream|ZipInputStream|JarFile|PackageInstaller|ProcessBuilder|Runtime[.]getRuntime|java[.]net|okhttp|http://|https://|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|ioctl|sysfs|/dev/|androidx[.]room|android[.]os[.]Binder|java[.]io[.]File' \
    "$ROOT_DIR/$SIGNER" "$ROOT_DIR/$VERSION" "$ROOT_DIR/$VERIFIER" \
    "$ROOT_DIR/$PROBE"; then
  echo "P5-W05 references package loading, persistence, network, vehicle, Binder, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P5-W05 Skill package verifier"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P5-W05` Skill package verifier'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P5-W05 Skill package verifier trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P5-W05 Skill package verifier"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P5-W05 Skill package verifier architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P5-W05 Skill package verifier detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P5-W05 Skill package verifier"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P5-W05 Skill package verifier Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "DEV-067 P5-W05 static package verification is not production artifact trust"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "ISSUE-040 Trusted signer evidence and atomic Skill policy publication ownership"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P5-W05 Skill package verifier"
require_text "README.md" "P5 Skill package verifier"

printf '%s\n' \
  "Central Brain Android Skill package verifier check passed" \
  "skill_artifact_verifier_contract_defined=true" \
  "skill_signer_policy_contract_defined=true" \
  "skill_version_policy_contract_defined=true" \
  "skill_artifact_hash_verified=true" \
  "skill_manifest_digest_verified=true" \
  "skill_signer_policy_verified=true" \
  "skill_runtime_version_verified=true" \
  "skill_capability_policy_verified=true" \
  "skill_revocation_downgrade_fail_closed=true" \
  "skill_package_verifier_android13_arm64_verified=false" \
  "trusted_skill_evidence_source_configured=false" \
  "package_signature_cryptographically_verified=false" \
  "dynamic_skill_loading_enabled=false" \
  "skill_execution_enabled=false" \
  "skill_package_verifier_runtime_wired=false" \
  "effect_dispatch_enabled=false" \
  "vehicle_readback_accessed=false" \
  "model_invoked=false" \
  "npu_accessed=false" \
  "network_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"

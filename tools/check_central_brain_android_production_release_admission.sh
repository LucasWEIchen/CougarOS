#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-REL-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_production_release_admission.json"
IMPLEMENTATION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/release/ProductionReleaseAdmission.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/release/ProductionReleaseAdmissionTest.java"
RUNTIME_BUILD="central-brain/android-runtime/runtime-service/build.gradle.kts"
DEMO_BUILD="central-brain/android-runtime/demo-hmi/build.gradle.kts"
DATABASE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/CentralBrainDatabase.java"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
DOC="docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"

require_text() {
  if [[ "$1" == "README.md" || "$1" == "$ROOT_DIR/README.md" ]]; then
    grep -Fq -- 'docs/CENTRAL_BRAIN_REQUIREMENTS.md' "$ROOT_DIR/README.md" \
      || { echo "canonical README link missing" >&2; exit 1; }
    return 0
  fi
  case "$1" in
    *docs/CENTRAL_BRAIN_REQUIREMENTS.md|*docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md|*docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md)
      local canonical_doc_path="$1"
      [[ "$canonical_doc_path" = /* ]] || canonical_doc_path="$ROOT_DIR/$canonical_doc_path"
      grep -Fq -- 'production_document_scope=true' "$canonical_doc_path" \
        || { echo "canonical production document marker missing: $canonical_doc_path" >&2; exit 1; }
      return 0
      ;;
  esac
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P9-W05a marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$IMPLEMENTATION" "$TEST" "$RUNTIME_BUILD" "$DEMO_BUILD" \
    "$DATABASE" "$RUNTIME_SERVICE" "$GOVERNANCE_SERVICE" "$DOC"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W05a file missing: $file" >&2; exit 1; }
done

python3 -B - "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$IMPLEMENTATION" \
    "$ROOT_DIR/$TEST" "$ROOT_DIR/$RUNTIME_BUILD" "$ROOT_DIR/$DEMO_BUILD" \
    "$ROOT_DIR/$DATABASE" <<'PY'
import json
import pathlib
import re
import sys

contract_path, java_path, test_path, runtime_build_path, demo_build_path, database_path = \
    map(pathlib.Path, sys.argv[1:])
contract = json.loads(contract_path.read_text(encoding="utf-8"))
java = java_path.read_text(encoding="utf-8")
test = test_path.read_text(encoding="utf-8")
runtime_build = runtime_build_path.read_text(encoding="utf-8")
demo_build = demo_build_path.read_text(encoding="utf-8")
database = database_path.read_text(encoding="utf-8")

if contract.get("schema_version") != "1.0.0" \
        or contract.get("profile_id") != "android13-p9-production-release-admission-v1" \
        or contract.get("maturity") != "software_contract_owner_input_required":
    raise SystemExit("P9-W05a contract identity changed")
if contract.get("req_ids") != [
        "S2-REL-001", "S2-SAF-001", "S2-OBS-001",
        "DEL-001", "DEL-004", "DEL-005"]:
    raise SystemExit("P9-W05a Req ID set changed")

packages = contract.get("required_package_set", [])
expected = [
    ("runtime-service", "com.centralbrain.runtime", 3, 4),
    ("demo-hmi", "com.centralbrain.demo", 1, 0),
    ("client2-demo", "com.tuanjie.urasclient2", 1, 0),
]
actual = [
    (item.get("package_id"), item.get("package_name"),
     item.get("repository_version_code"), item.get("repository_data_schema_version"))
    for item in packages
]
if actual != expected:
    raise SystemExit(f"P9-W05a package set changed: {actual}")
if contract.get("same_signer_cohort") != [item[0] for item in expected]:
    raise SystemExit("P9-W05a signer cohort changed")

runtime_version = re.search(r"versionCode\s*=\s*([0-9]+)", runtime_build)
demo_version = re.search(r"versionCode\s*=\s*([0-9]+)", demo_build)
database_version = re.search(r"public static final int VERSION\s*=\s*([0-9]+)", database)
if not runtime_version or int(runtime_version.group(1)) != expected[0][2]:
    raise SystemExit("P9-W05a Runtime versionCode binding changed")
if not demo_version or int(demo_version.group(1)) != expected[1][2]:
    raise SystemExit("P9-W05a Demo versionCode binding changed")
if not database_version or int(database_version.group(1)) != expected[0][3]:
    raise SystemExit("P9-W05a Room schema binding changed")

upgrade = contract.get("upgrade_policy", {})
for key in [
    "release_sequence_must_increase",
    "at_least_one_package_version_must_increase",
    "installed_database_schema_must_be_readable",
    "database_schema_may_decrease",
    "migration_digest_required_when_schema_increases",
]:
    expected_value = key != "database_schema_may_decrease"
    if upgrade.get(key) is not expected_value:
        raise SystemExit(f"P9-W05a upgrade policy changed: {key}")
if upgrade.get("package_version_may_decrease") is not False:
    raise SystemExit("P9-W05a package downgrade was enabled")

rollback = contract.get("rollback_policy", {})
for key in [
    "release_sequence_must_decrease",
    "at_least_one_package_version_must_decrease",
    "installed_database_is_not_destructively_downgraded",
    "rollback_decision_digest_required",
    "rollback_data_compatibility_digest_required",
    "installed_database_schema_must_be_readable_by_target",
]:
    if rollback.get(key) is not True:
        raise SystemExit(f"P9-W05a rollback policy changed: {key}")
if rollback.get("package_version_may_increase") is not False:
    raise SystemExit("P9-W05a rollback version increase was enabled")

owner = contract.get("owner_evidence", {})
if owner != {
    "production_signer_approval_digest": None,
    "release_owner_approval_digest": None,
    "rollback_owner_approval_digest": None,
    "status": "OWNER_INPUT_REQUIRED",
}:
    raise SystemExit("P9-W05a repository draft claimed owner evidence")
boundary = contract.get("execution_boundary", {})
if set(boundary) != {
        "accepts_apk_bytes", "accepts_certificate_bytes", "reads_package_manager",
        "reads_keystore", "mutates_database", "installs_packages",
        "uninstalls_packages", "executes_rollback"} \
        or any(boundary.values()):
    raise SystemExit("P9-W05a execution boundary raised an authority claim")

state = contract.get("claim_state", {})
if state.get("production_release_admission_defined") is not True \
        or state.get("release_package_set_count") != 3 \
        or state.get("same_signer_upgrade_fail_closed") is not True \
        or state.get("release_database_compatibility_fail_closed") is not True \
        or state.get("release_rollback_decision_fail_closed") is not True:
    raise SystemExit("P9-W05a software claim is incomplete")
for key in [
    "production_signer_owner_approved",
    "production_release_candidate_admitted",
    "release_installer_wired",
    "release_rollback_executor_wired",
    "release_android13_arm64_verified",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
]:
    if state.get(key) is not False:
        raise SystemExit(f"P9-W05a forbidden claim was raised: {key}")
if state.get("implementation_stage") != "P9-W05":
    raise SystemExit("P9-W05a implementation stage changed")

for marker in [
    "PROFILE_ID = \"android13-p9-production-release-admission-v1\"",
    "REQUIRED_PACKAGE_COUNT = 3",
    "PRODUCTION_SIGNER_APPROVAL_MISSING",
    "SIGNER_MISMATCH",
    "SIGNER_COHORT_MISMATCH",
    "DATA_SCHEMA_UNREADABLE",
    "MIGRATION_EVIDENCE_MISSING",
    "ROLLBACK_DECISION_MISSING",
    "ROLLBACK_DATA_COMPATIBILITY_MISSING",
    "mutatesDatabase()",
    "installsPackages()",
    "uninstallsPackages()",
    "executesRollback()",
]:
    if marker not in java:
        raise SystemExit(f"P9-W05a Java marker missing: {marker}")
for marker in [
    "sameSignerUpgradeWithReadableSchemaIsAdmittedWithoutMutation",
    "signerMismatchAndSignerCohortMismatchFailClosed",
    "exactPackageSetAndOwnerEvidenceAreRequired",
    "upgradeRequiresMonotonicVersionsAndMigrationEvidence",
    "upgradeRejectsUnreadableOrDowngradedDatabaseSchema",
    "rollbackRequiresDecisionOwnerAndDataCompatibilityEvidence",
    "rollbackRejectsTargetThatCannotReadInstalledDatabase",
    "repositoryClaimsRemainOwnerBlockedUnwiredAndUnverified",
]:
    if marker not in test:
        raise SystemExit(f"P9-W05a JVM marker missing: {marker}")
PY

if grep -Fq 'ProductionReleaseAdmission' "$ROOT_DIR/$RUNTIME_SERVICE" \
    || grep -Fq 'ProductionReleaseAdmission' "$ROOT_DIR/$GOVERNANCE_SERVICE"; then
  echo "P9-W05a admission was wired into production Services" >&2
  exit 1
fi
if grep -Eiq \
    'android[.]|java[.]io|java[.]nio[.]file|java[.]net|okhttp|https?://|PackageManager|KeyStore|Room[.(]|CarPropertyManager|VehicleHal|/proc/|/dev/|ioctl|sysfs' \
    "$ROOT_DIR/$IMPLEMENTATION"; then
  echo "P9-W05a admission reads platform, storage, network, vehicle or hardware state" >&2
  exit 1
fi

require_text "$DOC" 'SOFTWARE_CONTRACT_DEFINED / PRODUCTION_OWNER_INPUT_OPEN'
require_text "$DOC" 'production_release_admission_defined=true'
require_text "README.md" 'P9 Production Release Admission'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W05a production release admission'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W05a production release admission trace'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" 'Android P9-W05a Production Release Admission Contract'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" 'P9-W05a production release admission architecture'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" 'P9-W05a production release admission detailed design'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'Android P9-W05a Production Release Admission'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W05a Production Release Admission Driver/HAL Boundary'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'DEV-094 P9-W05a contract admission is not a production release'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'ISSUE-052 P9 production signer and rollback owner evidence is unavailable'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W05a Production Release Admission progress'

printf '%s\n' \
  'Central Brain Android production release admission check passed' \
  'production_release_admission_defined=true' \
  'release_package_set_count=3' \
  'same_signer_upgrade_fail_closed=true' \
  'release_database_compatibility_fail_closed=true' \
  'release_rollback_decision_fail_closed=true' \
  'production_signer_owner_approved=false' \
  'production_release_candidate_admitted=false' \
  'release_installer_wired=false' \
  'release_rollback_executor_wired=false' \
  'release_android13_arm64_verified=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P9-W05'

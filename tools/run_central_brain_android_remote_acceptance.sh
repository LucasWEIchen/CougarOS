#!/usr/bin/env bash
set -uo pipefail

# Req IDs: APP-004, XSC-001/004/005/006, NV-F-001/012,
# NV-G-006/007, NV-P-002, DEL-001/003/004/005.

BUNDLE_DIR=""
RELEASE_TAG=""
ARCHIVE_SHA256=""
TARGET_INPUTS=""
DEVICE_ALIAS=""
EVIDENCE_REFERENCE=""
SERIAL="${ANDROID_SERIAL:-}"
OUTPUT_DIR=""
EXECUTE_INSTALL=false
ALLOW_DEBUG_SIGNING=false
INCLUDE_CLIENT2=false
ALLOW_TEMPLATE_TARGET_INPUTS=false

usage() {
  cat <<'EOF'
Usage: run_central_brain_android_remote_acceptance.sh [options]

Required:
  --bundle-dir PATH          Extracted Central Brain hybrid delivery bundle.
  --release-tag TAG          Immutable android13-hwtest-vX.Y.Z-rc.N tag.
  --archive-sha256 SHA256    Verified outer release archive digest.
  --target-inputs PATH       Completed target-input JSON; never copied to evidence.
  --device-alias ALIAS       Non-secret device alias; never use an ADB serial.
  --evidence-reference ID    Non-secret internal raw-evidence record ID.

Options:
  --serial SERIAL            Select one ADB device explicitly.
  --output-dir PATH          New evidence directory; default is under the CWD.
  --execute-install          Install after a successful dry-run; default is read-only.
  --allow-debug-signing      Permit debug-signed APKs on an approved test device.
  --include-client2          Use the Runtime + Demo + Client2 profile.
  --allow-template-target-inputs
                             Local/emulator-only escape hatch; not valid target evidence.
  -h, --help                 Show this help.

The command never uploads evidence. Only github-safe/ may be pasted into an Issue.
EOF
}

die() {
  printf 'remote acceptance error: %s\n' "$1" >&2
  exit 2
}

while (($# > 0)); do
  case "$1" in
    --bundle-dir)
      [[ $# -ge 2 ]] || die "--bundle-dir requires a value"
      BUNDLE_DIR="$2"
      shift 2
      ;;
    --release-tag)
      [[ $# -ge 2 ]] || die "--release-tag requires a value"
      RELEASE_TAG="$2"
      shift 2
      ;;
    --archive-sha256)
      [[ $# -ge 2 ]] || die "--archive-sha256 requires a value"
      ARCHIVE_SHA256="${2,,}"
      shift 2
      ;;
    --target-inputs)
      [[ $# -ge 2 ]] || die "--target-inputs requires a value"
      TARGET_INPUTS="$2"
      shift 2
      ;;
    --device-alias)
      [[ $# -ge 2 ]] || die "--device-alias requires a value"
      DEVICE_ALIAS="$2"
      shift 2
      ;;
    --evidence-reference)
      [[ $# -ge 2 ]] || die "--evidence-reference requires a value"
      EVIDENCE_REFERENCE="$2"
      shift 2
      ;;
    --serial)
      [[ $# -ge 2 ]] || die "--serial requires a value"
      SERIAL="$2"
      shift 2
      ;;
    --output-dir)
      [[ $# -ge 2 ]] || die "--output-dir requires a value"
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --execute-install)
      EXECUTE_INSTALL=true
      shift
      ;;
    --allow-debug-signing)
      ALLOW_DEBUG_SIGNING=true
      shift
      ;;
    --include-client2)
      INCLUDE_CLIENT2=true
      shift
      ;;
    --allow-template-target-inputs)
      ALLOW_TEMPLATE_TARGET_INPUTS=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown option: $1"
      ;;
  esac
done

[[ -n "$BUNDLE_DIR" ]] || die "--bundle-dir is required"
[[ -n "$RELEASE_TAG" ]] || die "--release-tag is required"
[[ -n "$ARCHIVE_SHA256" ]] || die "--archive-sha256 is required"
[[ -n "$TARGET_INPUTS" ]] || die "--target-inputs is required"
[[ -n "$DEVICE_ALIAS" ]] || die "--device-alias is required"
[[ -n "$EVIDENCE_REFERENCE" ]] || die "--evidence-reference is required"
[[ "$RELEASE_TAG" =~ ^android13-hwtest-v[0-9]+\.[0-9]+\.[0-9]+-rc\.[0-9]+$ ]] \
  || die "release tag does not match android13-hwtest-vX.Y.Z-rc.N"
[[ "$ARCHIVE_SHA256" =~ ^[0-9a-f]{64}$ ]] || die "archive SHA-256 must be 64 hex characters"
[[ "$DEVICE_ALIAS" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] \
  || die "device alias must be 1-64 non-secret ASCII identifier characters"
[[ "$EVIDENCE_REFERENCE" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]] \
  || die "evidence reference must be 1-128 non-secret ASCII identifier characters"
[[ -d "$BUNDLE_DIR" ]] || die "bundle directory does not exist"
[[ -f "$TARGET_INPUTS" ]] || die "target-input file does not exist"

BUNDLE_DIR="$(realpath -e "$BUNDLE_DIR")"
TARGET_INPUTS="$(realpath -e "$TARGET_INPUTS")"
MANIFEST="$BUNDLE_DIR/DELIVERY-MANIFEST.json"
DELIVERY_TOOL="$BUNDLE_DIR/tools/central_brain_android_hybrid_delivery.py"
INSTALLER="$BUNDLE_DIR/tools/install_central_brain_android_hybrid_delivery.sh"
[[ -f "$MANIFEST" ]] || die "DELIVERY-MANIFEST.json is missing"
[[ -f "$DELIVERY_TOOL" ]] || die "bundle verifier is missing"
[[ -x "$INSTALLER" ]] || die "bundle installer is missing or not executable"

PYTHON="${PYTHON:-$(command -v python3 || true)}"
if [[ -n "${ADB:-}" ]]; then
  ADB_TOOL="$ADB"
elif [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
  ADB_TOOL="$ANDROID_HOME/platform-tools/adb"
elif [[ -n "${ANDROID_SDK_ROOT:-}" && -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]]; then
  ADB_TOOL="$ANDROID_SDK_ROOT/platform-tools/adb"
else
  ADB_TOOL="$(command -v adb || true)"
fi
[[ -n "$PYTHON" && -x "$PYTHON" ]] || die "python3 is unavailable"
[[ -n "$ADB_TOOL" && -x "$ADB_TOOL" ]] || die "adb is unavailable"
command -v sha256sum >/dev/null || die "sha256sum is unavailable"
command -v tar >/dev/null || die "tar is unavailable"

TARGET_INPUT_STATUS="$($PYTHON -B - "$TARGET_INPUTS" <<'PY'
import json
import pathlib
import re
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if payload.get("schema_version") != "1.0.0":
    raise SystemExit("target input schema must be 1.0.0")
if payload.get("device", {}).get("android_api_expected") != 33:
    raise SystemExit("target inputs must require Android API 33")
for key in (
    "physical_controller_evidence_available",
    "target_system_integration_owner_resolved",
    "production_activation_allowed",
    "target_hardware_validated",
):
    if payload.get("claim_state", {}).get(key) is not False:
        raise SystemExit(f"unsupported positive target claim: {key}")
status = payload.get("status", "unknown")
if not isinstance(status, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}", status):
    raise SystemExit("target input status must be a bounded ASCII identifier")
print(status)
PY
)" || die "target-input validation failed"
if [[ "$TARGET_INPUT_STATUS" == "template" && "$ALLOW_TEMPLATE_TARGET_INPUTS" != true ]]; then
  die "physical remote testing requires a non-template target-input status"
fi

mapfile -t MANIFEST_VALUES < <("$PYTHON" -B - "$MANIFEST" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
print(payload["delivery_id"])
print(payload["source_git_commit"])
PY
)
[[ ${#MANIFEST_VALUES[@]} -eq 2 ]] || die "delivery manifest identity is invalid"
DELIVERY_ID="${MANIFEST_VALUES[0]}"
SOURCE_GIT_COMMIT="${MANIFEST_VALUES[1]}"
[[ "$DELIVERY_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]] \
  || die "manifest delivery ID is invalid"
[[ "$SOURCE_GIT_COMMIT" =~ ^[0-9a-f]{40}$ ]] || die "manifest source commit is invalid"

if [[ -z "$SERIAL" ]]; then
  mapfile -t ONLINE_DEVICES < <("$ADB_TOOL" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  [[ ${#ONLINE_DEVICES[@]} -eq 1 ]] \
    || die "expected exactly one online ADB device; provide --serial"
  SERIAL="${ONLINE_DEVICES[0]}"
fi
[[ "$($ADB_TOOL -s "$SERIAL" get-state 2>/dev/null)" == "device" ]] \
  || die "selected ADB device is not online"

if [[ -z "$OUTPUT_DIR" ]]; then
  OUTPUT_DIR="$PWD/central-brain-remote-evidence-$(date -u +%Y%m%dT%H%M%SZ)-$DEVICE_ALIAS"
fi
OUTPUT_DIR="$(realpath -m "$OUTPUT_DIR")"
[[ ! -e "$OUTPUT_DIR" ]] || die "output path already exists"
umask 077
SAFE_DIR="$OUTPUT_DIR/github-safe"
PRIVATE_DIR="$OUTPUT_DIR/private"
mkdir -p "$SAFE_DIR" "$PRIVATE_DIR"

declare -A STEP_RC=()
run_step() {
  local name="$1"
  shift
  "$@" >"$PRIVATE_DIR/$name.log" 2>&1
  STEP_RC["$name"]=$?
  return 0
}

getprop_value() {
  "$ADB_TOOL" -s "$SERIAL" shell getprop "$1" 2>/dev/null | tr -d '\r\n'
}

ANDROID_API="$(getprop_value ro.build.version.sdk)"
ABI_LIST_64="$(getprop_value ro.product.cpu.abilist64)"
DEVICE_MODEL="$(getprop_value ro.product.model)"
BUILD_FINGERPRINT="$(getprop_value ro.build.fingerprint)"
SAFE_ABI_LIST_64="$(printf '%s' "$ABI_LIST_64" | tr -cd 'A-Za-z0-9,._-')"
[[ "$ANDROID_API" =~ ^[0-9]+$ ]] || ANDROID_API="unknown"
[[ -n "$SAFE_ABI_LIST_64" ]] || SAFE_ABI_LIST_64="unknown"

{
  printf 'adb_serial=%q\n' "$SERIAL"
  printf 'device_model=%q\n' "$DEVICE_MODEL"
  printf 'build_fingerprint=%q\n' "$BUILD_FINGERPRINT"
  printf 'target_input_path=%q\n' "$TARGET_INPUTS"
  printf 'github_upload_allowed=false\n'
} >"$PRIVATE_DIR/device.env"

run_step bundle-verify "$PYTHON" -B "$DELIVERY_TOOL" verify --bundle-dir "$BUNDLE_DIR"

INSTALL_ARGS=(
  --bundle-dir "$BUNDLE_DIR"
  --target-inputs "$TARGET_INPUTS"
  --serial "$SERIAL"
)
if [[ "$INCLUDE_CLIENT2" == true ]]; then
  INSTALL_ARGS+=(--include-client2)
fi
run_step installer-dry-run "$INSTALLER" "${INSTALL_ARGS[@]}"

INSTALL_RC="not-run"
LAUNCH_DEMO_RC="not-run"
LAUNCH_CLIENT2_RC="not-run"
if [[ "$EXECUTE_INSTALL" == true && "${STEP_RC[bundle-verify]}" -eq 0 \
    && "${STEP_RC[installer-dry-run]}" -eq 0 ]]; then
  EXECUTE_ARGS=("${INSTALL_ARGS[@]}" --execute)
  if [[ "$ALLOW_DEBUG_SIGNING" == true ]]; then
    EXECUTE_ARGS+=(--allow-debug-signing)
  fi
  run_step installer-execute "$INSTALLER" "${EXECUTE_ARGS[@]}"
  INSTALL_RC="${STEP_RC[installer-execute]}"
  if [[ "$INSTALL_RC" -eq 0 ]]; then
    run_step launch-demo "$ADB_TOOL" -s "$SERIAL" shell am start -W \
      -n com.centralbrain.demo/.DemoActivity
    LAUNCH_DEMO_RC="${STEP_RC[launch-demo]}"
    if [[ "$INCLUDE_CLIENT2" == true ]]; then
      run_step launch-client2 "$ADB_TOOL" -s "$SERIAL" shell am start -W \
        -n com.tuanjie.urasclient2/.MainActivity
      LAUNCH_CLIENT2_RC="${STEP_RC[launch-client2]}"
    fi
  fi
fi

run_step runtime-package-path "$ADB_TOOL" -s "$SERIAL" shell pm path com.centralbrain.runtime
run_step demo-package-path "$ADB_TOOL" -s "$SERIAL" shell pm path com.centralbrain.demo
if [[ "$INCLUDE_CLIENT2" == true ]]; then
  run_step client2-package-path "$ADB_TOOL" -s "$SERIAL" shell pm path com.tuanjie.urasclient2
fi
run_step runtime-dumpsys "$ADB_TOOL" -s "$SERIAL" shell dumpsys activity service \
  com.centralbrain.runtime/.CentralBrainRuntimeService
run_step diagnostic-dumpsys "$ADB_TOOL" -s "$SERIAL" shell dumpsys activity service \
  com.centralbrain.runtime/.CentralBrainDiagnosticService
run_step crash-log "$ADB_TOOL" -s "$SERIAL" logcat -b crash -d -v threadtime
run_step central-brain-log "$ADB_TOOL" -s "$SERIAL" logcat -d -v threadtime -s \
  CentralBrainNative:I CentralBrainRuntime:I CentralBrainDiagnostic:I \
  CentralBrainGovernance:I CentralBrainGovernanceDemo:I CbClient2Binder:I \
  AndroidRuntime:E '*:S'

BUNDLE_VERIFY_RC="${STEP_RC[bundle-verify]}"
DRY_RUN_RC="${STEP_RC[installer-dry-run]}"
AUTOMATED_GATE_STATE="passed"
OVERALL_RC=0
if [[ "$BUNDLE_VERIFY_RC" -ne 0 || "$DRY_RUN_RC" -ne 0 ]]; then
  AUTOMATED_GATE_STATE="failed"
  OVERALL_RC=1
elif [[ "$EXECUTE_INSTALL" == true && "$INSTALL_RC" -ne 0 ]]; then
  AUTOMATED_GATE_STATE="failed"
  OVERALL_RC=1
elif [[ "$EXECUTE_INSTALL" == true && "$LAUNCH_DEMO_RC" -ne 0 ]]; then
  AUTOMATED_GATE_STATE="failed"
  OVERALL_RC=1
elif [[ "$EXECUTE_INSTALL" == true && "$INCLUDE_CLIENT2" == true \
    && "$LAUNCH_CLIENT2_RC" -ne 0 ]]; then
  AUTOMATED_GATE_STATE="failed"
  OVERALL_RC=1
fi

INSTALL_PROFILE="maintenance"
if [[ "$INCLUDE_CLIENT2" == true ]]; then
  INSTALL_PROFILE="client2-demo"
fi

cat >"$SAFE_DIR/summary.env" <<EOF
contract_id=central-brain.github-remote-hardware-testing.v1
release_tag=$RELEASE_TAG
delivery_id=$DELIVERY_ID
source_git_commit=$SOURCE_GIT_COMMIT
archive_sha256=$ARCHIVE_SHA256
device_alias=$DEVICE_ALIAS
evidence_reference=$EVIDENCE_REFERENCE
raw_or_derived_device_identity_included=false
android_api=$ANDROID_API
supported_64_bit_abis=$SAFE_ABI_LIST_64
target_input_status=$TARGET_INPUT_STATUS
install_profile=$INSTALL_PROFILE
bundle_verify_exit_code=$BUNDLE_VERIFY_RC
dry_run_exit_code=$DRY_RUN_RC
install_requested=$EXECUTE_INSTALL
install_exit_code=$INSTALL_RC
demo_launch_exit_code=$LAUNCH_DEMO_RC
client2_launch_exit_code=$LAUNCH_CLIENT2_RC
automated_gate_state=$AUTOMATED_GATE_STATE
manual_scenario_result=not_collected_by_script
physical_controller_evidence_available=false
production_ready=false
target_hardware_validated=false
automatic_upload_enabled=false
raw_evidence_upload_allowed=false
EOF

cat >"$SAFE_DIR/issue-body.md" <<EOF
## Release identity

- Release tag: \`$RELEASE_TAG\`
- Delivery ID: \`$DELIVERY_ID\`
- Source commit: \`$SOURCE_GIT_COMMIT\`
- Archive SHA-256: \`$ARCHIVE_SHA256\`

## Target summary

- Device alias: \`$DEVICE_ALIAS\`
- Internal evidence reference: \`$EVIDENCE_REFERENCE\`
- Raw or derived device identity included: \`false\`
- Android API: \`$ANDROID_API\`
- 64-bit ABIs: \`$SAFE_ABI_LIST_64\`
- Target-input status: \`$TARGET_INPUT_STATUS\`
- Install profile: \`$INSTALL_PROFILE\`

## Automated gates

- Bundle verify exit code: \`$BUNDLE_VERIFY_RC\`
- Installer dry-run exit code: \`$DRY_RUN_RC\`
- Install requested: \`$EXECUTE_INSTALL\`
- Install exit code: \`$INSTALL_RC\`
- Demo launch exit code: \`$LAUNCH_DEMO_RC\`
- Client2 launch exit code: \`$LAUNCH_CLIENT2_RC\`
- Automated gate state: \`$AUTOMATED_GATE_STATE\`

## Manual result

- Result: \`PASS | FAIL | BLOCKED\`
- Failing scenario IDs: \`<none-or-list>\`
- Expected: \`<expected>\`
- Actual: \`<actual>\`
- First observed UTC: \`<timestamp>\`
- Reproduction count: \`<passed>/<attempted>\`

## Boundaries

- Manual scenarios are not executed by this collector.
- Physical-controller evidence remains unreviewed until the target owner accepts it.
- Production readiness and target hardware validation remain false.
- Raw evidence remains local and is not automatically uploaded.
EOF

cat >"$PRIVATE_DIR/README.txt" <<'EOF'
PRIVATE TARGET EVIDENCE

Do not upload this directory or its archive to GitHub without target security and
privacy owner review. It may contain an ADB serial, raw build fingerprint,
package state, task identifiers, crash details, or other target-only metadata.
The target-input JSON itself is intentionally not copied.
EOF

PRIVATE_ARCHIVE="$OUTPUT_DIR/private-evidence.tar.gz"
tar -C "$OUTPUT_DIR" -czf "$PRIVATE_ARCHIVE" private
PRIVATE_ARCHIVE_SHA256="$(sha256sum "$PRIVATE_ARCHIVE" | awk '{print $1}')"
printf '%s  %s\n' "$PRIVATE_ARCHIVE_SHA256" "$(basename "$PRIVATE_ARCHIVE")" \
  >"$OUTPUT_DIR/private-evidence.tar.gz.sha256"

printf '%s\n' \
  "remote_acceptance_evidence_collected=true" \
  "release_tag=$RELEASE_TAG" \
  "source_git_commit=$SOURCE_GIT_COMMIT" \
  "device_alias=$DEVICE_ALIAS" \
  "automated_gate_state=$AUTOMATED_GATE_STATE" \
  "github_safe_evidence_dir=$SAFE_DIR" \
  "private_evidence_archive=$PRIVATE_ARCHIVE" \
  "private_evidence_archive_sha256=$PRIVATE_ARCHIVE_SHA256" \
  "automatic_upload_enabled=false" \
  "raw_evidence_upload_allowed=false" \
  "physical_controller_evidence_available=false" \
  "production_ready=false" \
  "target_hardware_validated=false"

exit "$OVERALL_RC"

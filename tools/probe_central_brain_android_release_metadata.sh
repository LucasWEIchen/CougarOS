#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-REL-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${ANDROID_SERIAL:-}"

usage() {
  cat <<'EOF'
Usage: probe_central_brain_android_release_metadata.sh [--serial SERIAL]

Runs the already-installed debug release-metadata probe. It never builds,
installs, uninstalls, rolls back, or prints device/package/signer identities.
EOF
}

while (($# > 0)); do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "--serial requires a value" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown option" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  source "$ROOT_DIR/env.sh"
fi

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
[[ -x "$ADB" ]] || { echo "adb is unavailable" >&2; exit 1; }

if [[ -z "$SERIAL" ]]; then
  mapfile -t ONLINE_DEVICES < <(
    "$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ ${#ONLINE_DEVICES[@]} -ne 1 ]]; then
    echo "release metadata probe requires exactly one online adb transport" >&2
    exit 1
  fi
  SERIAL="${ONLINE_DEVICES[0]}"
fi

ADB_DEVICE=("$ADB" -s "$SERIAL")
[[ "$("${ADB_DEVICE[@]}" get-state | tr -d '\r')" == "device" ]] \
  || { echo "selected adb transport is not online" >&2; exit 1; }

SDK="$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${ADB_DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ "$SDK" != "33" || "$ABI" != "arm64-v8a" ]]; then
  echo "release metadata probe requires Android 13 ARM64" >&2
  exit 1
fi

NONCE="$(date +%s%N)"
START_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.release.ProductionReleaseMetadataProbeActivity \
  --es nonce "$NONCE")"
grep -Fq "Status: ok" <<<"$START_OUTPUT" \
  || { echo "release metadata debug probe did not start" >&2; exit 1; }

PASSED=false
PROBE_LOG=""
for _ in {1..40}; do
  PROBE_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbReleaseProbe:I '*:S')"
  if grep -Fq "nonce=$NONCE release_metadata_probe_complete=true" <<<"$PROBE_LOG" \
      && grep -Fq "release_metadata_projection_verified=true" <<<"$PROBE_LOG" \
      && grep -Fq "release_package_query_count=3" <<<"$PROBE_LOG" \
      && grep -Eq "release_installed_package_count=[0-3]" <<<"$PROBE_LOG" \
      && grep -Eq "release_repository_version_match_count=[0-3]" <<<"$PROBE_LOG" \
      && grep -Fq "release_same_signer_pair_query_count=2" <<<"$PROBE_LOG" \
      && grep -Eq "release_same_signer_pair_match_count=[0-2]" <<<"$PROBE_LOG" \
      && grep -Fq "release_candidate_metadata_complete=false" <<<"$PROBE_LOG" \
      && grep -Fq "release_dry_run_admitted=false" <<<"$PROBE_LOG" \
      && grep -Fq "production_signer_owner_approved=false" <<<"$PROBE_LOG" \
      && grep -Fq "production_release_candidate_admitted=false" <<<"$PROBE_LOG" \
      && grep -Fq "release_installer_wired=false" <<<"$PROBE_LOG" \
      && grep -Fq "release_install_executed=false" <<<"$PROBE_LOG" \
      && grep -Fq "release_uninstall_executed=false" <<<"$PROBE_LOG" \
      && grep -Fq "release_rollback_executor_wired=false" <<<"$PROBE_LOG" \
      && grep -Fq "release_rollback_executed=false" <<<"$PROBE_LOG" \
      && grep -Fq "release_signer_material_logged=false" <<<"$PROBE_LOG" \
      && grep -Fq "release_certificate_material_logged=false" <<<"$PROBE_LOG" \
      && grep -Fq "release_package_name_logged=false" <<<"$PROBE_LOG" \
      && grep -Fq "release_device_identity_logged=false" <<<"$PROBE_LOG" \
      && grep -Fq "release_raw_log_persisted=false" <<<"$PROBE_LOG" \
      && grep -Fq "release_android_debug_probe_available=true" <<<"$PROBE_LOG" \
      && grep -Fq "release_android_debug_probe_executed=true" <<<"$PROBE_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$PROBE_LOG" \
      && grep -Fq "production_ready=false" <<<"$PROBE_LOG" \
      && grep -Fq "target_hardware_validated=false" <<<"$PROBE_LOG"; then
    PASSED=true
    break
  fi
  sleep 0.25
done

if [[ "$PASSED" != true ]]; then
  echo "release metadata probe returned no conforming redacted evidence" >&2
  exit 1
fi

INSTALLED_COUNT="$(grep -Eo 'release_installed_package_count=[0-3]' \
  <<<"$PROBE_LOG" | tail -n 1 | cut -d= -f2)"
VERSION_COUNT="$(grep -Eo 'release_repository_version_match_count=[0-3]' \
  <<<"$PROBE_LOG" | tail -n 1 | cut -d= -f2)"
SIGNER_COUNT="$(grep -Eo 'release_same_signer_pair_match_count=[0-2]' \
  <<<"$PROBE_LOG" | tail -n 1 | cut -d= -f2)"

printf '%s\n' \
  'release_metadata_android_probe_passed=true' \
  'release_package_query_count=3' \
  "release_installed_package_count=$INSTALLED_COUNT" \
  "release_repository_version_match_count=$VERSION_COUNT" \
  'release_same_signer_pair_query_count=2' \
  "release_same_signer_pair_match_count=$SIGNER_COUNT" \
  'release_candidate_metadata_complete=false' \
  'release_dry_run_admitted=false' \
  'production_signer_owner_approved=false' \
  'production_release_candidate_admitted=false' \
  'release_installer_wired=false' \
  'release_install_executed=false' \
  'release_uninstall_executed=false' \
  'release_rollback_executor_wired=false' \
  'release_rollback_executed=false' \
  'release_android13_arm64_verified=true' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'

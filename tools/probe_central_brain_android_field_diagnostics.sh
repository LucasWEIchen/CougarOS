#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-OBS-001, S2-REL-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${ANDROID_SERIAL:-}"

usage() {
  cat <<'EOF'
Usage: probe_central_brain_android_field_diagnostics.sh [--serial SERIAL]

Runs bounded diagnostics against an already-installed debug release. It never
builds, installs, uninstalls, rolls back, uploads, or prints target identities,
package names, signer material, raw logs, or user/model/vehicle payloads.
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
    echo "field diagnostics requires exactly one online adb transport" >&2
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
  echo "field diagnostics requires Android 13 ARM64" >&2
  exit 1
fi

NONCE="$(date +%s%N)"
START_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.release.FieldDiagnosticsProbeActivity \
  --es nonce "$NONCE")"
grep -Fq "Status: ok" <<<"$START_OUTPUT" \
  || { echo "field diagnostics debug probe did not start" >&2; exit 1; }

PROBE_LINE=""
for _ in {1..40}; do
  PROBE_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbFieldDiag:I '*:S')"
  PROBE_LINE="$(grep -F "nonce=$NONCE field_diagnostics_probe_complete=true" \
    <<<"$PROBE_LOG" | tail -n 1 || true)"
  if [[ -n "$PROBE_LINE" ]] \
      && grep -Fq "field_diagnostics_projection_verified=true" <<<"$PROBE_LINE" \
      && grep -Fq "field_diagnostics_package_query_count=3" <<<"$PROBE_LINE" \
      && grep -Eq "field_diagnostics_installed_package_count=[0-3]" <<<"$PROBE_LINE" \
      && grep -Eq "field_diagnostics_repository_version_match_count=[0-3]" \
        <<<"$PROBE_LINE" \
      && grep -Fq "field_diagnostics_signer_pair_query_count=2" <<<"$PROBE_LINE" \
      && grep -Eq "field_diagnostics_signer_pair_match_count=[0-2]" <<<"$PROBE_LINE" \
      && grep -Fq "field_diagnostics_launch_target_query_count=2" <<<"$PROBE_LINE" \
      && grep -Eq "field_diagnostics_launchable_target_count=[0-2]" <<<"$PROBE_LINE" \
      && grep -Fq "field_diagnostics_service_query_count=2" <<<"$PROBE_LINE" \
      && grep -Eq "field_diagnostics_declared_service_count=[0-2]" <<<"$PROBE_LINE" \
      && grep -Fq "field_diagnostics_external_activity_started=false" <<<"$PROBE_LINE" \
      && grep -Fq "field_diagnostics_service_invoked=false" <<<"$PROBE_LINE" \
      && grep -Fq "field_diagnostics_raw_log_persisted=false" <<<"$PROBE_LINE" \
      && grep -Fq "field_diagnostics_device_identity_logged=false" <<<"$PROBE_LINE" \
      && grep -Fq "field_diagnostics_signer_material_logged=false" <<<"$PROBE_LINE" \
      && grep -Fq "field_diagnostics_target_input_logged=false" <<<"$PROBE_LINE" \
      && grep -Fq "field_diagnostics_automatic_upload_enabled=false" <<<"$PROBE_LINE" \
      && grep -Fq "field_diagnostics_android_debug_probe_executed=true" \
        <<<"$PROBE_LINE" \
      && grep -Fq "hardware_accessed=false" <<<"$PROBE_LINE" \
      && grep -Fq "production_ready=false" <<<"$PROBE_LINE" \
      && grep -Fq "target_hardware_validated=false" <<<"$PROBE_LINE"; then
    break
  fi
  PROBE_LINE=""
  sleep 0.25
done
[[ -n "$PROBE_LINE" ]] \
  || { echo "field diagnostics returned no conforming redacted preflight" >&2; exit 1; }

status_from_start() {
  local component="$1"
  local output status
  set +e
  output="$("${ADB_DEVICE[@]}" shell am start -W -n "$component" 2>&1)"
  status=$?
  set -e
  if [[ $status -eq 0 ]] && grep -Fq "Status: ok" <<<"$output"; then
    printf '%s' "PASS"
  else
    printf '%s' "FAIL"
  fi
}

DEMO_STATUS="$(status_from_start com.centralbrain.demo/.DemoActivity)"
CLIENT2_STATUS="$(status_from_start com.tuanjie.urasclient2/.MainActivity)"
RUNTIME_STATUS="$(status_from_start com.centralbrain.runtime/.RuntimeProbeActivity)"

DIAGNOSTIC_NONCE="$(date +%s%N)"
DIAGNOSTIC_START="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.DiagnosticProbeActivity \
  --es nonce "$DIAGNOSTIC_NONCE" 2>&1 || true)"
DIAGNOSTIC_STATUS="FAIL"
if grep -Fq "Status: ok" <<<"$DIAGNOSTIC_START"; then
  for _ in {1..40}; do
    DIAGNOSTIC_LOG="$("${ADB_DEVICE[@]}" logcat -d \
      -s CentralBrainDiagProbe:I '*:S')"
    if grep -Fq "nonce=$DIAGNOSTIC_NONCE diagnostic_probe_passed=true" \
        <<<"$DIAGNOSTIC_LOG"; then
      DIAGNOSTIC_STATUS="PASS"
      break
    fi
    sleep 0.25
  done
fi

if grep -Fq "field_diagnostics_release_bundle_preflight_passed=true" \
    <<<"$PROBE_LINE"; then
  RELEASE_STATUS="PASS"
else
  RELEASE_STATUS="FAIL"
fi

result_code() {
  [[ "$1" == "PASS" ]] && printf '%s' "0" || printf '%s' "1"
}

digest_of() {
  printf '%s' "$1" | sha256sum | cut -d' ' -f1
}

INSTALLED_COUNT="$(grep -Eo 'field_diagnostics_installed_package_count=[0-3]' \
  <<<"$PROBE_LINE" | tail -n 1 | cut -d= -f2)"
VERSION_COUNT="$(grep -Eo 'field_diagnostics_repository_version_match_count=[0-3]' \
  <<<"$PROBE_LINE" | tail -n 1 | cut -d= -f2)"
SIGNER_COUNT="$(grep -Eo 'field_diagnostics_signer_pair_match_count=[0-2]' \
  <<<"$PROBE_LINE" | tail -n 1 | cut -d= -f2)"

RELEASE_DIGEST="$(digest_of \
  "android13-p9-field-diagnostics-probe-v1|release.bundle|$RELEASE_STATUS|$INSTALLED_COUNT|$VERSION_COUNT|$SIGNER_COUNT")"
DEMO_DIGEST="$(digest_of \
  "android13-p9-field-diagnostics-probe-v1|demo.launch|$DEMO_STATUS")"
CLIENT2_DIGEST="$(digest_of \
  "android13-p9-field-diagnostics-probe-v1|client2.launch|$CLIENT2_STATUS")"
RUNTIME_DIGEST="$(digest_of \
  "android13-p9-field-diagnostics-probe-v1|runtime.service|$RUNTIME_STATUS")"
DIAGNOSTIC_DIGEST="$(digest_of \
  "android13-p9-field-diagnostics-probe-v1|diagnostics.service|$DIAGNOSTIC_STATUS")"

FAILED_COUNT=0
for status in "$RELEASE_STATUS" "$DEMO_STATUS" "$CLIENT2_STATUS" \
    "$RUNTIME_STATUS" "$DIAGNOSTIC_STATUS"; do
  [[ "$status" == "PASS" ]] || FAILED_COUNT=$((FAILED_COUNT + 1))
done

printf '%s\n' \
  'field_diagnostics_adapter_complete=true' \
  'field_diagnostics_category_count=8' \
  'field_diagnostics_executed_category_count=5' \
  'field_diagnostics_not_run_category_count=3' \
  "field_diagnostics_failed_category_count=$FAILED_COUNT" \
  'field_diagnostics_1_category=release.bundle' \
  "field_diagnostics_1_status=$RELEASE_STATUS" \
  "field_diagnostics_1_result_code=$(result_code "$RELEASE_STATUS")" \
  "field_diagnostics_1_detail_digest=$RELEASE_DIGEST" \
  'field_diagnostics_2_category=installer.dry_run' \
  'field_diagnostics_2_status=NOT_RUN' \
  'field_diagnostics_2_result_code=-1' \
  'field_diagnostics_3_category=installer.execute' \
  'field_diagnostics_3_status=NOT_RUN' \
  'field_diagnostics_3_result_code=-1' \
  'field_diagnostics_4_category=demo.launch' \
  "field_diagnostics_4_status=$DEMO_STATUS" \
  "field_diagnostics_4_result_code=$(result_code "$DEMO_STATUS")" \
  "field_diagnostics_4_detail_digest=$DEMO_DIGEST" \
  'field_diagnostics_5_category=client2.launch' \
  "field_diagnostics_5_status=$CLIENT2_STATUS" \
  "field_diagnostics_5_result_code=$(result_code "$CLIENT2_STATUS")" \
  "field_diagnostics_5_detail_digest=$CLIENT2_DIGEST" \
  'field_diagnostics_6_category=runtime.service' \
  "field_diagnostics_6_status=$RUNTIME_STATUS" \
  "field_diagnostics_6_result_code=$(result_code "$RUNTIME_STATUS")" \
  "field_diagnostics_6_detail_digest=$RUNTIME_DIGEST" \
  'field_diagnostics_7_category=diagnostics.service' \
  "field_diagnostics_7_status=$DIAGNOSTIC_STATUS" \
  "field_diagnostics_7_result_code=$(result_code "$DIAGNOSTIC_STATUS")" \
  "field_diagnostics_7_detail_digest=$DIAGNOSTIC_DIGEST" \
  'field_diagnostics_8_category=manual.scenario_matrix' \
  'field_diagnostics_8_status=NOT_RUN' \
  'field_diagnostics_8_result_code=-1' \
  'field_diagnostics_android_contract_probe_passed=true' \
  'field_diagnostics_target_category_execution_complete=false' \
  'release_evidence_target_report_admitted=false' \
  'release_evidence_runtime_diagnostics_wired=false' \
  'release_evidence_retest_workflow_wired=false' \
  'field_diagnostics_automatic_upload_enabled=false' \
  'field_diagnostics_android13_arm64_verified=true' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'

[[ "$FAILED_COUNT" -eq 0 ]] || exit 1

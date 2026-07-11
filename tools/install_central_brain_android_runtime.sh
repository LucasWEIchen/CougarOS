#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-004, XSC-005, XSC-006, NV-F-001, NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/central-brain/android-runtime"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REQUIRE_API_33=false

usage() {
  cat <<'EOF'
Usage: install_central_brain_android_runtime.sh [options]

Options:
  --serial SERIAL    Select an adb device explicitly.
  --skip-build       Reuse existing debug artifacts.
  --require-api-33   Fail unless the selected device is exactly Android API 33.
  -h, --help         Show this help.
EOF
}

while (($# > 0)); do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "--serial requires a value" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --skip-build)
      BUILD=false
      shift
      ;;
    --require-api-33)
      REQUIRE_API_33=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # Local workspace toolchain; target integrators may provide these variables.
  source "$ROOT_DIR/env.sh"
fi

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"

ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
if [[ ! -x "$ADB" ]]; then
  echo "adb not executable: $ADB" >&2
  exit 1
fi

if [[ "$BUILD" == true ]]; then
  "$ROOT_DIR/tools/build_central_brain_android_runtime.sh"
fi

RUNTIME_APK="$RUNTIME_DIR/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
DEMO_APK="$RUNTIME_DIR/demo-hmi/build/outputs/apk/debug/demo-hmi-debug.apk"
for artifact in "$RUNTIME_APK" "$DEMO_APK"; do
  if [[ ! -f "$artifact" ]]; then
    echo "missing Android runtime artifact: $artifact" >&2
    exit 1
  fi
done

if [[ -z "$SERIAL" ]]; then
  mapfile -t ONLINE_DEVICES < <("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ ${#ONLINE_DEVICES[@]} -ne 1 ]]; then
    echo "expected exactly one online adb device; use --serial when multiple exist" >&2
    "$ADB" devices -l >&2
    exit 1
  fi
  SERIAL="${ONLINE_DEVICES[0]}"
fi

ADB_DEVICE=("$ADB" -s "$SERIAL")
if [[ "$("${ADB_DEVICE[@]}" get-state)" != "device" ]]; then
  echo "adb device is not online: $SERIAL" >&2
  exit 1
fi

SDK="$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${ADB_DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
MODEL="$("${ADB_DEVICE[@]}" shell getprop ro.product.model | tr -d '\r')"
if [[ ! "$SDK" =~ ^[0-9]+$ ]] || ((SDK < 33)); then
  echo "Android API 33 or newer is required; device reported '$SDK'" >&2
  exit 1
fi
if [[ "$REQUIRE_API_33" == true && "$SDK" != "33" ]]; then
  echo "R1 API 33 exit evidence requested, but device reported API $SDK" >&2
  exit 1
fi

"${ADB_DEVICE[@]}" install -r "$RUNTIME_APK"
"${ADB_DEVICE[@]}" install -r "$DEMO_APK"
"${ADB_DEVICE[@]}" shell am force-stop com.centralbrain.runtime
"${ADB_DEVICE[@]}" shell am force-stop com.centralbrain.demo
"${ADB_DEVICE[@]}" logcat -c

DEMO_PACKAGE_DUMP="$("${ADB_DEVICE[@]}" shell dumpsys package com.centralbrain.demo)"
if ! grep -Fq "com.centralbrain.permission.BIND_RUNTIME: granted=true" <<<"$DEMO_PACKAGE_DUMP"; then
  echo "Demo HMI does not hold the signature BIND_RUNTIME permission" >&2
  exit 1
fi
if ! grep -Fq "com.centralbrain.permission.BIND_GOVERNANCE: granted=true" \
    <<<"$DEMO_PACKAGE_DUMP"; then
  echo "Demo HMI does not hold the signature BIND_GOVERNANCE permission" >&2
  exit 1
fi
if grep -Fq "com.centralbrain.permission.ACCESS_DIAGNOSTICS" <<<"$DEMO_PACKAGE_DUMP"; then
  echo "Demo HMI must not request the diagnostic permission" >&2
  exit 1
fi

set +e
UNAUTHORIZED_RUNTIME_OUTPUT="$("${ADB_DEVICE[@]}" shell am startservice \
  -n com.centralbrain.runtime/.CentralBrainRuntimeService 2>&1)"
UNAUTHORIZED_RUNTIME_STATUS=$?
UNAUTHORIZED_DIAGNOSTIC_OUTPUT="$("${ADB_DEVICE[@]}" shell am startservice \
  -n com.centralbrain.runtime/.CentralBrainDiagnosticService 2>&1)"
UNAUTHORIZED_DIAGNOSTIC_STATUS=$?
UNAUTHORIZED_GOVERNANCE_OUTPUT="$("${ADB_DEVICE[@]}" shell am startservice \
  -n com.centralbrain.runtime/.CentralBrainGovernanceService 2>&1)"
UNAUTHORIZED_GOVERNANCE_STATUS=$?
set -e
if [[ $UNAUTHORIZED_RUNTIME_STATUS -eq 0 ]] \
    || ! grep -Fq "Requires permission com.centralbrain.permission.BIND_RUNTIME" \
      <<<"$UNAUTHORIZED_RUNTIME_OUTPUT"; then
  echo "shell caller was not rejected by the production signature permission" >&2
  exit 1
fi
if [[ $UNAUTHORIZED_DIAGNOSTIC_STATUS -eq 0 ]] \
    || ! grep -Fq "Requires permission com.centralbrain.permission.ACCESS_DIAGNOSTICS" \
      <<<"$UNAUTHORIZED_DIAGNOSTIC_OUTPUT"; then
  echo "shell caller was not rejected by the diagnostic signature permission" >&2
  exit 1
fi
if [[ $UNAUTHORIZED_GOVERNANCE_STATUS -eq 0 ]] \
    || ! grep -Fq "Requires permission com.centralbrain.permission.BIND_GOVERNANCE" \
      <<<"$UNAUTHORIZED_GOVERNANCE_OUTPUT"; then
  echo "shell caller was not rejected by the Governance signature permission" >&2
  exit 1
fi

DIAGNOSTIC_NONCE="$(date +%s%N)"
DIAGNOSTIC_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.DiagnosticProbeActivity \
  --es nonce "$DIAGNOSTIC_NONCE")"
if ! grep -Fq "Status: ok" <<<"$DIAGNOSTIC_PROBE_OUTPUT"; then
  echo "$DIAGNOSTIC_PROBE_OUTPUT" >&2
  echo "diagnostic debug probe did not start successfully" >&2
  exit 1
fi
DIAGNOSTIC_PROBE_PASSED=false
for _ in {1..20}; do
  DIAGNOSTIC_LOG="$("${ADB_DEVICE[@]}" logcat -d \
    -s CentralBrainDiagProbe:I '*:S' | tail -n 20)"
  if grep -Fq "nonce=$DIAGNOSTIC_NONCE diagnostic_probe_passed=true" \
      <<<"$DIAGNOSTIC_LOG" \
      && grep -Fq "effect_delivery_activation_diagnostic_verified=true" \
        <<<"$DIAGNOSTIC_LOG"; then
    DIAGNOSTIC_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$DIAGNOSTIC_PROBE_PASSED" != true ]]; then
  echo "diagnostic Binder page probe did not pass" >&2
  exit 1
fi

MIGRATION_NONCE="$(date +%s%N)"
MIGRATION_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.persistence.MigrationProbeActivity \
  --es nonce "$MIGRATION_NONCE")"
if ! grep -Fq "Status: ok" <<<"$MIGRATION_PROBE_OUTPUT"; then
  echo "$MIGRATION_PROBE_OUTPUT" >&2
  echo "Room migration debug probe did not start successfully" >&2
  exit 1
fi
MIGRATION_PROBE_PASSED=false
for _ in {1..40}; do
  MIGRATION_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbMigrationProbe:I)"
  if grep -Fq "nonce=$MIGRATION_NONCE migration_probe_complete=true" \
      <<<"$MIGRATION_LOG" \
      && grep -Fq "room_migration_1_2_verified=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "room_schema_version=2" <<<"$MIGRATION_LOG" \
      && grep -Fq "room_table_count=8" <<<"$MIGRATION_LOG" \
      && grep -Fq "room_wal_enabled=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "legacy_task_preserved=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "legacy_approval_preserved=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "durable_dispatch_enabled=false" <<<"$MIGRATION_LOG"; then
    MIGRATION_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$MIGRATION_PROBE_PASSED" != true ]]; then
  echo "$MIGRATION_LOG" >&2
  echo "Room schema/WAL/migration probe did not pass" >&2
  exit 1
fi

REPOSITORY_NONCE="$(date +%s%N)"
REPOSITORY_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.persistence.DurableRepositoryProbeActivity \
  --es nonce "$REPOSITORY_NONCE")"
if ! grep -Fq "Status: ok" <<<"$REPOSITORY_PROBE_OUTPUT"; then
  echo "$REPOSITORY_PROBE_OUTPUT" >&2
  echo "durable repository debug probe did not start successfully" >&2
  exit 1
fi
REPOSITORY_PROBE_PASSED=false
for _ in {1..40}; do
  REPOSITORY_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbRepositoryProbe:I)"
  if grep -Fq "nonce=$REPOSITORY_NONCE repository_probe_complete=true" \
      <<<"$REPOSITORY_LOG" \
      && grep -Fq "task_admission_transaction_verified=true" <<<"$REPOSITORY_LOG" \
      && grep -Fq "task_idempotent_replay_verified=true" <<<"$REPOSITORY_LOG" \
      && grep -Fq "expired_deadline_replay_verified=true" <<<"$REPOSITORY_LOG" \
      && grep -Fq "new_expired_deadline_rejected=true" <<<"$REPOSITORY_LOG" \
      && grep -Fq "task_idempotency_conflict_verified=true" <<<"$REPOSITORY_LOG" \
      && grep -Fq "task_owner_isolation_verified=true" <<<"$REPOSITORY_LOG" \
      && grep -Fq "task_transition_transaction_verified=true" <<<"$REPOSITORY_LOG" \
      && grep -Fq "task_transition_replay_verified=true" <<<"$REPOSITORY_LOG" \
      && grep -Fq "terminal_settlement_transaction_verified=true" \
        <<<"$REPOSITORY_LOG" \
      && grep -Fq "durable_task_count=2" <<<"$REPOSITORY_LOG" \
      && grep -Fq "acceptance_audit_count=2" <<<"$REPOSITORY_LOG" \
      && grep -Fq "durable_audit_count=5" <<<"$REPOSITORY_LOG" \
      && grep -Fq "durable_checkpoint_count=4" <<<"$REPOSITORY_LOG" \
      && grep -Fq "repository_probe_isolated=true" <<<"$REPOSITORY_LOG" \
      && grep -Fq "durable_dispatch_enabled=false" <<<"$REPOSITORY_LOG"; then
    REPOSITORY_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$REPOSITORY_PROBE_PASSED" != true ]]; then
  echo "$REPOSITORY_LOG" >&2
  echo "durable task repository probe did not pass" >&2
  exit 1
fi

APPROVAL_REPOSITORY_NONCE="$(date +%s%N)"
APPROVAL_REPOSITORY_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.persistence.DurableApprovalRepositoryProbeActivity \
  --es nonce "$APPROVAL_REPOSITORY_NONCE")"
if ! grep -Fq "Status: ok" <<<"$APPROVAL_REPOSITORY_OUTPUT"; then
  echo "$APPROVAL_REPOSITORY_OUTPUT" >&2
  echo "durable approval repository debug probe did not start successfully" >&2
  exit 1
fi
APPROVAL_REPOSITORY_PASSED=false
for _ in {1..40}; do
  APPROVAL_REPOSITORY_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbApprovalProbe:I)"
  if grep -Fq "nonce=$APPROVAL_REPOSITORY_NONCE approval_probe_complete=true" \
      <<<"$APPROVAL_REPOSITORY_LOG" \
      && grep -Fq "approval_reopen_replay_verified=true" \
        <<<"$APPROVAL_REPOSITORY_LOG" \
      && grep -Fq "approval_idempotency_conflict_verified=true" \
        <<<"$APPROVAL_REPOSITORY_LOG" \
      && grep -Fq "approval_owner_isolation_verified=true" \
        <<<"$APPROVAL_REPOSITORY_LOG" \
      && grep -Fq "approval_cancel_idempotency_verified=true" \
        <<<"$APPROVAL_REPOSITORY_LOG" \
      && grep -Fq "approval_expiry_verified=true" <<<"$APPROVAL_REPOSITORY_LOG" \
      && grep -Fq "approval_durable=true" <<<"$APPROVAL_REPOSITORY_LOG" \
      && grep -Fq "approval_grant_supported=false" <<<"$APPROVAL_REPOSITORY_LOG" \
      && grep -Fq "service_dispatch_triggered=false" \
        <<<"$APPROVAL_REPOSITORY_LOG"; then
    APPROVAL_REPOSITORY_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$APPROVAL_REPOSITORY_PASSED" != true ]]; then
  echo "$APPROVAL_REPOSITORY_LOG" >&2
  echo "durable approval repository probe did not pass" >&2
  exit 1
fi

RESTART_NONCE="$(date +%s%N)"
RESTART_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.persistence.RestartReconciliationProbeActivity \
  --es nonce "$RESTART_NONCE")"
if ! grep -Fq "Status: ok" <<<"$RESTART_PROBE_OUTPUT"; then
  echo "$RESTART_PROBE_OUTPUT" >&2
  echo "restart reconciliation debug probe did not start successfully" >&2
  exit 1
fi
RESTART_PROBE_PASSED=false
for _ in {1..40}; do
  RESTART_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbRestartProbe:I)"
  if grep -Fq "nonce=$RESTART_NONCE restart_probe_complete=true" \
      <<<"$RESTART_LOG" \
      && grep -Fq "active_task_reconciled_failed=true" <<<"$RESTART_LOG" \
      && grep -Fq "incomplete_completion_reconciled_failed=true" \
        <<<"$RESTART_LOG" \
      && grep -Fq "restart_reconciliation_report_verified=true" \
        <<<"$RESTART_LOG" \
      && grep -Fq "restart_reconciliation_idempotent=true" <<<"$RESTART_LOG" \
      && grep -Fq "restart_reconciliation_audit_count=2" <<<"$RESTART_LOG" \
      && grep -Fq "task_execution_resume_enabled=false" <<<"$RESTART_LOG" \
      && grep -Fq "durable_dispatch_enabled=false" <<<"$RESTART_LOG"; then
    RESTART_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$RESTART_PROBE_PASSED" != true ]]; then
  echo "$RESTART_LOG" >&2
  echo "restart reconciliation probe did not pass" >&2
  exit 1
fi

EFFECT_NONCE="$(date +%s%N)"
EFFECT_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.persistence.DurableEffectRepositoryProbeActivity \
  --es nonce "$EFFECT_NONCE")"
if ! grep -Fq "Status: ok" <<<"$EFFECT_PROBE_OUTPUT"; then
  echo "$EFFECT_PROBE_OUTPUT" >&2
  echo "durable effect/outbox debug probe did not start successfully" >&2
  exit 1
fi
EFFECT_PROBE_PASSED=false
for _ in {1..40}; do
  EFFECT_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbEffectProbe:I)"
  if grep -Fq "nonce=$EFFECT_NONCE effect_probe_complete=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_prepare_transaction_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_non_running_task_rejected=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_route_mismatch_rejected=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_owner_scoped_token_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_reopen_replay_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_idempotency_conflict_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_owner_scope_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "outbox_claim_transaction_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "outbox_reopen_requeue_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "outbox_reconciliation_idempotent=true" <<<"$EFFECT_LOG" \
      && grep -Fq "outbox_fair_requeue_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "outbox_second_claim_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_retry_idempotent_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_retry_delay_conflict_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_retry_digest_conflict_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_retry_not_before_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_final_claim_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_attempt_limit_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_dead_letter_idempotent_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_dead_letter_digest_conflict_verified=true" \
        <<<"$EFFECT_LOG" \
      && grep -Fq "effect_stale_attempt_rejected=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_success_idempotent_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_success_digest_conflict_verified=true" \
        <<<"$EFFECT_LOG" \
      && grep -Fq "effect_cancel_idempotent_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_cancel_stale_attempt_rejected=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_cancel_digest_conflict_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_max_attempt_crash_dead_lettered=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_exhausted_reconciliation_idempotent=true" \
        <<<"$EFFECT_LOG" \
      && grep -Fq "effect_terminal_states_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_outbox_audit_verified=true" <<<"$EFFECT_LOG" \
      && grep -Fq "outbox_claim_attempt=2" <<<"$EFFECT_LOG" \
      && grep -Fq "effect_repository_wired=false" <<<"$EFFECT_LOG" \
      && grep -Fq "outbox_dispatch_enabled=false" <<<"$EFFECT_LOG" \
      && grep -Fq "service_dispatch_triggered=false" <<<"$EFFECT_LOG"; then
    EFFECT_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$EFFECT_PROBE_PASSED" != true ]]; then
  echo "$EFFECT_LOG" >&2
  echo "durable effect/outbox repository probe did not pass" >&2
  exit 1
fi

ADAPTER_NONCE="$(date +%s%N)"
ADAPTER_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.persistence.EffectAdapterContractProbeActivity \
  --es nonce "$ADAPTER_NONCE")"
if ! grep -Fq "Status: ok" <<<"$ADAPTER_PROBE_OUTPUT"; then
  echo "$ADAPTER_PROBE_OUTPUT" >&2
  echo "effect adapter contract debug probe did not start successfully" >&2
  exit 1
fi
ADAPTER_PROBE_PASSED=false
for _ in {1..40}; do
  ADAPTER_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbAdapterProbe:I)"
  if grep -Fq "nonce=$ADAPTER_NONCE adapter_probe_complete=true" \
      <<<"$ADAPTER_LOG" \
      && grep -Fq "effect_adapter_contract_verified=true" <<<"$ADAPTER_LOG" \
      && grep -Fq "unsafe_adapter_rejected=true" <<<"$ADAPTER_LOG" \
      && grep -Fq "adapter_destination_mismatch_rejected=true" \
        <<<"$ADAPTER_LOG" \
      && grep -Fq "adapter_duplicate_apply_idempotent=true" \
        <<<"$ADAPTER_LOG" \
      && grep -Fq "adapter_status_matches_apply_result=true" \
        <<<"$ADAPTER_LOG" \
      && grep -Fq "adapter_crash_after_apply_reconciled=true" \
        <<<"$ADAPTER_LOG" \
      && grep -Fq "adapter_crash_before_apply_retried=true" \
        <<<"$ADAPTER_LOG" \
      && grep -Fq "adapter_status_unavailable_deferred=true" \
        <<<"$ADAPTER_LOG" \
      && grep -Fq "adapter_unknown_status_dead_lettered=true" \
        <<<"$ADAPTER_LOG" \
      && grep -Fq "adapter_final_not_applied_dead_lettered=true" \
        <<<"$ADAPTER_LOG" \
      && grep -Fq "adapter_terminal_counts_verified=true" <<<"$ADAPTER_LOG" \
      && grep -Fq "adapter_fault_matrix_verified=true" <<<"$ADAPTER_LOG" \
      && grep -Fq "transient_effect_material_durable=false" <<<"$ADAPTER_LOG" \
      && grep -Fq "effect_adapter_production_wired=false" <<<"$ADAPTER_LOG" \
      && grep -Fq "real_adapter_dispatch_enabled=false" <<<"$ADAPTER_LOG" \
      && grep -Fq "service_dispatch_triggered=false" <<<"$ADAPTER_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$ADAPTER_LOG"; then
    ADAPTER_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$ADAPTER_PROBE_PASSED" != true ]]; then
  echo "$ADAPTER_LOG" >&2
  echo "effect adapter contract probe did not pass" >&2
  exit 1
fi

MATERIAL_NONCE="$(date +%s%N)"
MATERIAL_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.persistence.EffectDeliveryActivationProbeActivity \
  --es nonce "$MATERIAL_NONCE")"
if ! grep -Fq "Status: ok" <<<"$MATERIAL_PROBE_OUTPUT"; then
  echo "$MATERIAL_PROBE_OUTPUT" >&2
  echo "effect delivery activation debug probe did not start successfully" >&2
  exit 1
fi
MATERIAL_PROBE_PASSED=false
for _ in {1..40}; do
  MATERIAL_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbMaterialProbe:I)"
  if grep -Fq "nonce=$MATERIAL_NONCE material_probe_complete=true" \
      <<<"$MATERIAL_LOG" \
      && grep -Fq "current_empty_material_gate_verified=true" \
        <<<"$MATERIAL_LOG" \
      && grep -Fq "test_only_material_rejected=true" <<<"$MATERIAL_LOG" \
      && grep -Fq "synthetic_positive_gate_verified=true" <<<"$MATERIAL_LOG" \
      && grep -Fq "material_reopen_resolution_verified=true" \
        <<<"$MATERIAL_LOG" \
      && grep -Fq "material_defensive_copy_verified=true" <<<"$MATERIAL_LOG" \
      && grep -Fq "material_digest_mismatch_rejected=true" \
        <<<"$MATERIAL_LOG" \
      && grep -Fq "material_missing_rejected=true" <<<"$MATERIAL_LOG" \
      && grep -Fq "empty_material_resolution_blocked=true" \
        <<<"$MATERIAL_LOG" \
      && grep -Fq "activation_gate_no_side_effect_verified=true" \
        <<<"$MATERIAL_LOG" \
      && grep -Fq "material_activation_contract_verified=true" \
        <<<"$MATERIAL_LOG" \
      && grep -Fq "production_effect_delivery_activation_allowed=false" \
        <<<"$MATERIAL_LOG" \
      && grep -Fq "production_effect_material_source=empty" \
        <<<"$MATERIAL_LOG" \
      && grep -Fq "production_effect_material_durable=false" \
        <<<"$MATERIAL_LOG" \
      && grep -Fq "synthetic_material_source_process_only=true" \
        <<<"$MATERIAL_LOG" \
      && grep -Fq "raw_effect_material_persisted=false" <<<"$MATERIAL_LOG" \
      && grep -Fq "effect_adapter_production_wired=false" <<<"$MATERIAL_LOG" \
      && grep -Fq "real_adapter_dispatch_enabled=false" <<<"$MATERIAL_LOG" \
      && grep -Fq "service_dispatch_triggered=false" <<<"$MATERIAL_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$MATERIAL_LOG"; then
    MATERIAL_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$MATERIAL_PROBE_PASSED" != true ]]; then
  echo "$MATERIAL_LOG" >&2
  echo "effect delivery activation probe did not pass" >&2
  exit 1
fi

PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W -n com.centralbrain.runtime/.RuntimeProbeActivity)"
if ! grep -Fq "Status: ok" <<<"$PROBE_OUTPUT"; then
  echo "$PROBE_OUTPUT" >&2
  echo "runtime debug probe did not start successfully" >&2
  exit 1
fi

sleep 1
SERVICE_DUMP="$("${ADB_DEVICE[@]}" shell dumpsys activity services com.centralbrain.runtime)"
if ! grep -Fq "com.centralbrain.runtime/.CentralBrainRuntimeService" <<<"$SERVICE_DUMP"; then
  echo "CentralBrainRuntimeService is not running" >&2
  exit 1
fi
RUNTIME_PID="$("${ADB_DEVICE[@]}" shell pidof com.centralbrain.runtime 2>/dev/null | tr -d '\r' || true)"
if [[ -z "$RUNTIME_PID" ]]; then
  echo "runtime-service process is not running" >&2
  exit 1
fi
RUNTIME_CLIENT_DUMP="$("${ADB_DEVICE[@]}" shell dumpsys activity service \
  com.centralbrain.runtime/.CentralBrainRuntimeService)"
for marker in \
  "production_effect_activation_gate_wired=true" \
  "production_effect_delivery_activation_allowed=false" \
  "production_effect_adapter_configured=false" \
  "production_effect_material_source=empty.effect.material" \
  "production_effect_apply_enabled=false" \
  "production_effect_status_query_enabled=false" \
  "production_effect_activation_blockers=ADAPTER_MISSING" \
  "service_dispatch_triggered=false" \
  "hardware_accessed=false"; do
  if ! grep -Fq "$marker" <<<"$RUNTIME_CLIENT_DUMP"; then
    echo "Runtime dumpsys effect gate missing marker: $marker" >&2
    exit 1
  fi
done

DEMO_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W -n com.centralbrain.demo/.DemoActivity)"
if ! grep -Fq "Status: ok" <<<"$DEMO_OUTPUT"; then
  echo "$DEMO_OUTPUT" >&2
  echo "Demo HMI did not start successfully" >&2
  exit 1
fi

ACTIVITY_DUMP="$("${ADB_DEVICE[@]}" shell dumpsys activity activities)"
if ! grep -Eq 'mResumedActivity.*com\.centralbrain\.demo/.DemoActivity|topResumedActivity=.*com\.centralbrain\.demo/.DemoActivity' <<<"$ACTIVITY_DUMP"; then
  echo "DemoActivity is not the resumed activity" >&2
  exit 1
fi

UI_DUMP=""
for _ in {1..20}; do
  "${ADB_DEVICE[@]}" shell uiautomator dump /sdcard/central-brain-demo.xml >/dev/null
  UI_DUMP="$("${ADB_DEVICE[@]}" exec-out cat /sdcard/central-brain-demo.xml | tr -d '\r')"
  if grep -Fq "Typed Binder: completed" <<<"$UI_DUMP" \
      && grep -Fq "Replay: completed" <<<"$UI_DUMP" \
      && grep -Fq "Concurrent replay: completed" <<<"$UI_DUMP" \
      && grep -Fq "Cancel: confirmed" <<<"$UI_DUMP" \
      && grep -Fq "Governance: verified v1" <<<"$UI_DUMP"; then
    break
  fi
  sleep 0.5
done
for expected in \
  "Central Brain" \
  "android_integrated" \
  "Typed Binder: connected v1" \
  "Typed Binder: completed" \
  "Replay: completed" \
  "Concurrent replay: completed" \
  "Cancel: confirmed" \
  "Governance: verified v1"; do
  if ! grep -Fq "$expected" <<<"$UI_DUMP"; then
    echo "Demo HMI UI missing expected text: $expected" >&2
    exit 1
  fi
done

RUNTIME_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CentralBrainRuntime:I '*:S')"
if ! grep -Fq "job_supervisor_max_records=128 terminal_retention_ms=300000" \
    <<<"$RUNTIME_LOG"; then
  echo "Runtime did not report the bounded R3A Job Supervisor" >&2
  exit 1
fi
if ! grep -Fq "maturity=android_integrated evolution_stage=R4_DURABLE_WORKFLOW" \
    <<<"$RUNTIME_LOG"; then
  echo "Runtime did not report the R4 durable workflow stage" >&2
  exit 1
fi
if ! grep -Fq "capability_default=deny capability_rule_count=2" <<<"$RUNTIME_LOG"; then
  echo "Runtime did not load the strict R3B capability policy" >&2
  exit 1
fi
if ! grep -Fq "runtime_repository_wired=true task_recovery_enabled=false" \
    <<<"$RUNTIME_LOG"; then
  echo "Runtime did not report the bounded R4B2 repository/recovery boundary" >&2
  exit 1
fi
if ! grep -Fq "restart_reconciliation_enabled=true task_execution_resume_enabled=false" \
    <<<"$RUNTIME_LOG"; then
  echo "Runtime did not report the R4C1 fail-closed restart boundary" >&2
  exit 1
fi
for marker in \
  "production_effect_activation_gate_wired=true" \
  "production_effect_delivery_activation_allowed=false" \
  "production_effect_adapter_configured=false" \
  "production_effect_material_source=empty.effect.material" \
  "production_effect_material_durable=false" \
  "production_effect_apply_enabled=false" \
  "production_effect_status_query_enabled=false" \
  "production_effect_activation_blockers=ADAPTER_MISSING"; do
  if ! grep -Fq "$marker" <<<"$RUNTIME_LOG"; then
    echo "Runtime effect delivery gate missing marker: $marker" >&2
    exit 1
  fi
done
if ! grep -Fq "packages=[com.centralbrain.demo] resolved=true" <<<"$RUNTIME_LOG"; then
  echo "Runtime did not resolve the Demo Binder caller from trusted package evidence" >&2
  exit 1
fi

GOVERNANCE_LOG="$("${ADB_DEVICE[@]}" logcat -d \
  CentralBrainGovernance:I '*:S')"
if ! grep -Fq "state_source=runtime-owned-state-stub" <<<"$GOVERNANCE_LOG" \
    || ! grep -Fq "source_hardware_backed=false" <<<"$GOVERNANCE_LOG" \
    || ! grep -Fq "source_production_trusted=false" <<<"$GOVERNANCE_LOG" \
    || ! grep -Fq "approval_grant_supported=false" <<<"$GOVERNANCE_LOG" \
    || ! grep -Fq "approval_durable=true" <<<"$GOVERNANCE_LOG"; then
  echo "Governance Service did not report the R3C trusted-state/approval boundary" >&2
  exit 1
fi
if ! grep -Fq "packages=[com.centralbrain.demo] resolved=true" <<<"$GOVERNANCE_LOG"; then
  echo "Governance Service did not authorize the trusted Demo caller" >&2
  exit 1
fi

GOVERNANCE_DEMO_LOG="$("${ADB_DEVICE[@]}" logcat -d \
  CentralBrainGovernanceDemo:I '*:S')"
for marker in \
  "governance_probe_complete=true" \
  "read_policy_only=true" \
  "comfort_policy_only=true" \
  "high_risk_approval_required=true" \
  "approval_pending=true" \
  "approval_idempotent_replay_verified=true" \
  "approval_cancelled=true" \
  "approval_grant_supported=false" \
  "approval_durable=true" \
  "dispatch_allowed=false"; do
  if ! grep -Fq "$marker" <<<"$GOVERNANCE_DEMO_LOG"; then
    echo "Demo Governance evidence missing marker: $marker" >&2
    exit 1
  fi
done

DURABILITY_NONCE="$(date +%s%N)"
DURABILITY_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.persistence.RuntimeDurabilityProbeActivity \
  --es nonce "$DURABILITY_NONCE")"
if ! grep -Fq "Status: ok" <<<"$DURABILITY_PROBE_OUTPUT"; then
  echo "$DURABILITY_PROBE_OUTPUT" >&2
  echo "Runtime durability debug probe did not start successfully" >&2
  exit 1
fi
DURABILITY_PROBE_PASSED=false
for _ in {1..40}; do
  DURABILITY_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbRuntimeDurability:I)"
  if grep -Fq "nonce=$DURABILITY_NONCE runtime_durability_probe_complete=true" \
      <<<"$DURABILITY_LOG" \
      && grep -Fq "durable_completed_task_verified=true" <<<"$DURABILITY_LOG" \
      && grep -Fq "durable_cancelled_task_verified=true" <<<"$DURABILITY_LOG" \
      && grep -Fq "durable_checkpoint_chain_verified=true" <<<"$DURABILITY_LOG" \
      && grep -Fq "durable_terminal_settlement_verified=true" <<<"$DURABILITY_LOG" \
      && grep -Fq "production_durable_approval_verified=true" <<<"$DURABILITY_LOG" \
      && grep -Fq "runtime_repository_wired=true" <<<"$DURABILITY_LOG" \
      && grep -Fq "task_recovery_enabled=false" <<<"$DURABILITY_LOG" \
      && grep -Fq "durable_dispatch_enabled=false" <<<"$DURABILITY_LOG"; then
    DURABILITY_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$DURABILITY_PROBE_PASSED" != true ]]; then
  echo "$DURABILITY_LOG" >&2
  echo "Runtime durable lifecycle probe did not pass" >&2
  exit 1
fi

API_33_EXIT=false
if [[ "$SDK" == "33" ]]; then
  API_33_EXIT=true
fi

printf '%s\n' \
  "device_serial=$SERIAL" \
  "device_model=$MODEL" \
  "android_api=$SDK" \
  "device_abi=$ABI" \
  "runtime_service_running=true" \
  "runtime_pid=$RUNTIME_PID" \
  "evolution_stage=R4_DURABLE_WORKFLOW" \
  "demo_hmi_resumed=true" \
  "demo_ui_android_integrated=true" \
  "typed_binder_connected=true" \
  "typed_binder_callback_completed=true" \
  "typed_binder_cancel_confirmed=true" \
  "signature_permission_enforced=true" \
  "governance_signature_permission_enforced=true" \
  "governance_permission_requested_by_demo=true" \
  "diagnostic_permission_requested_by_demo=false" \
  "diagnostic_binder_page_verified=true" \
  "effect_delivery_activation_diagnostic_verified=true" \
  "room_schema_version=2" \
  "room_table_count=8" \
  "room_wal_enabled=true" \
  "room_migration_1_2_verified=true" \
  "legacy_task_preserved=true" \
  "legacy_approval_preserved=true" \
  "task_admission_transaction_verified=true" \
  "task_idempotent_replay_verified=true" \
  "task_idempotency_conflict_verified=true" \
  "task_owner_isolation_verified=true" \
  "runtime_repository_wired=true" \
  "task_recovery_enabled=false" \
  "restart_reconciliation_enabled=true" \
  "restart_reconciliation_idempotent=true" \
  "incomplete_completion_reconciled_failed=true" \
  "task_execution_resume_enabled=false" \
  "effect_prepare_transaction_verified=true" \
  "effect_non_running_task_rejected=true" \
  "effect_route_mismatch_rejected=true" \
  "effect_owner_scoped_token_verified=true" \
  "effect_reopen_replay_verified=true" \
  "effect_idempotency_conflict_verified=true" \
  "effect_owner_scope_verified=true" \
  "outbox_claim_transaction_verified=true" \
  "outbox_reopen_requeue_verified=true" \
  "outbox_reconciliation_idempotent=true" \
  "outbox_fair_requeue_verified=true" \
  "outbox_second_claim_verified=true" \
  "effect_retry_idempotent_verified=true" \
  "effect_retry_delay_conflict_verified=true" \
  "effect_retry_digest_conflict_verified=true" \
  "effect_retry_not_before_verified=true" \
  "effect_final_claim_verified=true" \
  "effect_attempt_limit_verified=true" \
  "effect_dead_letter_idempotent_verified=true" \
  "effect_dead_letter_digest_conflict_verified=true" \
  "effect_stale_attempt_rejected=true" \
  "effect_success_idempotent_verified=true" \
  "effect_success_digest_conflict_verified=true" \
  "effect_cancel_idempotent_verified=true" \
  "effect_cancel_stale_attempt_rejected=true" \
  "effect_cancel_digest_conflict_verified=true" \
  "effect_max_attempt_crash_dead_lettered=true" \
  "effect_exhausted_reconciliation_idempotent=true" \
  "effect_terminal_states_verified=true" \
  "effect_outbox_audit_verified=true" \
  "outbox_claim_attempt=2" \
  "effect_repository_wired=false" \
  "outbox_dispatch_enabled=false" \
  "effect_adapter_contract_verified=true" \
  "unsafe_adapter_rejected=true" \
  "adapter_destination_mismatch_rejected=true" \
  "adapter_duplicate_apply_idempotent=true" \
  "adapter_status_matches_apply_result=true" \
  "adapter_crash_after_apply_reconciled=true" \
  "adapter_crash_before_apply_retried=true" \
  "adapter_status_unavailable_deferred=true" \
  "adapter_unknown_status_dead_lettered=true" \
  "adapter_final_not_applied_dead_lettered=true" \
  "adapter_terminal_counts_verified=true" \
  "adapter_fault_matrix_verified=true" \
  "transient_effect_material_durable=false" \
  "effect_adapter_production_wired=false" \
  "real_adapter_dispatch_enabled=false" \
  "current_empty_material_gate_verified=true" \
  "test_only_material_rejected=true" \
  "synthetic_positive_gate_verified=true" \
  "material_reopen_resolution_verified=true" \
  "material_defensive_copy_verified=true" \
  "material_digest_mismatch_rejected=true" \
  "material_missing_rejected=true" \
  "empty_material_resolution_blocked=true" \
  "activation_gate_no_side_effect_verified=true" \
  "material_activation_contract_verified=true" \
  "production_effect_delivery_activation_allowed=false" \
  "production_effect_activation_gate_wired=true" \
  "production_effect_adapter_configured=false" \
  "production_effect_material_source=empty" \
  "production_effect_material_source_id=empty.effect.material" \
  "production_effect_material_durable=false" \
  "production_effect_apply_enabled=false" \
  "production_effect_status_query_enabled=false" \
  "production_effect_gate_dumpsys_verified=true" \
  "synthetic_material_source_process_only=true" \
  "raw_effect_material_persisted=false" \
  "durable_replay_callback_verified=true" \
  "durable_concurrent_replay_verified=true" \
  "durable_completed_task_verified=true" \
  "durable_cancelled_task_verified=true" \
  "durable_checkpoint_chain_verified=true" \
  "durable_terminal_settlement_verified=true" \
  "approval_reopen_replay_verified=true" \
  "approval_idempotent_replay_verified=true" \
  "approval_idempotency_conflict_verified=true" \
  "approval_owner_isolation_verified=true" \
  "approval_cancel_idempotency_verified=true" \
  "approval_expiry_verified=true" \
  "production_durable_approval_verified=true" \
  "durable_dispatch_enabled=false" \
  "job_supervisor_active=true" \
  "trusted_caller_identity_resolved=true" \
  "request_identity_fields_used=false" \
  "capability_policy_loaded=true" \
  "allowed_client_capabilities_verified=true" \
  "governance_typed_binder_connected=true" \
  "action_risk_classes_verified=true" \
  "runtime_owned_state_provider_verified=true" \
  "high_risk_pending_approval_verified=true" \
  "approval_cancel_verified=true" \
  "approval_grant_supported=false" \
  "approval_durable=true" \
  "service_dispatch_triggered=false" \
  "r1_api33_exit_criteria_met=$API_33_EXIT" \
  "r4_durable_workflow_exit_criteria_met=$API_33_EXIT" \
  "hardware_accessed=false" \
  "driver_development_triggered=false" \
  "virtualization_development_triggered=false"

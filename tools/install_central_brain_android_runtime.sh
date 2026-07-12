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
        <<<"$DIAGNOSTIC_LOG" \
      && grep -Fq "model_runtime_readiness_diagnostic_verified=true" \
        <<<"$DIAGNOSTIC_LOG" \
      && grep -Fq "event_runtime_readiness_diagnostic_verified=true" \
        <<<"$DIAGNOSTIC_LOG" \
      && grep -Fq "memory_runtime_readiness_diagnostic_verified=true" \
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
      && grep -Fq "room_migration_2_3_verified=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "room_schema_version=3" <<<"$MIGRATION_LOG" \
      && grep -Fq "room_table_count=8" <<<"$MIGRATION_LOG" \
      && grep -Fq "room_wal_enabled=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "legacy_task_preserved=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "legacy_approval_preserved=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "legacy_event_cursor_preserved=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "event_cursor_schema_v3_verified=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "event_cursor_schema_ready=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "event_cursor_repository_wired=false" <<<"$MIGRATION_LOG" \
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

EVENT_CURSOR_NONCE="$(date +%s%N)"
EVENT_CURSOR_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.persistence.DurableEventCursorRepositoryProbeActivity \
  --es nonce "$EVENT_CURSOR_NONCE")"
if ! grep -Fq "Status: ok" <<<"$EVENT_CURSOR_PROBE_OUTPUT"; then
  echo "$EVENT_CURSOR_PROBE_OUTPUT" >&2
  echo "durable Event cursor repository debug probe did not start successfully" >&2
  exit 1
fi
EVENT_CURSOR_PROBE_PASSED=false
for _ in {1..40}; do
  EVENT_CURSOR_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbEventCursorRepo:I)"
  if grep -Fq "nonce=$EVENT_CURSOR_NONCE durable_event_cursor_probe_complete=true" \
      <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "durable_event_cursor_repository_verified=true" \
        <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "event_cursor_registration_idempotency_verified=true" \
        <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "event_cursor_admission_bounds_verified=true" \
        <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "event_cursor_owner_isolation_verified=true" \
        <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "event_cursor_ack_monotonic_verified=true" \
        <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "event_cursor_source_regression_blocked=true" \
        <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "event_cursor_overflow_resync_verified=true" \
        <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "event_cursor_reopen_recovery_verified=true" \
        <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "event_cursor_cancel_idempotency_verified=true" \
        <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "event_cursor_record_bounds_verified=true" \
        <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "event_cursor_audit_exactly_once_verified=true" \
        <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "event_cursor_probe_persistence_verified=true" \
        <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "event_cursor_repository_implementation_available=true" \
        <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "event_cursor_repository_production_wired=false" \
        <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "event_cursor_persistence_wired=false" <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "durable_event_source_available=false" <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "event_broker_production_wired=false" <<<"$EVENT_CURSOR_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$EVENT_CURSOR_LOG"; then
    EVENT_CURSOR_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$EVENT_CURSOR_PROBE_PASSED" != true ]]; then
  echo "$EVENT_CURSOR_LOG" >&2
  echo "durable Event cursor repository probe did not pass" >&2
  exit 1
fi

MEMORY_LIFECYCLE_NONCE="$(date +%s%N)"
MEMORY_LIFECYCLE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.memory.MemoryLifecycleProbeActivity \
  --es nonce "$MEMORY_LIFECYCLE_NONCE")"
if ! grep -Fq "Status: ok" <<<"$MEMORY_LIFECYCLE_OUTPUT"; then
  echo "$MEMORY_LIFECYCLE_OUTPUT" >&2
  echo "Memory lifecycle debug probe did not start successfully" >&2
  exit 1
fi
MEMORY_LIFECYCLE_PASSED=false
for _ in {1..40}; do
  MEMORY_LIFECYCLE_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbMemoryLifecycle:I)"
  if grep -Fq "nonce=$MEMORY_LIFECYCLE_NONCE memory_lifecycle_probe_complete=true" \
      <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_lifecycle_contract_verified=true" \
        <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_scope_policy_verified=true" <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_profile_consent_verified=true" \
        <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_write_idempotency_verified=true" \
        <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_owner_isolation_verified=true" \
        <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_query_redaction_verified=true" \
        <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_ttl_expiry_verified=true" <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_delete_idempotency_verified=true" \
        <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_export_authorization_verified=true" \
        <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_record_bounds_verified=true" \
        <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_process_only=true" <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_persistence_wired=false" <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_production_service_wired=false" \
        <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "raw_memory_content_stored=false" <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_profile_storage_durable=false" \
        <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_consent_revocation_wired=false" \
        <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "memory_encryption_key_configured=false" \
        <<<"$MEMORY_LIFECYCLE_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$MEMORY_LIFECYCLE_LOG"; then
    MEMORY_LIFECYCLE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$MEMORY_LIFECYCLE_PASSED" != true ]]; then
  echo "$MEMORY_LIFECYCLE_LOG" >&2
  echo "Memory lifecycle probe did not pass" >&2
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

MODEL_PROVIDER_NONCE="$(date +%s%N)"
MODEL_PROVIDER_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.model.ModelProviderContractProbeActivity \
  --es nonce "$MODEL_PROVIDER_NONCE")"
if ! grep -Fq "Status: ok" <<<"$MODEL_PROVIDER_PROBE_OUTPUT"; then
  echo "$MODEL_PROVIDER_PROBE_OUTPUT" >&2
  echo "model provider contract debug probe did not start successfully" >&2
  exit 1
fi
MODEL_PROVIDER_PROBE_PASSED=false
for _ in {1..40}; do
  MODEL_PROVIDER_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbModelProbe:I)"
  if grep -Fq "nonce=$MODEL_PROVIDER_NONCE model_provider_probe_complete=true" \
      <<<"$MODEL_PROVIDER_LOG" \
      && grep -Fq "model_provider_contract_verified=true" \
        <<<"$MODEL_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_profile_verified=true" \
        <<<"$MODEL_PROVIDER_LOG" \
      && grep -Fq "vendor_npu_empty_profile_verified=true" \
        <<<"$MODEL_PROVIDER_LOG" \
      && grep -Fq "unsafe_provider_descriptor_rejected=true" \
        <<<"$MODEL_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_implementation_configured=false" \
        <<<"$MODEL_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_routing_enabled=false" \
        <<<"$MODEL_PROVIDER_LOG" \
      && grep -Fq "vendor_npu_provider_available=false" \
        <<<"$MODEL_PROVIDER_LOG" \
      && grep -Fq "model_provider_runtime_wired=false" \
        <<<"$MODEL_PROVIDER_LOG" \
      && grep -Fq "model_router_dispatch_enabled=false" \
        <<<"$MODEL_PROVIDER_LOG" \
      && grep -Fq "ollama_android_provider_configured=false" \
        <<<"$MODEL_PROVIDER_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$MODEL_PROVIDER_LOG"; then
    MODEL_PROVIDER_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$MODEL_PROVIDER_PROBE_PASSED" != true ]]; then
  echo "$MODEL_PROVIDER_LOG" >&2
  echo "model provider contract probe did not pass" >&2
  exit 1
fi

SCHEDULER_NONCE="$(date +%s%N)"
SCHEDULER_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.scheduler.InferenceSchedulerContractProbeActivity \
  --es nonce "$SCHEDULER_NONCE")"
if ! grep -Fq "Status: ok" <<<"$SCHEDULER_PROBE_OUTPUT"; then
  echo "$SCHEDULER_PROBE_OUTPUT" >&2
  echo "inference scheduler contract debug probe did not start successfully" >&2
  exit 1
fi
SCHEDULER_PROBE_PASSED=false
for _ in {1..40}; do
  SCHEDULER_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbSchedulerProbe:I)"
  if grep -Fq "nonce=$SCHEDULER_NONCE scheduler_probe_complete=true" \
      <<<"$SCHEDULER_LOG" \
      && grep -Fq "inference_scheduler_contract_verified=true" \
        <<<"$SCHEDULER_LOG" \
      && grep -Fq "trusted_effective_priority_verified=true" \
        <<<"$SCHEDULER_LOG" \
      && grep -Fq "priority_deadline_fifo_order_verified=true" \
        <<<"$SCHEDULER_LOG" \
      && grep -Fq "global_owner_queue_quota_verified=true" \
        <<<"$SCHEDULER_LOG" \
      && grep -Fq "global_owner_running_quota_verified=true" \
        <<<"$SCHEDULER_LOG" \
      && grep -Fq "provider_slot_quota_verified=true" <<<"$SCHEDULER_LOG" \
      && grep -Fq "queued_deadline_expiry_verified=true" \
        <<<"$SCHEDULER_LOG" \
      && grep -Fq "running_deadline_cancel_directive_verified=true" \
        <<<"$SCHEDULER_LOG" \
      && grep -Fq "queued_cancel_verified=true" <<<"$SCHEDULER_LOG" \
      && grep -Fq "running_cancel_requires_provider_verified=true" \
        <<<"$SCHEDULER_LOG" \
      && grep -Fq "completion_after_cancel_resolved=true" \
        <<<"$SCHEDULER_LOG" \
      && grep -Fq "current_profiles_non_routable_verified=true" \
        <<<"$SCHEDULER_LOG" \
      && grep -Fq "provider_cancel_invoked=false" <<<"$SCHEDULER_LOG" \
      && grep -Fq "scheduler_production_wired=false" <<<"$SCHEDULER_LOG" \
      && grep -Fq "model_provider_runtime_wired=false" <<<"$SCHEDULER_LOG" \
      && grep -Fq "model_router_dispatch_enabled=false" <<<"$SCHEDULER_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$SCHEDULER_LOG"; then
    SCHEDULER_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$SCHEDULER_PROBE_PASSED" != true ]]; then
  echo "$SCHEDULER_LOG" >&2
  echo "inference scheduler contract probe did not pass" >&2
  exit 1
fi

STUB_PROVIDER_NONCE="$(date +%s%N)"
STUB_PROVIDER_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.model.DeterministicStubProviderProbeActivity \
  --es nonce "$STUB_PROVIDER_NONCE")"
if ! grep -Fq "Status: ok" <<<"$STUB_PROVIDER_PROBE_OUTPUT"; then
  echo "$STUB_PROVIDER_PROBE_OUTPUT" >&2
  echo "deterministic stub provider debug probe did not start successfully" >&2
  exit 1
fi
STUB_PROVIDER_PROBE_PASSED=false
for _ in {1..40}; do
  STUB_PROVIDER_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbStubProviderProbe:I)"
  if grep -Fq "nonce=$STUB_PROVIDER_NONCE stub_provider_probe_complete=true" \
      <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_provider_contract_verified=true" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_lifecycle_verified=true" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_stream_verified=true" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_output_deterministic=true" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_cancel_ack_verified=true" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_metrics_verified=true" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_retryable_fault_verified=true" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_fault_isolation_verified=true" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_profile_boundary_verified=true" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_test_only=true" <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_implementation_available=true" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_implementation_configured=false" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "deterministic_stub_routing_enabled=false" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "model_provider_runtime_wired=false" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "model_router_dispatch_enabled=false" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "production_inference_enabled=false" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "ollama_android_provider_configured=false" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "vendor_npu_provider_available=false" \
        <<<"$STUB_PROVIDER_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$STUB_PROVIDER_LOG"; then
    STUB_PROVIDER_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$STUB_PROVIDER_PROBE_PASSED" != true ]]; then
  echo "$STUB_PROVIDER_LOG" >&2
  echo "deterministic stub provider probe did not pass" >&2
  exit 1
fi

MODEL_ROUTER_NONCE="$(date +%s%N)"
MODEL_ROUTER_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.model.TestOnlyModelRouterProbeActivity \
  --es nonce "$MODEL_ROUTER_NONCE")"
if ! grep -Fq "Status: ok" <<<"$MODEL_ROUTER_PROBE_OUTPUT"; then
  echo "$MODEL_ROUTER_PROBE_OUTPUT" >&2
  echo "test-only model router debug probe did not start successfully" >&2
  exit 1
fi
MODEL_ROUTER_PROBE_PASSED=false
for _ in {1..40}; do
  MODEL_ROUTER_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbModelRouterProbe:I)"
  if grep -Fq "nonce=$MODEL_ROUTER_NONCE model_router_probe_complete=true" \
      <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "test_model_router_contract_verified=true" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "test_model_router_e2e_verified=true" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "scheduler_provider_lease_binding_verified=true" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "model_router_stream_forward_verified=true" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "model_router_cancel_verified=true" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "model_router_deadline_verified=true" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "model_router_no_fallback_verified=true" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "model_router_terminal_once_verified=true" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "model_router_provider_identity_verified=true" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "model_router_replay_validation_verified=true" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "model_router_profile_boundary_verified=true" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "test_model_router_dispatch_verified=true" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "provider_infer_invoked_in_debug=true" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "model_router_test_only=true" <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "model_router_implementation_available=true" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "production_model_router_wired=false" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "production_model_router_dispatch_enabled=false" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "production_inference_enabled=false" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "deterministic_stub_implementation_configured=false" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "deterministic_stub_routing_enabled=false" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "ollama_android_provider_configured=false" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "vendor_npu_provider_available=false" \
        <<<"$MODEL_ROUTER_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$MODEL_ROUTER_LOG"; then
    MODEL_ROUTER_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$MODEL_ROUTER_PROBE_PASSED" != true ]]; then
  echo "$MODEL_ROUTER_LOG" >&2
  echo "test-only model router probe did not pass" >&2
  exit 1
fi

EVENT_RUNTIME_NONCE="$(date +%s%N)"
EVENT_RUNTIME_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.events.BoundedEventRuntimeProbeActivity \
  --es nonce "$EVENT_RUNTIME_NONCE")"
if ! grep -Fq "Status: ok" <<<"$EVENT_RUNTIME_PROBE_OUTPUT"; then
  echo "$EVENT_RUNTIME_PROBE_OUTPUT" >&2
  echo "bounded event runtime debug probe did not start successfully" >&2
  exit 1
fi
EVENT_RUNTIME_PROBE_PASSED=false
for _ in {1..40}; do
  EVENT_RUNTIME_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbEventRuntimeProbe:I)"
  if grep -Fq "nonce=$EVENT_RUNTIME_NONCE event_runtime_probe_complete=true" \
      <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "event_runtime_contract_verified=true" \
        <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "event_trusted_topic_verified=true" \
        <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "event_monotonic_sequence_verified=true" \
        <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "event_cursor_replay_verified=true" \
        <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "event_overflow_before_delivery_verified=true" \
        <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "event_owner_isolation_verified=true" \
        <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "event_subscription_idempotency_verified=true" \
        <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "event_cancel_idempotency_verified=true" \
        <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "event_observer_retry_verified=true" \
        <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "event_runtime_process_only=true" \
        <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "event_cursor_persistence_wired=false" \
        <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "event_broker_production_wired=false" \
        <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "event_callback_binder_wired=false" \
        <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "dds_runtime_active=false" <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "network_transport_active=false" <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "vehicle_bus_accessed=false" <<<"$EVENT_RUNTIME_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$EVENT_RUNTIME_LOG"; then
    EVENT_RUNTIME_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$EVENT_RUNTIME_PROBE_PASSED" != true ]]; then
  echo "$EVENT_RUNTIME_LOG" >&2
  echo "bounded event runtime probe did not pass" >&2
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
  "model_runtime_readiness_snapshot_wired=true" \
  "production_inference_allowed=false" \
  "model_provider_contract_available=true" \
  "inference_scheduler_contract_available=true" \
  "test_model_router_implementation_available=true" \
  "model_router_test_only=true" \
  "deterministic_stub_profile_id=deterministic.stub" \
  "deterministic_stub_lifecycle=COLD" \
  "deterministic_stub_health=HEALTHY" \
  "deterministic_stub_detail_code=STUB_IMPLEMENTATION_NOT_WIRED" \
  "deterministic_stub_implementation_configured=false" \
  "deterministic_stub_routing_enabled=false" \
  "vendor_npu_profile_id=vendor.npu.empty" \
  "vendor_npu_lifecycle=UNAVAILABLE" \
  "vendor_npu_health=UNAVAILABLE" \
  "vendor_npu_detail_code=VENDOR_RUNTIME_UNAVAILABLE" \
  "vendor_npu_provider_available=false" \
  "scheduler_production_wired=false" \
  "production_model_router_wired=false" \
  "production_model_router_dispatch_enabled=false" \
  "ollama_android_provider_configured=false" \
  "model_runtime_activation_blockers=PRODUCTION_PROVIDER_MISSING" \
  "event_runtime_readiness_snapshot_wired=true" \
  "event_runtime_activation_allowed=false" \
  "bounded_event_runtime_implementation_available=true" \
  "event_cursor_schema_ready=true" \
  "event_repository_implementation_available=true" \
  "trusted_event_topic_count=3" \
  "durable_event_source_available=false" \
  "event_runtime_production_wired=false" \
  "event_cursor_repository_production_wired=false" \
  "event_cursor_persistence_wired=false" \
  "event_callback_binder_wired=false" \
  "event_broker_production_wired=false" \
  "event_middleware_chain_wired=false" \
  "raw_event_payload_persisted=false" \
  "dds_runtime_active=false" \
  "network_transport_active=false" \
  "vehicle_bus_accessed=false" \
  "event_runtime_activation_blockers=DURABLE_PUBLISHER_SEQUENCE_MISSING" \
  "memory_runtime_readiness_snapshot_wired=true" \
  "memory_runtime_activation_allowed=false" \
  "bounded_memory_lifecycle_implementation_available=true" \
  "memory_scope_count=3" \
  "memory_schema_ready=false" \
  "memory_repository_implementation_available=false" \
  "durable_encrypted_memory_storage_available=false" \
  "memory_encryption_key_lifecycle_configured=false" \
  "memory_consent_authority_wired=false" \
  "memory_consent_revocation_wired=false" \
  "trusted_memory_retention_clock_wired=false" \
  "memory_runtime_production_wired=false" \
  "memory_repository_production_wired=false" \
  "memory_middleware_chain_wired=false" \
  "raw_memory_content_stored=false" \
  "profile_memory_storage_durable=false" \
  "memory_runtime_activation_blockers=DURABLE_ENCRYPTED_STORAGE_MISSING" \
  "service_dispatch_triggered=false" \
  "hardware_accessed=false"; do
  if ! grep -Fq "$marker" <<<"$RUNTIME_CLIENT_DUMP"; then
    echo "Runtime dumpsys missing marker: $marker" >&2
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
for marker in \
  "model_runtime_readiness_snapshot_wired=true" \
  "production_inference_allowed=false" \
  "deterministic_stub_profile_id=deterministic.stub" \
  "deterministic_stub_lifecycle=COLD" \
  "deterministic_stub_health=HEALTHY" \
  "deterministic_stub_detail_code=STUB_IMPLEMENTATION_NOT_WIRED" \
  "deterministic_stub_implementation_configured=false" \
  "deterministic_stub_routing_enabled=false" \
  "vendor_npu_profile_id=vendor.npu.empty" \
  "vendor_npu_lifecycle=UNAVAILABLE" \
  "vendor_npu_health=UNAVAILABLE" \
  "vendor_npu_detail_code=VENDOR_RUNTIME_UNAVAILABLE" \
  "vendor_npu_provider_available=false" \
  "scheduler_production_wired=false" \
  "production_model_router_wired=false" \
  "production_model_router_dispatch_enabled=false" \
  "model_runtime_activation_blockers=PRODUCTION_PROVIDER_MISSING"; do
  if ! grep -Fq "$marker" <<<"$RUNTIME_LOG"; then
    echo "Runtime model readiness missing marker: $marker" >&2
    exit 1
  fi
done
for marker in \
  "event_runtime_readiness_snapshot_wired=true" \
  "event_runtime_activation_allowed=false" \
  "bounded_event_runtime_implementation_available=true" \
  "event_cursor_schema_ready=true" \
  "event_repository_implementation_available=true" \
  "durable_event_source_available=false" \
  "event_runtime_production_wired=false" \
  "event_cursor_repository_production_wired=false" \
  "event_cursor_persistence_wired=false" \
  "event_callback_binder_wired=false" \
  "event_broker_production_wired=false" \
  "event_middleware_chain_wired=false" \
  "event_runtime_activation_blockers=DURABLE_PUBLISHER_SEQUENCE_MISSING"; do
  if ! grep -Fq "$marker" <<<"$RUNTIME_LOG"; then
    echo "Runtime Event readiness missing marker: $marker" >&2
    exit 1
  fi
done
for marker in \
  "memory_runtime_readiness_snapshot_wired=true" \
  "memory_runtime_activation_allowed=false" \
  "bounded_memory_lifecycle_implementation_available=true" \
  "memory_scope_count=3" \
  "memory_schema_ready=false" \
  "memory_repository_implementation_available=false" \
  "durable_encrypted_memory_storage_available=false" \
  "memory_encryption_key_lifecycle_configured=false" \
  "memory_consent_authority_wired=false" \
  "memory_consent_revocation_wired=false" \
  "trusted_memory_retention_clock_wired=false" \
  "memory_runtime_production_wired=false" \
  "memory_repository_production_wired=false" \
  "memory_middleware_chain_wired=false" \
  "memory_runtime_activation_blockers=DURABLE_ENCRYPTED_STORAGE_MISSING"; do
  if ! grep -Fq "$marker" <<<"$RUNTIME_LOG"; then
    echo "Runtime Memory readiness missing marker: $marker" >&2
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
  "model_runtime_readiness_diagnostic_verified=true" \
  "event_runtime_readiness_diagnostic_verified=true" \
  "memory_runtime_readiness_diagnostic_verified=true" \
  "room_schema_version=3" \
  "room_table_count=8" \
  "room_wal_enabled=true" \
  "room_migration_1_2_verified=true" \
  "room_migration_2_3_verified=true" \
  "legacy_task_preserved=true" \
  "legacy_approval_preserved=true" \
  "legacy_event_cursor_preserved=true" \
  "event_cursor_schema_v3_verified=true" \
  "event_cursor_schema_ready=true" \
  "event_cursor_repository_wired=false" \
  "durable_event_cursor_repository_verified=true" \
  "event_cursor_registration_idempotency_verified=true" \
  "event_cursor_admission_bounds_verified=true" \
  "event_cursor_owner_isolation_verified=true" \
  "event_cursor_ack_monotonic_verified=true" \
  "event_cursor_source_regression_blocked=true" \
  "event_cursor_overflow_resync_verified=true" \
  "event_cursor_reopen_recovery_verified=true" \
  "event_cursor_cancel_idempotency_verified=true" \
  "event_cursor_record_bounds_verified=true" \
  "event_cursor_audit_exactly_once_verified=true" \
  "event_cursor_probe_persistence_verified=true" \
  "event_cursor_repository_implementation_available=true" \
  "event_cursor_repository_production_wired=false" \
  "durable_event_source_available=false" \
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
  "model_runtime_readiness_snapshot_wired=true" \
  "model_runtime_readiness_log_verified=true" \
  "model_runtime_readiness_dumpsys_verified=true" \
  "production_inference_allowed=false" \
  "model_provider_contract_available=true" \
  "inference_scheduler_contract_available=true" \
  "test_model_router_implementation_available=true" \
  "model_router_test_only=true" \
  "model_runtime_activation_blockers=PRODUCTION_PROVIDER_MISSING,PRODUCTION_ROUTE_MISSING,SCHEDULER_NOT_WIRED,MODEL_ROUTER_NOT_WIRED,VENDOR_NPU_INTERFACE_EMPTY" \
  "event_runtime_readiness_snapshot_wired=true" \
  "event_runtime_readiness_log_verified=true" \
  "event_runtime_readiness_dumpsys_verified=true" \
  "event_runtime_activation_allowed=false" \
  "bounded_event_runtime_implementation_available=true" \
  "event_repository_implementation_available=true" \
  "trusted_event_topic_count=3" \
  "event_runtime_production_wired=false" \
  "event_middleware_chain_wired=false" \
  "raw_event_payload_persisted=false" \
  "event_runtime_activation_blockers=DURABLE_PUBLISHER_SEQUENCE_MISSING,EVENT_RUNTIME_NOT_WIRED,EVENT_REPOSITORY_NOT_WIRED,CALLBACK_BINDER_NOT_DEFINED,BROKER_NOT_CONFIGURED,MIDDLEWARE_CHAIN_NOT_WIRED" \
  "memory_runtime_readiness_snapshot_wired=true" \
  "memory_runtime_readiness_log_verified=true" \
  "memory_runtime_readiness_dumpsys_verified=true" \
  "memory_runtime_activation_allowed=false" \
  "bounded_memory_lifecycle_implementation_available=true" \
  "memory_scope_count=3" \
  "memory_schema_ready=false" \
  "memory_repository_implementation_available=false" \
  "durable_encrypted_memory_storage_available=false" \
  "memory_encryption_key_lifecycle_configured=false" \
  "memory_consent_authority_wired=false" \
  "trusted_memory_retention_clock_wired=false" \
  "memory_runtime_production_wired=false" \
  "memory_repository_production_wired=false" \
  "memory_middleware_chain_wired=false" \
  "memory_runtime_activation_blockers=DURABLE_ENCRYPTED_STORAGE_MISSING,KEY_LIFECYCLE_NOT_CONFIGURED,CONSENT_AUTHORITY_NOT_WIRED,CONSENT_REVOCATION_NOT_WIRED,TRUSTED_RETENTION_CLOCK_NOT_WIRED,MEMORY_REPOSITORY_NOT_IMPLEMENTED,MEMORY_RUNTIME_NOT_WIRED,MIDDLEWARE_CHAIN_NOT_WIRED" \
  "synthetic_material_source_process_only=true" \
  "raw_effect_material_persisted=false" \
  "model_provider_contract_verified=true" \
  "deterministic_stub_profile_verified=true" \
  "vendor_npu_empty_profile_verified=true" \
  "unsafe_provider_descriptor_rejected=true" \
  "deterministic_stub_implementation_configured=false" \
  "deterministic_stub_routing_enabled=false" \
  "vendor_npu_provider_available=false" \
  "model_provider_runtime_wired=false" \
  "model_router_dispatch_enabled=false" \
  "ollama_android_provider_configured=false" \
  "inference_scheduler_contract_verified=true" \
  "trusted_effective_priority_verified=true" \
  "priority_deadline_fifo_order_verified=true" \
  "global_owner_queue_quota_verified=true" \
  "global_owner_running_quota_verified=true" \
  "provider_slot_quota_verified=true" \
  "queued_deadline_expiry_verified=true" \
  "running_deadline_cancel_directive_verified=true" \
  "queued_cancel_verified=true" \
  "running_cancel_requires_provider_verified=true" \
  "completion_after_cancel_resolved=true" \
  "current_profiles_non_routable_verified=true" \
  "provider_cancel_invoked=false" \
  "scheduler_production_wired=false" \
  "deterministic_stub_provider_contract_verified=true" \
  "deterministic_stub_lifecycle_verified=true" \
  "deterministic_stub_stream_verified=true" \
  "deterministic_stub_output_deterministic=true" \
  "deterministic_stub_cancel_ack_verified=true" \
  "deterministic_stub_metrics_verified=true" \
  "deterministic_stub_retryable_fault_verified=true" \
  "deterministic_stub_fault_isolation_verified=true" \
  "deterministic_stub_profile_boundary_verified=true" \
  "deterministic_stub_test_only=true" \
  "deterministic_stub_implementation_available=true" \
  "test_model_router_contract_verified=true" \
  "test_model_router_e2e_verified=true" \
  "scheduler_provider_lease_binding_verified=true" \
  "model_router_stream_forward_verified=true" \
  "model_router_cancel_verified=true" \
  "model_router_deadline_verified=true" \
  "model_router_no_fallback_verified=true" \
  "model_router_terminal_once_verified=true" \
  "model_router_provider_identity_verified=true" \
  "model_router_replay_validation_verified=true" \
  "model_router_profile_boundary_verified=true" \
  "test_model_router_dispatch_verified=true" \
  "provider_infer_invoked_in_debug=true" \
  "model_router_test_only=true" \
  "model_router_implementation_available=true" \
  "production_model_router_wired=false" \
  "production_model_router_dispatch_enabled=false" \
  "production_inference_enabled=false" \
  "event_runtime_contract_verified=true" \
  "event_trusted_topic_verified=true" \
  "event_monotonic_sequence_verified=true" \
  "event_cursor_replay_verified=true" \
  "event_overflow_before_delivery_verified=true" \
  "event_owner_isolation_verified=true" \
  "event_subscription_idempotency_verified=true" \
  "event_cancel_idempotency_verified=true" \
  "event_observer_retry_verified=true" \
  "event_runtime_process_only=true" \
  "event_cursor_persistence_wired=false" \
  "event_broker_production_wired=false" \
  "event_callback_binder_wired=false" \
  "dds_runtime_active=false" \
  "network_transport_active=false" \
  "vehicle_bus_accessed=false" \
  "memory_lifecycle_contract_verified=true" \
  "memory_scope_policy_verified=true" \
  "memory_profile_consent_verified=true" \
  "memory_write_idempotency_verified=true" \
  "memory_owner_isolation_verified=true" \
  "memory_query_redaction_verified=true" \
  "memory_ttl_expiry_verified=true" \
  "memory_delete_idempotency_verified=true" \
  "memory_export_authorization_verified=true" \
  "memory_record_bounds_verified=true" \
  "memory_process_only=true" \
  "memory_persistence_wired=false" \
  "memory_production_service_wired=false" \
  "raw_memory_content_stored=false" \
  "memory_profile_storage_durable=false" \
  "memory_consent_revocation_wired=false" \
  "memory_encryption_key_configured=false" \
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

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
bash "$ROOT_DIR/tools/verify_central_brain_native_runtime_apk.sh" "$RUNTIME_APK"

if [[ -z "$SERIAL" ]]; then
  mapfile -t ONLINE_DEVICES < <("$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ ${#ONLINE_DEVICES[@]} -ne 1 ]]; then
    echo "expected exactly one online adb device; use --serial when multiple exist" >&2
    "$ADB" devices -l >&2
    exit 1
  fi
  SERIAL="${ONLINE_DEVICES[0]}"
fi

ADB_DEVICE=("$ADB" -s "$SERIAL")
if [[ "$("${ADB_DEVICE[@]}" get-state | tr -d '\r')" != "device" ]]; then
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
UNAUTHORIZED_DEBUG_SIMULATION_OUTPUT="$("${ADB_DEVICE[@]}" shell am startservice \
  -a com.centralbrain.runtime.action.BIND_DEBUG_SIMULATION_CONTROLLER \
  -n com.centralbrain.runtime/.simulation.DebugSimulationControllerService 2>&1)"
UNAUTHORIZED_DEBUG_SIMULATION_STATUS=$?
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
if [[ $UNAUTHORIZED_DEBUG_SIMULATION_STATUS -eq 0 ]] \
    || ! grep -Fq \
      "Requires permission com.centralbrain.permission.CONTROL_DEBUG_SIMULATION" \
      <<<"$UNAUTHORIZED_DEBUG_SIMULATION_OUTPUT"; then
  echo "shell caller was not rejected by the debug simulation signature permission" >&2
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
        <<<"$DIAGNOSTIC_LOG" \
      && grep -Fq "skill_governance_readiness_diagnostic_verified=true" \
        <<<"$DIAGNOSTIC_LOG" \
      && grep -Fq "runtime_acceptance_diagnostic_verified=true" \
        <<<"$DIAGNOSTIC_LOG" \
      && grep -Fq "native_runtime_diagnostic_verified=true" \
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
      && grep -Fq "room_migration_3_4_verified=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "room_schema_version=4" <<<"$MIGRATION_LOG" \
      && grep -Fq "room_table_count=13" <<<"$MIGRATION_LOG" \
      && grep -Fq "room_wal_enabled=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "legacy_task_preserved=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "legacy_approval_preserved=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "legacy_event_cursor_preserved=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "legacy_runtime_session_preserved=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "legacy_session_v1_exposure_blocked=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "event_cursor_schema_v3_verified=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "event_cursor_schema_ready=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "room_schema_v4_verified=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "room_v4_foreign_keys_verified=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "room_v4_query_index_verified=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "room_v4_crash_transaction_rollback_verified=true" \
          <<<"$MIGRATION_LOG" \
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

SKILL_RUNTIME_NONCE="$(date +%s%N)"
SKILL_RUNTIME_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.skills.BuiltInSkillRuntimeProbeActivity \
  --es nonce "$SKILL_RUNTIME_NONCE")"
if ! grep -Fq "Status: ok" <<<"$SKILL_RUNTIME_OUTPUT"; then
  echo "$SKILL_RUNTIME_OUTPUT" >&2
  echo "built-in Skill runtime debug probe did not start successfully" >&2
  exit 1
fi
SKILL_RUNTIME_PASSED=false
for _ in {1..40}; do
  SKILL_RUNTIME_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbBuiltInSkill:I)"
  if grep -Fq "nonce=$SKILL_RUNTIME_NONCE skill_runtime_probe_complete=true" \
      <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_runtime_contract_verified=true" <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_catalog_verified=true" <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_signer_allowlist_verified=true" <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_manifest_schema_verified=true" <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_invocation_idempotency_verified=true" \
        <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_capability_policy_verified=true" <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_safety_state_verified=true" <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_owner_isolation_verified=true" <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_cancel_idempotency_verified=true" \
        <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_record_bounds_verified=true" <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_process_only=true" <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_manifest_signer_evidence_compile_time_only=true" \
        <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_dynamic_loading_enabled=false" <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_cryptographic_artifact_verification_performed=false" \
        <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_production_service_wired=false" <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "raw_skill_input_stored=false" <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "skill_network_access_enabled=false" <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "service_dispatch_triggered=false" <<<"$SKILL_RUNTIME_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$SKILL_RUNTIME_LOG"; then
    SKILL_RUNTIME_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$SKILL_RUNTIME_PASSED" != true ]]; then
  echo "$SKILL_RUNTIME_LOG" >&2
  echo "built-in Skill runtime probe did not pass" >&2
  exit 1
fi

GOVERNANCE_MIDDLEWARE_NONCE="$(date +%s%N)"
GOVERNANCE_MIDDLEWARE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.governance.GovernanceMiddlewareProbeActivity \
  --es nonce "$GOVERNANCE_MIDDLEWARE_NONCE")"
if ! grep -Fq "Status: ok" <<<"$GOVERNANCE_MIDDLEWARE_OUTPUT"; then
  echo "$GOVERNANCE_MIDDLEWARE_OUTPUT" >&2
  echo "governance middleware debug probe did not start successfully" >&2
  exit 1
fi
GOVERNANCE_MIDDLEWARE_PASSED=false
for _ in {1..40}; do
  GOVERNANCE_MIDDLEWARE_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbGovMiddleware:I)"
  if grep -Fq \
      "nonce=$GOVERNANCE_MIDDLEWARE_NONCE governance_middleware_probe_complete=true" \
      <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_middleware_contract_verified=true" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_middleware_order_verified=true" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_middleware_allow_path_verified=true" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_middleware_first_rejection_verified=true" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_middleware_audit_finalizer_verified=true" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_middleware_privacy_verified=true" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_middleware_policy_verified=true" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_middleware_qos_verified=true" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_middleware_output_guard_verified=true" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_middleware_audit_bounds_verified=true" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_middleware_process_only=true" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_middleware_production_wired=false" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_dispatch_execution_enabled=false" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_service_dispatch_triggered=false" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "raw_governance_input_stored=false" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "raw_governance_output_stored=false" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_audit_persistence_wired=false" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "governance_network_access_enabled=false" \
        <<<"$GOVERNANCE_MIDDLEWARE_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$GOVERNANCE_MIDDLEWARE_LOG"; then
    GOVERNANCE_MIDDLEWARE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$GOVERNANCE_MIDDLEWARE_PASSED" != true ]]; then
  echo "$GOVERNANCE_MIDDLEWARE_LOG" >&2
  echo "governance middleware probe did not pass" >&2
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

MODEL_CONTRACT_V2_NONCE="$(date +%s%N)"
MODEL_CONTRACT_V2_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.model.ModelContractV2ProbeActivity \
  --es nonce "$MODEL_CONTRACT_V2_NONCE")"
if ! grep -Fq "Status: ok" <<<"$MODEL_CONTRACT_V2_PROBE_OUTPUT"; then
  echo "$MODEL_CONTRACT_V2_PROBE_OUTPUT" >&2
  echo "ModelRequest/Result v2 debug probe did not start successfully" >&2
  exit 1
fi
MODEL_CONTRACT_V2_PROBE_PASSED=false
for _ in {1..40}; do
  MODEL_CONTRACT_V2_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbModelV2Probe:I)"
  if grep -Fq \
      "nonce=$MODEL_CONTRACT_V2_NONCE model_contract_v2_probe_complete=true" \
      <<<"$MODEL_CONTRACT_V2_LOG" \
      && grep -Fq "model_contract_v2_verified=true" \
        <<<"$MODEL_CONTRACT_V2_LOG" \
      && grep -Fq "model_request_v2_fields_verified=true" \
        <<<"$MODEL_CONTRACT_V2_LOG" \
      && grep -Fq "model_result_v2_binding_verified=true" \
        <<<"$MODEL_CONTRACT_V2_LOG" \
      && grep -Fq "model_privacy_fallback_fail_closed=true" \
        <<<"$MODEL_CONTRACT_V2_LOG" \
      && grep -Fq "model_raw_content_accepted=false" \
        <<<"$MODEL_CONTRACT_V2_LOG" \
      && grep -Fq "model_provider_registry_wired=false" \
        <<<"$MODEL_CONTRACT_V2_LOG" \
      && grep -Fq "model_policy_router_wired=false" \
        <<<"$MODEL_CONTRACT_V2_LOG" \
      && grep -Fq "model_invoked=false" <<<"$MODEL_CONTRACT_V2_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$MODEL_CONTRACT_V2_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$MODEL_CONTRACT_V2_LOG" \
      && grep -Fq "production_ready=false" <<<"$MODEL_CONTRACT_V2_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$MODEL_CONTRACT_V2_LOG"; then
    MODEL_CONTRACT_V2_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$MODEL_CONTRACT_V2_PROBE_PASSED" != true ]]; then
  echo "$MODEL_CONTRACT_V2_LOG" >&2
  echo "ModelRequest/Result v2 probe did not pass" >&2
  exit 1
fi

MODEL_REGISTRY_NONCE="$(date +%s%N)"
MODEL_REGISTRY_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.model.ModelProviderRegistryProbeActivity \
  --es nonce "$MODEL_REGISTRY_NONCE")"
if ! grep -Fq "Status: ok" <<<"$MODEL_REGISTRY_PROBE_OUTPUT"; then
  echo "$MODEL_REGISTRY_PROBE_OUTPUT" >&2
  echo "Model Provider Registry debug probe did not start successfully" >&2
  exit 1
fi
MODEL_REGISTRY_PROBE_PASSED=false
for _ in {1..40}; do
  MODEL_REGISTRY_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbModelRegistry:I)"
  if grep -Fq \
      "nonce=$MODEL_REGISTRY_NONCE model_provider_registry_probe_complete=true" \
      <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "model_provider_registry_verified=true" \
        <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "model_provider_catalog_verified=true" \
        <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "model_provider_health_freshness_verified=true" \
        <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "model_provider_health_replay_verified=true" \
        <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "model_provider_availability_separation_verified=true" \
        <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "model_provider_placeholder_fail_closed=true" \
        <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "model_provider_count=4" <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "model_contract_test_available_count=1" \
        <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "model_development_available_count=1" \
        <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "model_production_ready_count=0" \
        <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "model_provider_registry_android13_arm64_verified=true" \
        <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "model_provider_registry_runtime_wired=false" \
        <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "model_policy_router_wired=false" \
        <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "model_invoked=false" <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "network_accessed=false" <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "production_ready=false" <<<"$MODEL_REGISTRY_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$MODEL_REGISTRY_LOG"; then
    MODEL_REGISTRY_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$MODEL_REGISTRY_PROBE_PASSED" != true ]]; then
  echo "$MODEL_REGISTRY_LOG" >&2
  echo "Model Provider Registry probe did not pass" >&2
  exit 1
fi

MODEL_POLICY_ROUTER_NONCE="$(date +%s%N)"
MODEL_POLICY_ROUTER_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.model.PolicyAwareModelRouterProbeActivity \
  --es nonce "$MODEL_POLICY_ROUTER_NONCE")"
if ! grep -Fq "Status: ok" <<<"$MODEL_POLICY_ROUTER_PROBE_OUTPUT"; then
  echo "$MODEL_POLICY_ROUTER_PROBE_OUTPUT" >&2
  echo "PolicyAwareModelRouter debug probe did not start successfully" >&2
  exit 1
fi
MODEL_POLICY_ROUTER_PROBE_PASSED=false
for _ in {1..40}; do
  MODEL_POLICY_ROUTER_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbModelRouter:I)"
  if grep -Fq \
      "nonce=$MODEL_POLICY_ROUTER_NONCE model_policy_router_probe_complete=true" \
      <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "model_policy_router_verified=true" \
        <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "model_policy_router_selection_verified=true" \
        <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "model_policy_router_privacy_network_thermal_verified=true" \
        <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "model_policy_router_quota_verified=true" \
        <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "model_policy_router_fallback_bounded=true" \
        <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "model_policy_router_android13_arm64_verified=true" \
        <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "model_policy_router_runtime_wired=false" \
        <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "action_authorization_granted=false" \
        <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "effect_dispatch_requested=false" \
        <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "provider_invoked=false" <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "model_invoked=false" <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "network_accessed=false" <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "production_ready=false" <<<"$MODEL_POLICY_ROUTER_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$MODEL_POLICY_ROUTER_LOG"; then
    MODEL_POLICY_ROUTER_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$MODEL_POLICY_ROUTER_PROBE_PASSED" != true ]]; then
  echo "$MODEL_POLICY_ROUTER_LOG" >&2
  echo "PolicyAwareModelRouter probe did not pass" >&2
  exit 1
fi

LOCAL_MODEL_PROVIDER_NONCE="$(date +%s%N)"
LOCAL_MODEL_PROVIDER_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.model.LocalModelProviderProbeActivity \
  --es nonce "$LOCAL_MODEL_PROVIDER_NONCE")"
if ! grep -Fq "Status: ok" <<<"$LOCAL_MODEL_PROVIDER_PROBE_OUTPUT"; then
  echo "$LOCAL_MODEL_PROVIDER_PROBE_OUTPUT" >&2
  echo "LocalModelProvider debug probe did not start successfully" >&2
  exit 1
fi
LOCAL_MODEL_PROVIDER_PROBE_PASSED=false
for _ in {1..40}; do
  LOCAL_MODEL_PROVIDER_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbLocalProvider:I)"
  if grep -Fq \
      "nonce=$LOCAL_MODEL_PROVIDER_NONCE local_model_provider_probe_complete=true" \
      <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "local_model_provider_verified=true" \
        <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "local_model_provider_lifecycle_verified=true" \
        <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "local_model_provider_stream_limit_verified=true" \
        <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "local_model_provider_cancel_verified=true" \
        <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "local_model_provider_deadline_verified=true" \
        <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "local_model_provider_overflow_rejected=true" \
        <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "local_model_provider_profile_boundary_verified=true" \
        <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "local_model_provider_registry_boundary_verified=true" \
        <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "local_model_provider_debug_only=true" \
        <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "local_model_provider_release_source_absent=true" \
        <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "local_model_provider_runtime_wired=false" \
        <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "local_model_provider_vendor_npu_fallback_enabled=false" \
        <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "production_inference_enabled=false" \
        <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "raw_model_content_logged=false" \
        <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "network_accessed=false" <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "production_ready=false" <<<"$LOCAL_MODEL_PROVIDER_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$LOCAL_MODEL_PROVIDER_LOG"; then
    LOCAL_MODEL_PROVIDER_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$LOCAL_MODEL_PROVIDER_PROBE_PASSED" != true ]]; then
  echo "$LOCAL_MODEL_PROVIDER_LOG" >&2
  echo "LocalModelProvider probe did not pass" >&2
  exit 1
fi

STRUCTURED_MODEL_OUTPUT_NONCE="$(date +%s%N)"
STRUCTURED_MODEL_OUTPUT_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.model.StructuredModelOutputProbeActivity \
  --es nonce "$STRUCTURED_MODEL_OUTPUT_NONCE")"
if ! grep -Fq "Status: ok" <<<"$STRUCTURED_MODEL_OUTPUT_PROBE_OUTPUT"; then
  echo "$STRUCTURED_MODEL_OUTPUT_PROBE_OUTPUT" >&2
  echo "StructuredModelOutput debug probe did not start successfully" >&2
  exit 1
fi
STRUCTURED_MODEL_OUTPUT_PROBE_PASSED=false
for _ in {1..40}; do
  STRUCTURED_MODEL_OUTPUT_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbModelSchemaProbe:I)"
  if grep -Fq \
      "nonce=$STRUCTURED_MODEL_OUTPUT_NONCE structured_model_output_probe_complete=true" \
      <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "structured_model_output_verified=true" \
        <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "model_output_catalog_binding_verified=true" \
        <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "model_output_unknown_capability_rejected=true" \
        <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "security_boundary_probe_complete=true" \
        <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "model_output_unknown_field_rejected=true" \
        <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "model_output_path_like_identifier_rejected=true" \
        <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "model_output_oversize_rejected=true" \
        <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "session_request_oversize_rejected=true" \
        <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "security_android_debug_probe_available=true" \
        <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "security_android_debug_probe_executed=true" \
        <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "model_output_no_action_authority=true" \
        <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "model_output_schema_runtime_wired=false" \
        <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "model_invoked=false" <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "network_accessed=false" <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "production_ready=false" <<<"$STRUCTURED_MODEL_OUTPUT_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$STRUCTURED_MODEL_OUTPUT_LOG"; then
    STRUCTURED_MODEL_OUTPUT_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$STRUCTURED_MODEL_OUTPUT_PROBE_PASSED" != true ]]; then
  echo "$STRUCTURED_MODEL_OUTPUT_LOG" >&2
  echo "StructuredModelOutput probe did not pass" >&2
  exit 1
fi

SCENARIO_EVALUATION_NONCE="$(date +%s%N)"
SCENARIO_EVALUATION_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.model.ScenarioEvaluationHarnessProbeActivity \
  --es nonce "$SCENARIO_EVALUATION_NONCE")"
if ! grep -Fq "Status: ok" <<<"$SCENARIO_EVALUATION_PROBE_OUTPUT"; then
  echo "$SCENARIO_EVALUATION_PROBE_OUTPUT" >&2
  echo "ScenarioEvaluationHarness debug probe did not start successfully" >&2
  exit 1
fi
SCENARIO_EVALUATION_PROBE_PASSED=false
for _ in {1..40}; do
  SCENARIO_EVALUATION_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbModelEvalProbe:I)"
  if grep -Fq \
      "nonce=$SCENARIO_EVALUATION_NONCE scenario_evaluation_probe_complete=true" \
      <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "scenario_evaluation_verified=true" \
        <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "evaluation_corpus_verified=true" \
        <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "evaluation_metrics_verified=true" \
        <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "evaluation_boundary_verified=true" \
        <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "evaluation_case_count=12" \
        <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "intent_accuracy_permille=1000" \
        <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "unsafe_proposal_rate_permille=0" \
        <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "invalid_schema_rate_permille=0" \
        <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "fallback_rate_permille=0" \
        <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "scenario_evaluation_runtime_wired=false" \
        <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "raw_evaluation_content_logged=false" \
        <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "model_invoked=false" <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "network_accessed=false" <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "production_ready=false" <<<"$SCENARIO_EVALUATION_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$SCENARIO_EVALUATION_LOG"; then
    SCENARIO_EVALUATION_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$SCENARIO_EVALUATION_PROBE_PASSED" != true ]]; then
  echo "$SCENARIO_EVALUATION_LOG" >&2
  echo "ScenarioEvaluationHarness probe did not pass" >&2
  exit 1
fi

RESOURCE_ADMISSION_NONCE="$(date +%s%N)"
RESOURCE_ADMISSION_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.scheduler.ModelResourceAdmissionProbeActivity \
  --es nonce "$RESOURCE_ADMISSION_NONCE")"
if ! grep -Fq "Status: ok" <<<"$RESOURCE_ADMISSION_PROBE_OUTPUT"; then
  echo "$RESOURCE_ADMISSION_PROBE_OUTPUT" >&2
  echo "model resource admission debug probe did not start successfully" >&2
  exit 1
fi
RESOURCE_ADMISSION_PROBE_PASSED=false
for _ in {1..40}; do
  RESOURCE_ADMISSION_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbResourceProbe:I)"
  if grep -Fq \
      "nonce=$RESOURCE_ADMISSION_NONCE resource_admission_probe_complete=true" \
      <<<"$RESOURCE_ADMISSION_LOG" \
      && grep -Fq "model_resource_admission_verified=true" \
        <<<"$RESOURCE_ADMISSION_LOG" \
      && grep -Fq "foreground_vehicle_priority_verified=true" \
        <<<"$RESOURCE_ADMISSION_LOG" \
      && grep -Fq "thermal_degradation_verified=true" \
        <<<"$RESOURCE_ADMISSION_LOG" \
      && grep -Fq "thermal_resource_fail_closed_verified=true" \
        <<<"$RESOURCE_ADMISSION_LOG" \
      && grep -Fq "admission_boundary_verified=true" \
        <<<"$RESOURCE_ADMISSION_LOG" \
      && grep -Fq "resource_admission_runtime_wired=false" \
        <<<"$RESOURCE_ADMISSION_LOG" \
      && grep -Fq "provider_invoked=false" <<<"$RESOURCE_ADMISSION_LOG" \
      && grep -Fq "model_invoked=false" <<<"$RESOURCE_ADMISSION_LOG" \
      && grep -Fq "action_authorization_granted=false" \
        <<<"$RESOURCE_ADMISSION_LOG" \
      && grep -Fq "effect_dispatch_requested=false" \
        <<<"$RESOURCE_ADMISSION_LOG" \
      && grep -Fq "network_accessed=false" <<<"$RESOURCE_ADMISSION_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$RESOURCE_ADMISSION_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$RESOURCE_ADMISSION_LOG" \
      && grep -Fq "production_ready=false" <<<"$RESOURCE_ADMISSION_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$RESOURCE_ADMISSION_LOG"; then
    RESOURCE_ADMISSION_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$RESOURCE_ADMISSION_PROBE_PASSED" != true ]]; then
  echo "$RESOURCE_ADMISSION_LOG" >&2
  echo "model resource admission probe did not pass" >&2
  exit 1
fi

PERFORMANCE_BUDGET_NONCE="$(date +%s%N)"
PERFORMANCE_BUDGET_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.performance.PerformanceBudgetProbeActivity \
  --es nonce "$PERFORMANCE_BUDGET_NONCE")"
if ! grep -Fq "Status: ok" <<<"$PERFORMANCE_BUDGET_PROBE_OUTPUT"; then
  echo "$PERFORMANCE_BUDGET_PROBE_OUTPUT" >&2
  echo "performance budget debug probe did not start successfully" >&2
  exit 1
fi
PERFORMANCE_BUDGET_PROBE_PASSED=false
for _ in {1..40}; do
  PERFORMANCE_BUDGET_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbPerfBudgetProbe:I)"
  if grep -Fq \
      "nonce=$PERFORMANCE_BUDGET_NONCE performance_budget_probe_complete=true" \
      <<<"$PERFORMANCE_BUDGET_LOG" \
      && grep -Fq "performance_budget_contract_verified=true" \
        <<<"$PERFORMANCE_BUDGET_LOG" \
      && grep -Fq "performance_budget_catalog_verified=true" \
        <<<"$PERFORMANCE_BUDGET_LOG" \
      && grep -Fq "performance_budget_report_validation_verified=true" \
        <<<"$PERFORMANCE_BUDGET_LOG" \
      && grep -Fq "performance_budget_boundary_verified=true" \
        <<<"$PERFORMANCE_BUDGET_LOG" \
      && grep -Fq "performance_budget_target_measurement_complete=false" \
        <<<"$PERFORMANCE_BUDGET_LOG" \
      && grep -Fq "performance_budget_runtime_wired=false" \
        <<<"$PERFORMANCE_BUDGET_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$PERFORMANCE_BUDGET_LOG" \
      && grep -Fq "production_ready=false" <<<"$PERFORMANCE_BUDGET_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$PERFORMANCE_BUDGET_LOG"; then
    PERFORMANCE_BUDGET_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$PERFORMANCE_BUDGET_PROBE_PASSED" != true ]]; then
  echo "$PERFORMANCE_BUDGET_LOG" >&2
  echo "performance budget probe did not pass" >&2
  exit 1
fi

STABILITY_MATRIX_NONCE="$(date +%s%N)"
STABILITY_MATRIX_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.reliability.StabilityFaultMatrixProbeActivity \
  --es nonce "$STABILITY_MATRIX_NONCE")"
if ! grep -Fq "Status: ok" <<<"$STABILITY_MATRIX_PROBE_OUTPUT"; then
  echo "$STABILITY_MATRIX_PROBE_OUTPUT" >&2
  echo "stability fault matrix debug probe did not start successfully" >&2
  exit 1
fi
STABILITY_MATRIX_PROBE_PASSED=false
for _ in {1..40}; do
  STABILITY_MATRIX_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbStabilityProbe:I)"
  if grep -Fq \
      "nonce=$STABILITY_MATRIX_NONCE stability_fault_matrix_probe_complete=true" \
      <<<"$STABILITY_MATRIX_LOG" \
      && grep -Fq "stability_fault_matrix_contract_verified=true" \
        <<<"$STABILITY_MATRIX_LOG" \
      && grep -Fq "stability_matrix_verified=true" <<<"$STABILITY_MATRIX_LOG" \
      && grep -Fq "stability_report_validation_verified=true" \
        <<<"$STABILITY_MATRIX_LOG" \
      && grep -Fq "stability_boundary_verified=true" \
        <<<"$STABILITY_MATRIX_LOG" \
      && grep -Fq "stability_target_72h_complete=false" \
        <<<"$STABILITY_MATRIX_LOG" \
      && grep -Fq "stability_fault_injection_runtime_wired=false" \
        <<<"$STABILITY_MATRIX_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$STABILITY_MATRIX_LOG" \
      && grep -Fq "production_ready=false" <<<"$STABILITY_MATRIX_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$STABILITY_MATRIX_LOG"; then
    STABILITY_MATRIX_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$STABILITY_MATRIX_PROBE_PASSED" != true ]]; then
  echo "$STABILITY_MATRIX_LOG" >&2
  echo "stability fault matrix probe did not pass" >&2
  exit 1
fi

PRIVACY_REDACTION_NONCE="$(date +%s%N)"
PRIVACY_REDACTION_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.privacy.PrivacyRedactionAuditProbeActivity \
  --es nonce "$PRIVACY_REDACTION_NONCE")"
if ! grep -Fq "Status: ok" <<<"$PRIVACY_REDACTION_PROBE_OUTPUT"; then
  echo "$PRIVACY_REDACTION_PROBE_OUTPUT" >&2
  echo "privacy redaction audit debug probe did not start successfully" >&2
  exit 1
fi
PRIVACY_REDACTION_PROBE_PASSED=false
for _ in {1..40}; do
  PRIVACY_REDACTION_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbPrivacyProbe:I)"
  if grep -Fq \
      "nonce=$PRIVACY_REDACTION_NONCE privacy_redaction_probe_complete=true" \
      <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_redacted_audit_projection_verified=true" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Eq "privacy_inventory_digest=[0-9a-f]{64}" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Eq "privacy_policy_body_digest=[0-9a-f]{64}" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_surface_count=12" <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_unresolved_surface_count=2" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_admission_code_count=3" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_operation_code_count=1" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_current_policy_admitted=false" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_raw_user_text_logged=false" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_raw_model_output_logged=false" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_raw_vehicle_payload_logged=false" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_location_logged=false" <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_owner_reference_logged=false" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_authorization_digest_logged=false" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_consent_digest_logged=false" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_repository_mutation_wired=false" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_runtime_lifecycle_wiring_complete=false" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_android_debug_probe_available=true" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "privacy_android_debug_probe_executed=true" \
        <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "production_ready=false" <<<"$PRIVACY_REDACTION_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$PRIVACY_REDACTION_LOG"; then
    PRIVACY_REDACTION_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$PRIVACY_REDACTION_PROBE_PASSED" != true ]]; then
  echo "$PRIVACY_REDACTION_LOG" >&2
  echo "privacy redaction audit probe did not pass" >&2
  exit 1
fi

ADB="$ADB" "$ROOT_DIR/tools/probe_central_brain_android_release_metadata.sh" \
  --serial "$SERIAL"

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

VEHICLE_SIGNAL_NONCE="$(date +%s%N)"
VEHICLE_SIGNAL_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.vehicle.schema.VehicleSignalSchemaProbeActivity \
  --es nonce "$VEHICLE_SIGNAL_NONCE")"
if ! grep -Fq "Status: ok" <<<"$VEHICLE_SIGNAL_PROBE_OUTPUT"; then
  echo "$VEHICLE_SIGNAL_PROBE_OUTPUT" >&2
  echo "vehicle signal schema debug probe did not start successfully" >&2
  exit 1
fi
VEHICLE_SIGNAL_PROBE_PASSED=false
for _ in {1..40}; do
  VEHICLE_SIGNAL_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbVehicleSignal:I)"
  if grep -Fq \
      "nonce=$VEHICLE_SIGNAL_NONCE vehicle_signal_schema_probe_complete=true" \
      <<<"$VEHICLE_SIGNAL_LOG" \
      && grep -Fq "vehicle_signal_schema_verified=true" \
        <<<"$VEHICLE_SIGNAL_LOG" \
      && grep -Fq "vehicle_signal_path_allowlist_verified=true" \
        <<<"$VEHICLE_SIGNAL_LOG" \
      && grep -Fq "vehicle_signal_typed_scalar_verified=true" \
        <<<"$VEHICLE_SIGNAL_LOG" \
      && grep -Fq "vehicle_signal_unit_area_verified=true" \
        <<<"$VEHICLE_SIGNAL_LOG" \
      && grep -Fq "vehicle_signal_freshness_quality_verified=true" \
        <<<"$VEHICLE_SIGNAL_LOG" \
      && grep -Fq "vehicle_signal_schema_android13_arm64_verified=true" \
        <<<"$VEHICLE_SIGNAL_LOG" \
      && grep -Fq "vehicle_signal_provider_wired=false" \
        <<<"$VEHICLE_SIGNAL_LOG" \
      && grep -Fq "vehicle_property_mapping_configured=false" \
        <<<"$VEHICLE_SIGNAL_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$VEHICLE_SIGNAL_LOG"; then
    VEHICLE_SIGNAL_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$VEHICLE_SIGNAL_PROBE_PASSED" != true ]]; then
  echo "$VEHICLE_SIGNAL_LOG" >&2
  echo "vehicle signal schema probe did not pass" >&2
  exit 1
fi

VEHICLE_CATALOG_NONCE="$(date +%s%N)"
VEHICLE_CATALOG_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.vehicle.capability.VehicleCapabilityCatalogProbeActivity \
  --es nonce "$VEHICLE_CATALOG_NONCE")"
if ! grep -Fq "Status: ok" <<<"$VEHICLE_CATALOG_PROBE_OUTPUT"; then
  echo "$VEHICLE_CATALOG_PROBE_OUTPUT" >&2
  echo "vehicle capability catalog debug probe did not start successfully" >&2
  exit 1
fi
VEHICLE_CATALOG_PROBE_PASSED=false
for _ in {1..40}; do
  VEHICLE_CATALOG_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbVehicleCatalog:I)"
  if grep -Fq \
      "nonce=$VEHICLE_CATALOG_NONCE vehicle_capability_catalog_probe_complete=true" \
      <<<"$VEHICLE_CATALOG_LOG" \
      && grep -Fq "vehicle_capability_catalog_verified=true" \
        <<<"$VEHICLE_CATALOG_LOG" \
      && grep -Fq "vehicle_capability_count=8" <<<"$VEHICLE_CATALOG_LOG" \
      && grep -Fq "vehicle_capability_target_ranges_verified=true" \
        <<<"$VEHICLE_CATALOG_LOG" \
      && grep -Fq "vehicle_production_authorization_fail_closed_verified=true" \
        <<<"$VEHICLE_CATALOG_LOG" \
      && grep -Fq "vehicle_signal_dependency_mapping_verified=true" \
        <<<"$VEHICLE_CATALOG_LOG" \
      && grep -Fq "vehicle_capability_catalog_android13_arm64_verified=true" \
        <<<"$VEHICLE_CATALOG_LOG" \
      && grep -Fq "vehicle_production_capability_authorized_count=0" \
        <<<"$VEHICLE_CATALOG_LOG" \
      && grep -Fq "vehicle_capability_adapter_registry_wired=false" \
        <<<"$VEHICLE_CATALOG_LOG" \
      && grep -Fq "vehicle_property_mapping_configured=false" \
        <<<"$VEHICLE_CATALOG_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$VEHICLE_CATALOG_LOG"; then
    VEHICLE_CATALOG_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$VEHICLE_CATALOG_PROBE_PASSED" != true ]]; then
  echo "$VEHICLE_CATALOG_LOG" >&2
  echo "vehicle capability catalog probe did not pass" >&2
  exit 1
fi

VEHICLE_TWIN_NONCE="$(date +%s%N)"
VEHICLE_TWIN_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.vehicle.twin.VehicleDigitalTwinStoreProbeActivity \
  --es nonce "$VEHICLE_TWIN_NONCE")"
if ! grep -Fq "Status: ok" <<<"$VEHICLE_TWIN_PROBE_OUTPUT"; then
  echo "$VEHICLE_TWIN_PROBE_OUTPUT" >&2
  echo "vehicle Digital Twin debug probe did not start successfully" >&2
  exit 1
fi
VEHICLE_TWIN_PROBE_PASSED=false
for _ in {1..40}; do
  VEHICLE_TWIN_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbVehicleTwin:I)"
  if grep -Fq \
      "nonce=$VEHICLE_TWIN_NONCE vehicle_digital_twin_probe_complete=true" \
      <<<"$VEHICLE_TWIN_LOG" \
      && grep -Fq "vehicle_digital_twin_store_verified=true" \
        <<<"$VEHICLE_TWIN_LOG" \
      && grep -Fq "vehicle_digital_twin_desired_reported_separation_verified=true" \
        <<<"$VEHICLE_TWIN_LOG" \
      && grep -Fq "vehicle_digital_twin_monotonic_revision_verified=true" \
        <<<"$VEHICLE_TWIN_LOG" \
      && grep -Fq "vehicle_digital_twin_ttl_quality_verified=true" \
        <<<"$VEHICLE_TWIN_LOG" \
      && grep -Fq "vehicle_digital_twin_atomic_snapshot_verified=true" \
        <<<"$VEHICLE_TWIN_LOG" \
      && grep -Fq "vehicle_digital_twin_stale_report_rejected=true" \
        <<<"$VEHICLE_TWIN_LOG" \
      && grep -Fq "vehicle_digital_twin_reconciliation_verified=true" \
        <<<"$VEHICLE_TWIN_LOG" \
      && grep -Fq "vehicle_digital_twin_android13_arm64_verified=true" \
        <<<"$VEHICLE_TWIN_LOG" \
      && grep -Fq "vehicle_digital_twin_persistence_wired=false" \
        <<<"$VEHICLE_TWIN_LOG" \
      && grep -Fq "vehicle_capability_adapter_registry_wired=false" \
        <<<"$VEHICLE_TWIN_LOG" \
      && grep -Fq "vehicle_property_mapping_configured=false" \
        <<<"$VEHICLE_TWIN_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$VEHICLE_TWIN_LOG"; then
    VEHICLE_TWIN_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$VEHICLE_TWIN_PROBE_PASSED" != true ]]; then
  echo "$VEHICLE_TWIN_LOG" >&2
  echo "vehicle Digital Twin probe did not pass" >&2
  exit 1
fi

CONTEXT_SNAPSHOT_NONCE="$(date +%s%N)"
CONTEXT_SNAPSHOT_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.context.ContextSnapshotBuilderProbeActivity \
  --es nonce "$CONTEXT_SNAPSHOT_NONCE")"
if ! grep -Fq "Status: ok" <<<"$CONTEXT_SNAPSHOT_PROBE_OUTPUT"; then
  echo "$CONTEXT_SNAPSHOT_PROBE_OUTPUT" >&2
  echo "Context snapshot debug probe did not start successfully" >&2
  exit 1
fi
CONTEXT_SNAPSHOT_PROBE_PASSED=false
for _ in {1..40}; do
  CONTEXT_SNAPSHOT_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbContextSnapshot:I)"
  if grep -Fq \
      "nonce=$CONTEXT_SNAPSHOT_NONCE context_snapshot_probe_complete=true" \
      <<<"$CONTEXT_SNAPSHOT_LOG" \
      && grep -Fq "context_snapshot_builder_verified=true" \
        <<<"$CONTEXT_SNAPSHOT_LOG" \
      && grep -Fq "context_snapshot_version_digest_verified=true" \
        <<<"$CONTEXT_SNAPSHOT_LOG" \
      && grep -Fq "context_snapshot_required_field_policy_verified=true" \
        <<<"$CONTEXT_SNAPSHOT_LOG" \
      && grep -Fq "context_snapshot_freshness_report_verified=true" \
        <<<"$CONTEXT_SNAPSHOT_LOG" \
      && grep -Fq "context_snapshot_driving_state_verified=true" \
        <<<"$CONTEXT_SNAPSHOT_LOG" \
      && grep -Fq "context_snapshot_restricted_fail_closed_verified=true" \
        <<<"$CONTEXT_SNAPSHOT_LOG" \
      && grep -Fq "context_snapshot_source_trust_verified=true" \
        <<<"$CONTEXT_SNAPSHOT_LOG" \
      && grep -Fq "context_snapshot_android13_arm64_verified=true" \
        <<<"$CONTEXT_SNAPSHOT_LOG" \
      && grep -Fq "context_snapshot_production_trusted=false" \
        <<<"$CONTEXT_SNAPSHOT_LOG" \
      && grep -Fq "context_snapshot_production_wired=false" \
        <<<"$CONTEXT_SNAPSHOT_LOG" \
      && grep -Fq "vehicle_signal_provider_wired=false" \
        <<<"$CONTEXT_SNAPSHOT_LOG" \
      && grep -Fq "vehicle_capability_adapter_registry_wired=false" \
        <<<"$CONTEXT_SNAPSHOT_LOG" \
      && grep -Fq "vehicle_property_mapping_configured=false" \
        <<<"$CONTEXT_SNAPSHOT_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$CONTEXT_SNAPSHOT_LOG"; then
    CONTEXT_SNAPSHOT_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$CONTEXT_SNAPSHOT_PROBE_PASSED" != true ]]; then
  echo "$CONTEXT_SNAPSHOT_LOG" >&2
  echo "Context snapshot probe did not pass" >&2
  exit 1
fi

SCENARIO_MANIFEST_NONCE="$(date +%s%N)"
SCENARIO_MANIFEST_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.scenario.ScenarioManifestProbeActivity \
  --es nonce "$SCENARIO_MANIFEST_NONCE")"
if ! grep -Fq "Status: ok" <<<"$SCENARIO_MANIFEST_PROBE_OUTPUT"; then
  echo "$SCENARIO_MANIFEST_PROBE_OUTPUT" >&2
  echo "Scenario manifest debug probe did not start successfully" >&2
  exit 1
fi
SCENARIO_MANIFEST_PROBE_PASSED=false
for _ in {1..40}; do
  SCENARIO_MANIFEST_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbScenarioManifest:I)"
  if grep -Fq \
      "nonce=$SCENARIO_MANIFEST_NONCE scenario_manifest_probe_complete=true" \
      <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_manifest_parser_verified=true" \
        <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_manifest_schema_version_verified=true" \
        <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_catalog_count=3" <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_catalog_digest_verified=true" \
        <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_manifest_artifact_digest_verified=true" \
        <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_manifest_fatigue_policy_verified=true" \
        <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_manifest_unknown_field_rejected=true" \
        <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_manifest_oversize_rejected=true" \
        <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_catalog_duplicate_id_rejected=true" \
        <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_manifest_invalid_dag_rejected=true" \
        <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_catalog_isolation_verified=true" \
        <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_manifest_android13_arm64_verified=true" \
        <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_manifest_artifact_crypto_verified=false" \
        <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_catalog_production_trusted=false" \
        <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_runtime_wired=false" <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "scenario_graph_execution_enabled=false" \
        <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "vehicle_signal_provider_wired=false" \
        <<<"$SCENARIO_MANIFEST_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$SCENARIO_MANIFEST_LOG"; then
    SCENARIO_MANIFEST_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$SCENARIO_MANIFEST_PROBE_PASSED" != true ]]; then
  echo "$SCENARIO_MANIFEST_LOG" >&2
  echo "Scenario manifest probe did not pass" >&2
  exit 1
fi

SCENARIO_RESOLVER_NONCE="$(date +%s%N)"
SCENARIO_RESOLVER_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.scenario.ScenarioResolverProbeActivity \
  --es nonce "$SCENARIO_RESOLVER_NONCE")"
if ! grep -Fq "Status: ok" <<<"$SCENARIO_RESOLVER_PROBE_OUTPUT"; then
  echo "$SCENARIO_RESOLVER_PROBE_OUTPUT" >&2
  echo "Scenario resolver debug probe did not start successfully" >&2
  exit 1
fi
SCENARIO_RESOLVER_PROBE_PASSED=false
for _ in {1..40}; do
  SCENARIO_RESOLVER_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbScenarioResolver:I)"
  if grep -Fq \
      "nonce=$SCENARIO_RESOLVER_NONCE scenario_resolver_probe_complete=true" \
      <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "scenario_resolver_defined=true" <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "scenario_resolver_explicit_verified=true" \
        <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "scenario_resolver_cold_verified=true" \
        <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "scenario_resolver_fatigue_verified=true" \
        <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "scenario_resolver_rest_verified=true" \
        <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "scenario_resolver_unknown_intent_rejected=true" \
        <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "scenario_resolver_ambiguous_intent_rejected=true" \
        <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "scenario_resolver_capability_policy_verified=true" \
        <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "scenario_resolver_production_fail_closed=true" \
        <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "scenario_resolution_digest_verified=true" \
        <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "scenario_resolver_android13_arm64_verified=true" \
        <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "scenario_resolver_model_invoked=false" \
        <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "scenario_resolver_runtime_wired=false" \
        <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "scenario_compiler_wired=false" \
        <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "scenario_graph_execution_enabled=false" \
        <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "vehicle_signal_provider_wired=false" \
        <<<"$SCENARIO_RESOLVER_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$SCENARIO_RESOLVER_LOG"; then
    SCENARIO_RESOLVER_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$SCENARIO_RESOLVER_PROBE_PASSED" != true ]]; then
  echo "$SCENARIO_RESOLVER_LOG" >&2
  echo "Scenario resolver probe did not pass" >&2
  exit 1
fi

SCENARIO_COMPILER_NONCE="$(date +%s%N)"
SCENARIO_COMPILER_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.scenario.ScenarioPlanCompilerProbeActivity \
  --es nonce "$SCENARIO_COMPILER_NONCE")"
if ! grep -Fq "Status: ok" <<<"$SCENARIO_COMPILER_PROBE_OUTPUT"; then
  echo "$SCENARIO_COMPILER_PROBE_OUTPUT" >&2
  echo "Scenario plan compiler debug probe did not start successfully" >&2
  exit 1
fi
SCENARIO_COMPILER_PROBE_PASSED=false
for _ in {1..40}; do
  SCENARIO_COMPILER_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbScenarioCompiler:I)"
  if grep -Fq \
      "nonce=$SCENARIO_COMPILER_NONCE scenario_plan_compiler_probe_complete=true" \
      <<<"$SCENARIO_COMPILER_LOG" \
      && grep -Fq "scenario_plan_compiler_defined=true" \
        <<<"$SCENARIO_COMPILER_LOG" \
      && grep -Fq "scenario_plan_golden_verified=true" \
        <<<"$SCENARIO_COMPILER_LOG" \
      && grep -Fq "scenario_plan_degraded_fallback_verified=true" \
        <<<"$SCENARIO_COMPILER_LOG" \
      && grep -Fq "scenario_plan_moving_seat_absent=true" \
        <<<"$SCENARIO_COMPILER_LOG" \
      && grep -Fq "scenario_plan_cycle_rejected=true" \
        <<<"$SCENARIO_COMPILER_LOG" \
      && grep -Fq "scenario_plan_digest_verified=true" \
        <<<"$SCENARIO_COMPILER_LOG" \
      && grep -Fq "scenario_plan_immutable_verified=true" \
        <<<"$SCENARIO_COMPILER_LOG" \
      && grep -Fq "scenario_plan_compiler_android13_arm64_verified=true" \
        <<<"$SCENARIO_COMPILER_LOG" \
      && grep -Fq "scenario_plan_compiler_runtime_wired=false" \
        <<<"$SCENARIO_COMPILER_LOG" \
      && grep -Fq "scenario_plan_runtime_published=false" \
        <<<"$SCENARIO_COMPILER_LOG" \
      && grep -Fq "scenario_graph_execution_enabled=false" \
        <<<"$SCENARIO_COMPILER_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" \
        <<<"$SCENARIO_COMPILER_LOG" \
      && grep -Fq "vehicle_signal_provider_wired=false" \
        <<<"$SCENARIO_COMPILER_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$SCENARIO_COMPILER_LOG"; then
    SCENARIO_COMPILER_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$SCENARIO_COMPILER_PROBE_PASSED" != true ]]; then
  echo "$SCENARIO_COMPILER_LOG" >&2
  echo "Scenario plan compiler probe did not pass" >&2
  exit 1
fi

AGENT_GRAPH_NONCE="$(date +%s%N)"
AGENT_GRAPH_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.graph.AgentGraphRuntimeProbeActivity \
  --es nonce "$AGENT_GRAPH_NONCE")"
if ! grep -Fq "Status: ok" <<<"$AGENT_GRAPH_PROBE_OUTPUT"; then
  echo "$AGENT_GRAPH_PROBE_OUTPUT" >&2
  echo "Agent Graph Runtime debug probe did not start successfully" >&2
  exit 1
fi
AGENT_GRAPH_PROBE_PASSED=false
for _ in {1..40}; do
  AGENT_GRAPH_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbAgentGraph:I)"
  if grep -Fq \
      "nonce=$AGENT_GRAPH_NONCE agent_graph_runtime_probe_complete=true" \
      <<<"$AGENT_GRAPH_LOG" \
      && grep -Fq "agent_graph_runtime_defined=true" <<<"$AGENT_GRAPH_LOG" \
      && grep -Fq "agent_graph_state_transition_verified=true" \
        <<<"$AGENT_GRAPH_LOG" \
      && grep -Fq "agent_graph_same_session_fifo_verified=true" \
        <<<"$AGENT_GRAPH_LOG" \
      && grep -Fq "agent_graph_cross_session_bounded_verified=true" \
        <<<"$AGENT_GRAPH_LOG" \
      && grep -Fq "agent_graph_partial_terminal_verified=true" \
        <<<"$AGENT_GRAPH_LOG" \
      && grep -Fq "agent_graph_deadline_verified=true" <<<"$AGENT_GRAPH_LOG" \
      && grep -Fq "agent_graph_compensation_fail_closed_verified=true" \
        <<<"$AGENT_GRAPH_LOG" \
      && grep -Fq "agent_graph_event_projection_bounded=true" \
        <<<"$AGENT_GRAPH_LOG" \
      && grep -Fq "agent_graph_android13_arm64_verified=true" \
        <<<"$AGENT_GRAPH_LOG" \
      && grep -Fq "agent_graph_executor_dispatch_enabled=false" \
        <<<"$AGENT_GRAPH_LOG" \
      && grep -Fq "agent_graph_runtime_production_wired=false" \
        <<<"$AGENT_GRAPH_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$AGENT_GRAPH_LOG" \
      && grep -Fq "model_invoked=false" <<<"$AGENT_GRAPH_LOG" \
      && grep -Fq "vehicle_signal_provider_wired=false" \
        <<<"$AGENT_GRAPH_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$AGENT_GRAPH_LOG"; then
    AGENT_GRAPH_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$AGENT_GRAPH_PROBE_PASSED" != true ]]; then
  echo "$AGENT_GRAPH_LOG" >&2
  echo "Agent Graph Runtime probe did not pass" >&2
  exit 1
fi

TYPED_NODE_EXECUTOR_NONCE="$(date +%s%N)"
TYPED_NODE_EXECUTOR_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.graph.TypedNodeExecutorsProbeActivity \
  --es nonce "$TYPED_NODE_EXECUTOR_NONCE")"
if ! grep -Fq "Status: ok" <<<"$TYPED_NODE_EXECUTOR_PROBE_OUTPUT"; then
  echo "$TYPED_NODE_EXECUTOR_PROBE_OUTPUT" >&2
  echo "Typed Node Executor debug probe did not start successfully" >&2
  exit 1
fi
TYPED_NODE_EXECUTOR_PROBE_PASSED=false
for _ in {1..40}; do
  TYPED_NODE_EXECUTOR_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbTypedNodeExec:I)"
  if grep -Fq \
      "nonce=$TYPED_NODE_EXECUTOR_NONCE typed_node_executor_probe_complete=true" \
      <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "typed_node_executor_contract_defined=true" \
        <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "typed_node_executor_schema_count=11" \
        <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "typed_node_executor_debug_count=7" \
        <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "typed_node_executor_exact_class_verified=true" \
        <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "typed_node_executor_context_verified=true" \
        <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "typed_node_executor_policy_approval_verified=true" \
        <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "typed_node_executor_effect_fail_closed_verified=true" \
        <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "typed_node_executor_verification_verified=true" \
        <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "typed_node_executor_summary_verified=true" \
        <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "typed_node_executor_unsupported_fail_closed_verified=true" \
        <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "typed_node_executor_android13_arm64_verified=true" \
        <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "typed_node_executor_graph_dispatch_enabled=false" \
        <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "typed_node_executor_production_wired=false" \
        <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" \
        <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "model_invoked=false" <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "network_accessed=false" <<<"$TYPED_NODE_EXECUTOR_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$TYPED_NODE_EXECUTOR_LOG"; then
    TYPED_NODE_EXECUTOR_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$TYPED_NODE_EXECUTOR_PROBE_PASSED" != true ]]; then
  echo "$TYPED_NODE_EXECUTOR_LOG" >&2
  echo "Typed Node Executor probe did not pass" >&2
  exit 1
fi

CHECKPOINT_SERIALIZER_NONCE="$(date +%s%N)"
CHECKPOINT_SERIALIZER_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.graph.CheckpointSerializerProbeActivity \
  --es nonce "$CHECKPOINT_SERIALIZER_NONCE")"
if ! grep -Fq "Status: ok" <<<"$CHECKPOINT_SERIALIZER_PROBE_OUTPUT"; then
  echo "$CHECKPOINT_SERIALIZER_PROBE_OUTPUT" >&2
  echo "Checkpoint Serializer debug probe did not start successfully" >&2
  exit 1
fi
CHECKPOINT_SERIALIZER_PROBE_PASSED=false
for _ in {1..40}; do
  CHECKPOINT_SERIALIZER_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbCheckpoint:I)"
  if grep -Fq \
      "nonce=$CHECKPOINT_SERIALIZER_NONCE checkpoint_serializer_probe_complete=true" \
      <<<"$CHECKPOINT_SERIALIZER_LOG" \
      && grep -Fq "checkpoint_serializer_defined=true" \
        <<<"$CHECKPOINT_SERIALIZER_LOG" \
      && grep -Fq "checkpoint_serializer_registered_dto_verified=true" \
        <<<"$CHECKPOINT_SERIALIZER_LOG" \
      && grep -Fq "checkpoint_serializer_canonical_digest_verified=true" \
        <<<"$CHECKPOINT_SERIALIZER_LOG" \
      && grep -Fq "checkpoint_serializer_malformed_unknown_rejected=true" \
        <<<"$CHECKPOINT_SERIALIZER_LOG" \
      && grep -Fq "checkpoint_serializer_size_depth_limit_verified=true" \
        <<<"$CHECKPOINT_SERIALIZER_LOG" \
      && grep -Fq "checkpoint_serializer_security_corpus_verified=true" \
        <<<"$CHECKPOINT_SERIALIZER_LOG" \
      && grep -Fq "checkpoint_serializer_android13_arm64_verified=true" \
        <<<"$CHECKPOINT_SERIALIZER_LOG" \
      && grep -Fq "checkpoint_serializer_java_serialization_enabled=false" \
        <<<"$CHECKPOINT_SERIALIZER_LOG" \
      && grep -Fq "agent_graph_runtime_persistence_wired=false" \
        <<<"$CHECKPOINT_SERIALIZER_LOG" \
      && grep -Fq "agent_graph_executor_dispatch_enabled=false" \
        <<<"$CHECKPOINT_SERIALIZER_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" \
        <<<"$CHECKPOINT_SERIALIZER_LOG" \
      && grep -Fq "model_invoked=false" <<<"$CHECKPOINT_SERIALIZER_LOG" \
      && grep -Fq "network_accessed=false" <<<"$CHECKPOINT_SERIALIZER_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$CHECKPOINT_SERIALIZER_LOG"; then
    CHECKPOINT_SERIALIZER_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$CHECKPOINT_SERIALIZER_PROBE_PASSED" != true ]]; then
  echo "$CHECKPOINT_SERIALIZER_LOG" >&2
  echo "Checkpoint Serializer probe did not pass" >&2
  exit 1
fi

RETRY_TIMEOUT_POLICY_NONCE="$(date +%s%N)"
RETRY_TIMEOUT_POLICY_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.graph.RetryTimeoutPolicyProbeActivity \
  --es nonce "$RETRY_TIMEOUT_POLICY_NONCE")"
if ! grep -Fq "Status: ok" <<<"$RETRY_TIMEOUT_POLICY_PROBE_OUTPUT"; then
  echo "$RETRY_TIMEOUT_POLICY_PROBE_OUTPUT" >&2
  echo "Retry/Timeout policy debug probe did not start successfully" >&2
  exit 1
fi
RETRY_TIMEOUT_POLICY_PROBE_PASSED=false
for _ in {1..40}; do
  RETRY_TIMEOUT_POLICY_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbRetryPolicy:I)"
  if grep -Fq \
      "nonce=$RETRY_TIMEOUT_POLICY_NONCE retry_timeout_policy_probe_complete=true" \
      <<<"$RETRY_TIMEOUT_POLICY_LOG" \
      && grep -Fq "node_retry_policy_defined=true" \
        <<<"$RETRY_TIMEOUT_POLICY_LOG" \
      && grep -Fq "node_timeout_policy_defined=true" \
        <<<"$RETRY_TIMEOUT_POLICY_LOG" \
      && grep -Fq "backoff_deterministic_bounded_verified=true" \
        <<<"$RETRY_TIMEOUT_POLICY_LOG" \
      && grep -Fq "timeout_deadline_clamp_verified=true" \
        <<<"$RETRY_TIMEOUT_POLICY_LOG" \
      && grep -Fq "retry_attempt_budget_verified=true" \
        <<<"$RETRY_TIMEOUT_POLICY_LOG" \
      && grep -Fq "effect_idempotency_reconcile_gate_verified=true" \
        <<<"$RETRY_TIMEOUT_POLICY_LOG" \
      && grep -Fq "retry_deadline_fail_closed_verified=true" \
        <<<"$RETRY_TIMEOUT_POLICY_LOG" \
      && grep -Fq "retry_timeout_policy_android13_arm64_verified=true" \
        <<<"$RETRY_TIMEOUT_POLICY_LOG" \
      && grep -Fq "retry_timeout_policy_runtime_wired=false" \
        <<<"$RETRY_TIMEOUT_POLICY_LOG" \
      && grep -Fq "agent_graph_executor_dispatch_enabled=false" \
        <<<"$RETRY_TIMEOUT_POLICY_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" \
        <<<"$RETRY_TIMEOUT_POLICY_LOG" \
      && grep -Fq "model_invoked=false" <<<"$RETRY_TIMEOUT_POLICY_LOG" \
      && grep -Fq "network_accessed=false" <<<"$RETRY_TIMEOUT_POLICY_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$RETRY_TIMEOUT_POLICY_LOG"; then
    RETRY_TIMEOUT_POLICY_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$RETRY_TIMEOUT_POLICY_PROBE_PASSED" != true ]]; then
  echo "$RETRY_TIMEOUT_POLICY_LOG" >&2
  echo "Retry/Timeout policy probe did not pass" >&2
  exit 1
fi

APPROVAL_INTERRUPT_NONCE="$(date +%s%N)"
APPROVAL_INTERRUPT_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.graph.ApprovalInterruptProbeActivity \
  --es nonce "$APPROVAL_INTERRUPT_NONCE")"
if ! grep -Fq "Status: ok" <<<"$APPROVAL_INTERRUPT_PROBE_OUTPUT"; then
  echo "$APPROVAL_INTERRUPT_PROBE_OUTPUT" >&2
  echo "Approval interrupt debug probe did not start successfully" >&2
  exit 1
fi
APPROVAL_INTERRUPT_PROBE_PASSED=false
for _ in {1..40}; do
  APPROVAL_INTERRUPT_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbApprovalInterrupt:I)"
  if grep -Fq \
      "nonce=$APPROVAL_INTERRUPT_NONCE approval_interrupt_probe_complete=true" \
      <<<"$APPROVAL_INTERRUPT_LOG" \
      && grep -Fq "approval_interrupt_record_defined=true" \
        <<<"$APPROVAL_INTERRUPT_LOG" \
      && grep -Fq "approval_interrupt_binding_verified=true" \
        <<<"$APPROVAL_INTERRUPT_LOG" \
      && grep -Fq "approval_interrupt_checkpoint_roundtrip_verified=true" \
        <<<"$APPROVAL_INTERRUPT_LOG" \
      && grep -Fq "approval_interrupt_trusted_decision_verified=true" \
        <<<"$APPROVAL_INTERRUPT_LOG" \
      && grep -Fq "approval_resume_owner_plan_context_policy_verified=true" \
        <<<"$APPROVAL_INTERRUPT_LOG" \
      && grep -Fq "approval_resume_safety_revalidation_verified=true" \
        <<<"$APPROVAL_INTERRUPT_LOG" \
      && grep -Fq "approval_resume_expiry_verified=true" \
        <<<"$APPROVAL_INTERRUPT_LOG" \
      && grep -Fq "approval_interrupt_android13_arm64_verified=true" \
        <<<"$APPROVAL_INTERRUPT_LOG" \
      && grep -Fq "approval_interrupt_persistence_wired=false" \
        <<<"$APPROVAL_INTERRUPT_LOG" \
      && grep -Fq "approval_grant_service_published=false" \
        <<<"$APPROVAL_INTERRUPT_LOG" \
      && grep -Fq "agent_graph_executor_dispatch_enabled=false" \
        <<<"$APPROVAL_INTERRUPT_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" \
        <<<"$APPROVAL_INTERRUPT_LOG" \
      && grep -Fq "model_invoked=false" <<<"$APPROVAL_INTERRUPT_LOG" \
      && grep -Fq "network_accessed=false" <<<"$APPROVAL_INTERRUPT_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$APPROVAL_INTERRUPT_LOG"; then
    APPROVAL_INTERRUPT_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$APPROVAL_INTERRUPT_PROBE_PASSED" != true ]]; then
  echo "$APPROVAL_INTERRUPT_LOG" >&2
  echo "Approval interrupt probe did not pass" >&2
  exit 1
fi

EFFECT_COORDINATOR_NONCE="$(date +%s%N)"
EFFECT_COORDINATOR_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.effects.EffectCoordinatorProbeActivity \
  --es nonce "$EFFECT_COORDINATOR_NONCE")"
if ! grep -Fq "Status: ok" <<<"$EFFECT_COORDINATOR_PROBE_OUTPUT"; then
  echo "$EFFECT_COORDINATOR_PROBE_OUTPUT" >&2
  echo "EffectCoordinator debug probe did not start successfully" >&2
  exit 1
fi
EFFECT_COORDINATOR_PROBE_PASSED=false
for _ in {1..40}; do
  EFFECT_COORDINATOR_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbEffectCoordinator:I)"
  if grep -Fq \
      "nonce=$EFFECT_COORDINATOR_NONCE effect_coordinator_probe_complete=true" \
      <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "effect_batch_defined=true" <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "effect_dependency_plan_verified=true" \
        <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "effect_resource_conflict_serialized=true" \
        <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "effect_adapter_registry_profile_isolation_verified=true" \
        <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "effect_prepare_all_required_verified=true" \
        <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "effect_optional_degradation_verified=true" \
        <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "effect_independent_observation_verified=true" \
        <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "effect_coordinator_android13_arm64_verified=true" \
        <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "effect_coordinator_graph_wired=false" \
        <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "effect_coordinator_persistence_wired=false" \
        <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "production_effect_adapter_registered=false" \
        <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "production_effect_dispatch_enabled=false" \
        <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "effect_verification_reconciliation_wired=false" \
        <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "model_invoked=false" <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "network_accessed=false" <<<"$EFFECT_COORDINATOR_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$EFFECT_COORDINATOR_LOG"; then
    EFFECT_COORDINATOR_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$EFFECT_COORDINATOR_PROBE_PASSED" != true ]]; then
  echo "$EFFECT_COORDINATOR_LOG" >&2
  echo "EffectCoordinator probe did not pass" >&2
  exit 1
fi

EFFECT_VERIFICATION_NONCE="$(date +%s%N)"
EFFECT_VERIFICATION_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.effects.EffectVerificationProbeActivity \
  --es nonce "$EFFECT_VERIFICATION_NONCE")"
if ! grep -Fq "Status: ok" <<<"$EFFECT_VERIFICATION_PROBE_OUTPUT"; then
  echo "$EFFECT_VERIFICATION_PROBE_OUTPUT" >&2
  echo "Effect verification debug probe did not start successfully" >&2
  exit 1
fi
EFFECT_VERIFICATION_PROBE_PASSED=false
for _ in {1..40}; do
  EFFECT_VERIFICATION_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbEffectVerify:I)"
  if grep -Fq \
      "nonce=$EFFECT_VERIFICATION_NONCE effect_verification_probe_complete=true" \
      <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "effect_verifier_defined=true" \
        <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "effect_verification_policies_verified=true" \
        <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "effect_state_separation_verified=true" \
        <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "effect_unknown_reconciliation_verified=true" \
        <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "effect_verified_redispatch_blocked=true" \
        <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "effect_production_readback_fail_closed=true" \
        <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "effect_verification_android13_arm64_verified=true" \
        <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "effect_verification_reconciliation_runtime_wired=false" \
        <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "effect_verification_scheduler_wired=false" \
        <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "effect_verification_persistence_wired=false" \
        <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "effect_verification_production_readback_wired=false" \
        <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "effect_verification_graph_wired=false" \
        <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "production_effect_dispatch_enabled=false" \
        <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "model_invoked=false" <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "network_accessed=false" <<<"$EFFECT_VERIFICATION_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$EFFECT_VERIFICATION_LOG"; then
    EFFECT_VERIFICATION_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$EFFECT_VERIFICATION_PROBE_PASSED" != true ]]; then
  echo "$EFFECT_VERIFICATION_LOG" >&2
  echo "Effect verification probe did not pass" >&2
  exit 1
fi

COMPENSATION_UNDO_NONCE="$(date +%s%N)"
COMPENSATION_UNDO_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.effects.CompensationUndoProbeActivity \
  --es nonce "$COMPENSATION_UNDO_NONCE")"
if ! grep -Fq "Status: ok" <<<"$COMPENSATION_UNDO_PROBE_OUTPUT"; then
  echo "$COMPENSATION_UNDO_PROBE_OUTPUT" >&2
  echo "Compensation/Undo debug probe did not start successfully" >&2
  exit 1
fi
COMPENSATION_UNDO_PROBE_PASSED=false
for _ in {1..40}; do
  COMPENSATION_UNDO_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbCompUndo:I)"
  if grep -Fq \
      "nonce=$COMPENSATION_UNDO_NONCE compensation_undo_probe_complete=true" \
      <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "compensation_planner_defined=true" \
        <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "compensation_absolute_before_verified=true" \
        <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "compensation_reverse_dependency_verified=true" \
        <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "compensation_irreversible_rejected=true" \
        <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "undo_ttl_governance_verified=true" \
        <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "undo_new_governed_task_verified=true" \
        <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "undo_idempotent_replay_verified=true" \
        <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "undo_production_fail_closed=true" \
        <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "compensation_undo_android13_arm64_verified=true" \
        <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "compensation_undo_runtime_wired=false" \
        <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "compensation_undo_persistence_wired=false" \
        <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "undo_binder_service_published=false" \
        <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "compensation_dispatch_enabled=false" \
        <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "production_compensation_authority_wired=false" \
        <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" \
        <<<"$COMPENSATION_UNDO_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$COMPENSATION_UNDO_LOG"; then
    COMPENSATION_UNDO_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$COMPENSATION_UNDO_PROBE_PASSED" != true ]]; then
  echo "$COMPENSATION_UNDO_LOG" >&2
  echo "Compensation/Undo probe did not pass" >&2
  exit 1
fi

SIMULATED_ADAPTER_NONCE="$(date +%s%N)"
SIMULATED_ADAPTER_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.simulation.SimulatedEffectAdapterProbeActivity \
  --es nonce "$SIMULATED_ADAPTER_NONCE")"
if ! grep -Fq "Status: ok" <<<"$SIMULATED_ADAPTER_PROBE_OUTPUT"; then
  echo "$SIMULATED_ADAPTER_PROBE_OUTPUT" >&2
  echo "Simulated Effect adapter base probe did not start successfully" >&2
  exit 1
fi
SIMULATED_ADAPTER_PROBE_PASSED=false
for _ in {1..40}; do
  SIMULATED_ADAPTER_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbSimEffectBase:I)"
  if grep -Fq \
      "nonce=$SIMULATED_ADAPTER_NONCE simulated_effect_adapter_probe_complete=true" \
      <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "simulated_effect_adapter_base_defined=true" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "simulation_descriptor_verified=true" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "simulation_clock_verified=true" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "simulation_delay_verified=true" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "simulation_timeout_verified=true" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "simulation_failure_verified=true" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "simulation_readback_mismatch_verified=true" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "simulation_idempotency_verified=true" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "simulated_effect_adapter_android13_arm64_verified=true" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "simulated_effect_adapter_debug_only=true" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "simulated_effect_adapter_production_registered=false" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "simulated_effect_adapter_runtime_wired=false" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "scenario_plan_runtime_published=false" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "scenario_graph_execution_enabled=false" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "vehicle_signal_provider_wired=false" \
        <<<"$SIMULATED_ADAPTER_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$SIMULATED_ADAPTER_LOG"; then
    SIMULATED_ADAPTER_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$SIMULATED_ADAPTER_PROBE_PASSED" != true ]]; then
  echo "$SIMULATED_ADAPTER_LOG" >&2
  echo "Simulated Effect adapter base probe did not pass" >&2
  exit 1
fi

SIMULATED_HVAC_NONCE="$(date +%s%N)"
SIMULATED_HVAC_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.simulation.SimulatedHvacEffectAdapterProbeActivity \
  --es nonce "$SIMULATED_HVAC_NONCE")"
if ! grep -Fq "Status: ok" <<<"$SIMULATED_HVAC_PROBE_OUTPUT"; then
  echo "$SIMULATED_HVAC_PROBE_OUTPUT" >&2
  echo "Simulated HVAC adapter probe did not start successfully" >&2
  exit 1
fi
SIMULATED_HVAC_PROBE_PASSED=false
for _ in {1..40}; do
  SIMULATED_HVAC_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbSimHvac:I)"
  if grep -Fq "nonce=$SIMULATED_HVAC_NONCE simulated_hvac_probe_complete=true" \
      <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "simulated_hvac_adapter_defined=true" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "simulated_hvac_typed_target_verified=true" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "simulated_hvac_range_zone_verified=true" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "simulated_hvac_desired_reported_verified=true" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "simulated_hvac_delay_verified=true" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "simulated_hvac_timeout_verified=true" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "simulated_hvac_failure_verified=true" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "simulated_hvac_readback_mismatch_verified=true" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "simulated_hvac_idempotency_verified=true" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "simulated_hvac_android13_arm64_verified=true" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "simulated_hvac_debug_only=true" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "simulated_hvac_production_registered=false" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "simulated_hvac_runtime_wired=false" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "scenario_plan_runtime_published=false" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "scenario_graph_execution_enabled=false" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "vehicle_signal_provider_wired=false" <<<"$SIMULATED_HVAC_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$SIMULATED_HVAC_LOG"; then
    SIMULATED_HVAC_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$SIMULATED_HVAC_PROBE_PASSED" != true ]]; then
  echo "$SIMULATED_HVAC_LOG" >&2
  echo "Simulated HVAC adapter probe did not pass" >&2
  exit 1
fi

SIMULATED_SEAT_NONCE="$(date +%s%N)"
SIMULATED_SEAT_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.simulation.SimulatedSeatEffectAdapterProbeActivity \
  --es nonce "$SIMULATED_SEAT_NONCE")"
if ! grep -Fq "Status: ok" <<<"$SIMULATED_SEAT_PROBE_OUTPUT"; then
  echo "$SIMULATED_SEAT_PROBE_OUTPUT" >&2
  echo "Simulated Seat adapter probe did not start successfully" >&2
  exit 1
fi
SIMULATED_SEAT_PROBE_PASSED=false
for _ in {1..40}; do
  SIMULATED_SEAT_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbSimSeat:I)"
  if grep -Fq "nonce=$SIMULATED_SEAT_NONCE simulated_seat_probe_complete=true" \
      <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "simulated_seat_adapter_defined=true" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "simulated_seat_typed_target_verified=true" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "simulated_seat_heat_vent_verified=true" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "simulated_seat_recline_safety_verified=true" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "simulated_seat_dispatch_revalidation_verified=true" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "simulated_seat_belt_race_verified=true" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "simulated_seat_progress_verified=true" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "simulated_seat_fault_readback_verified=true" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "simulated_seat_idempotency_verified=true" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "simulated_seat_android13_arm64_verified=true" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "simulated_seat_debug_only=true" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "simulated_seat_production_registered=false" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "simulated_seat_runtime_wired=false" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "scenario_plan_runtime_published=false" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "scenario_graph_execution_enabled=false" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "vehicle_signal_provider_wired=false" <<<"$SIMULATED_SEAT_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$SIMULATED_SEAT_LOG"; then
    SIMULATED_SEAT_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$SIMULATED_SEAT_PROBE_PASSED" != true ]]; then
  echo "$SIMULATED_SEAT_LOG" >&2
  echo "Simulated Seat adapter probe did not pass" >&2
  exit 1
fi

SIMULATED_MEDIA_NAV_NONCE="$(date +%s%N)"
SIMULATED_MEDIA_NAV_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.simulation.SimulatedMediaNavigationAdapterProbeActivity \
  --es nonce "$SIMULATED_MEDIA_NAV_NONCE")"
if ! grep -Fq "Status: ok" <<<"$SIMULATED_MEDIA_NAV_PROBE_OUTPUT"; then
  echo "$SIMULATED_MEDIA_NAV_PROBE_OUTPUT" >&2
  echo "Simulated Media/Navigation adapter probe did not start successfully" >&2
  exit 1
fi
SIMULATED_MEDIA_NAV_PROBE_PASSED=false
for _ in {1..40}; do
  SIMULATED_MEDIA_NAV_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbSimMediaNav:I)"
  if grep -Fq "nonce=$SIMULATED_MEDIA_NAV_NONCE simulated_media_nav_probe_complete=true" \
      <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "simulated_media_adapter_defined=true" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "simulated_navigation_adapter_defined=true" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "simulated_media_nav_typed_target_verified=true" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "simulated_media_state_verified=true" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "simulated_navigation_synthetic_observation_verified=true" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "simulated_navigation_query_digest_only=true" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "simulated_media_nav_delay_verified=true" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "simulated_media_nav_fault_readback_verified=true" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "simulated_media_nav_idempotency_verified=true" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "simulated_media_nav_replaceable_backend_verified=true" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "simulated_media_nav_android13_arm64_verified=true" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "simulated_media_nav_debug_only=true" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "simulated_media_nav_production_registered=false" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "simulated_media_nav_runtime_wired=false" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "external_activity_started=false" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "location_uploaded=false" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "network_accessed=false" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "scenario_plan_runtime_published=false" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "scenario_graph_execution_enabled=false" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$SIMULATED_MEDIA_NAV_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$SIMULATED_MEDIA_NAV_LOG"; then
    SIMULATED_MEDIA_NAV_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$SIMULATED_MEDIA_NAV_PROBE_PASSED" != true ]]; then
  echo "$SIMULATED_MEDIA_NAV_LOG" >&2
  echo "Simulated Media/Navigation adapter probe did not pass" >&2
  exit 1
fi

DEBUG_SIMULATION_NONCE="$(date +%s%N)"
DEBUG_SIMULATION_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.simulation.DebugSimulationControllerProbeActivity \
  --es nonce "$DEBUG_SIMULATION_NONCE")"
if ! grep -Fq "Status: ok" <<<"$DEBUG_SIMULATION_PROBE_OUTPUT"; then
  echo "$DEBUG_SIMULATION_PROBE_OUTPUT" >&2
  echo "Debug simulation controller probe did not start successfully" >&2
  exit 1
fi
DEBUG_SIMULATION_PROBE_PASSED=false
for _ in {1..40}; do
  DEBUG_SIMULATION_LOG="$("${ADB_DEVICE[@]}" logcat -d \
    -s CbDebugSimProbe:I CbDebugSimService:I '*:S')"
  if grep -Fq \
      "nonce=$DEBUG_SIMULATION_NONCE debug_simulation_controller_probe_complete=true" \
      <<<"$DEBUG_SIMULATION_LOG" \
      && grep -Fq "debug_simulation_controller_defined=true" \
        <<<"$DEBUG_SIMULATION_LOG" \
      && grep -Fq "debug_simulation_controller_aidl_version=1" \
        <<<"$DEBUG_SIMULATION_LOG" \
      && grep -Fq "debug_simulation_controller_signature_permission_enforced=true" \
        <<<"$DEBUG_SIMULATION_LOG" \
      && grep -Fq "debug_simulation_controller_capability_enforced=true" \
        <<<"$DEBUG_SIMULATION_LOG" \
      && grep -Fq \
        "debug_simulation_controller_state_signal_fault_clock_reset_verified=true" \
        <<<"$DEBUG_SIMULATION_LOG" \
      && grep -Fq "debug_simulation_controller_audit_bounded_verified=true" \
        <<<"$DEBUG_SIMULATION_LOG" \
      && grep -Fq "debug_simulation_controller_android13_arm64_verified=true" \
        <<<"$DEBUG_SIMULATION_LOG" \
      && grep -Fq "debug_simulation_controller_debug_only=true" \
        <<<"$DEBUG_SIMULATION_LOG" \
      && grep -Fq "debug_simulation_controller_production_exported=false" \
        <<<"$DEBUG_SIMULATION_LOG" \
      && grep -Fq "debug_simulation_controller_runtime_wired=false" \
        <<<"$DEBUG_SIMULATION_LOG" \
      && grep -Fq "vehicle_signal_provider_wired=false" \
        <<<"$DEBUG_SIMULATION_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$DEBUG_SIMULATION_LOG" \
      && grep -Fq "debug_simulation_audit_event=true command=setDrivingState outcome=APPLIED" \
        <<<"$DEBUG_SIMULATION_LOG" \
      && grep -Fq "debug_simulation_audit_event=true command=reset outcome=APPLIED" \
        <<<"$DEBUG_SIMULATION_LOG"; then
    DEBUG_SIMULATION_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$DEBUG_SIMULATION_PROBE_PASSED" != true ]]; then
  echo "$DEBUG_SIMULATION_LOG" >&2
  echo "Debug simulation controller probe did not pass" >&2
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
  "native_runtime_process_wired=true" \
  "native_runtime_process_ready=true" \
  "native_runtime_process_lifecycle=READY" \
  "native_runtime_detail_code=READY" \
  "native_library_loaded=true" \
  "native_runtime_initialized=true" \
  "native_runtime_abi_version=1" \
  "native_runtime_max_slots=4" \
  "native_runtime_active_slots=0" \
  "native_runtime_generation=1" \
  "native_runtime_last_status=OK" \
  "native_software_provider_available=false" \
  "native_vendor_npu_provider_available=false" \
  "native_runtime_dispatch_enabled=false" \
  "native_hardware_accessed=false" \
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
  "skill_governance_readiness_snapshot_wired=true" \
  "skill_governance_activation_allowed=false" \
  "bounded_built_in_skill_runtime_implementation_available=true" \
  "compiled_built_in_skill_count=3" \
  "compile_time_skill_signer_evidence_available=true" \
  "skill_artifact_cryptographic_verification_performed=false" \
  "fixed_governance_middleware_implementation_available=true" \
  "governance_middleware_stage_count=9" \
  "governance_middleware_order_fixed=true" \
  "skill_lifecycle_store_implemented=false" \
  "skill_revocation_configured=false" \
  "skill_rollback_configured=false" \
  "skill_sandbox_configured=false" \
  "governance_production_authorities_wired=false" \
  "skill_route_owner_registry_wired=false" \
  "skill_governance_middleware_production_wired=false" \
  "skill_governance_audit_persistence_wired=false" \
  "skill_dispatcher_production_wired=false" \
  "skill_dynamic_loading_enabled=false" \
  "raw_skill_input_stored=false" \
  "raw_skill_output_stored=false" \
  "skill_network_access_enabled=false" \
  "skill_governance_activation_blockers=ARTIFACT_CRYPTO_VERIFIER_NOT_CONFIGURED" \
  "runtime_acceptance_snapshot_wired=true" \
  "core_software_baseline_ready=true" \
  "r7_application_integration_complete=true" \
  "client2_binder_migration_complete=true" \
  "api33_end_to_end_acceptance_complete=true" \
  "production_activation_allowed=false" \
  "target_hardware_validated=false" \
  "target_system_integration_owner_resolved=false" \
  "typed_binder_integrated=true" \
  "trusted_governance_integrated=true" \
  "durable_workflow_foundation_ready=true" \
  "standard_artifact_count=3" \
  "signature_protected_service_count=3" \
  "runtime_acceptance_blockers=TARGET_SYSTEM_INTEGRATION_OWNER_UNRESOLVED" \
  "service_dispatch_triggered=false" \
  "hardware_accessed=false"; do
  if ! grep -Fq "$marker" <<<"$RUNTIME_CLIENT_DUMP"; then
    echo "Runtime dumpsys missing marker: $marker" >&2
    exit 1
  fi
done

GRAPH_RESTART_NONCE="$(date +%s%N)"
GRAPH_RESTART_SEED_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.persistence.GraphRestartRecoveryProbeActivity \
  --es nonce "$GRAPH_RESTART_NONCE" \
  --es phase seed)"
if ! grep -Fq "Status: ok" <<<"$GRAPH_RESTART_SEED_OUTPUT"; then
  echo "$GRAPH_RESTART_SEED_OUTPUT" >&2
  echo "Graph restart recovery seed probe did not start successfully" >&2
  exit 1
fi
GRAPH_RESTART_SEED_PASSED=false
for _ in {1..40}; do
  GRAPH_RESTART_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbGraphRestart:I)"
  if grep -Fq "nonce=$GRAPH_RESTART_NONCE graph_restart_seed_complete=true" \
      <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_room_v4_seed_verified=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_side_effect_count=0" \
        <<<"$GRAPH_RESTART_LOG"; then
    GRAPH_RESTART_SEED_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$GRAPH_RESTART_SEED_PASSED" != true ]]; then
  echo "$GRAPH_RESTART_LOG" >&2
  echo "Graph restart recovery seed probe did not pass" >&2
  exit 1
fi

"${ADB_DEVICE[@]}" shell am force-stop com.centralbrain.runtime
GRAPH_RESTART_FIRST_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.persistence.GraphRestartRecoveryProbeActivity \
  --es nonce "$GRAPH_RESTART_NONCE" \
  --es phase recover-first)"
if ! grep -Fq "Status: ok" <<<"$GRAPH_RESTART_FIRST_OUTPUT"; then
  echo "$GRAPH_RESTART_FIRST_OUTPUT" >&2
  echo "Graph restart first recovery probe did not start successfully" >&2
  exit 1
fi
GRAPH_RESTART_FIRST_PASSED=false
for _ in {1..40}; do
  GRAPH_RESTART_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbGraphRestart:I)"
  if grep -Fq \
      "nonce=$GRAPH_RESTART_NONCE graph_restart_first_recovery_complete=true" \
      <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_process_generation_changed=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_side_effect_count=0" \
        <<<"$GRAPH_RESTART_LOG"; then
    GRAPH_RESTART_FIRST_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$GRAPH_RESTART_FIRST_PASSED" != true ]]; then
  echo "$GRAPH_RESTART_LOG" >&2
  echo "Graph restart first recovery probe did not pass" >&2
  exit 1
fi

"${ADB_DEVICE[@]}" shell am force-stop com.centralbrain.runtime
GRAPH_RESTART_REPLAY_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.persistence.GraphRestartRecoveryProbeActivity \
  --es nonce "$GRAPH_RESTART_NONCE" \
  --es phase recover-replay)"
if ! grep -Fq "Status: ok" <<<"$GRAPH_RESTART_REPLAY_OUTPUT"; then
  echo "$GRAPH_RESTART_REPLAY_OUTPUT" >&2
  echo "Graph restart replay recovery probe did not start successfully" >&2
  exit 1
fi
GRAPH_RESTART_REPLAY_PASSED=false
for _ in {1..40}; do
  GRAPH_RESTART_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbGraphRestart:I)"
  if grep -Fq "nonce=$GRAPH_RESTART_NONCE graph_restart_probe_complete=true" \
      <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_reconciler_defined=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_room_v4_repository_verified=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_waiting_recovered=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_executing_reconciled=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_unknown_effect_reconciled=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_approval_undo_revalidation_verified=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_checkpoint_mismatch_stuck=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_continue_after_revalidate_verified=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_process_death_verified=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_idempotent_reopen_verified=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_audit_exactly_once_verified=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_historical_digest_replay_verified=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_side_effect_count=0" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_android13_arm64_verified=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_repository_implementation_available=true" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_runtime_wired=false" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_binder_published=false" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_executor_dispatch_enabled=false" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_effect_dispatch_enabled=false" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "graph_restart_production_wired=false" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "agent_graph_runtime_persistence_wired=false" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "production_effect_dispatch_enabled=false" \
        <<<"$GRAPH_RESTART_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$GRAPH_RESTART_LOG"; then
    GRAPH_RESTART_REPLAY_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$GRAPH_RESTART_REPLAY_PASSED" != true ]]; then
  echo "$GRAPH_RESTART_LOG" >&2
  echo "Graph restart replay recovery probe did not pass" >&2
  exit 1
fi

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
for marker in \
  "native_runtime_process_wired=true" \
  "native_runtime_process_ready=true" \
  "native_runtime_process_lifecycle=READY" \
  "native_runtime_detail_code=READY" \
  "native_library_loaded=true" \
  "native_runtime_initialized=true" \
  "native_runtime_abi_version=1" \
  "native_runtime_max_slots=4" \
  "native_runtime_active_slots=0" \
  "native_runtime_generation=1" \
  "native_runtime_last_status=OK" \
  "native_software_provider_available=false" \
  "native_vendor_npu_provider_available=false" \
  "native_runtime_dispatch_enabled=false" \
  "native_hardware_accessed=false"; do
  if ! grep -Fq "$marker" <<<"$RUNTIME_LOG"; then
    echo "Runtime Native Runtime integration missing marker: $marker" >&2
    exit 1
  fi
done
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
if ! grep -Fq "capability_default=deny capability_rule_count=4" <<<"$RUNTIME_LOG"; then
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
for marker in \
  "skill_governance_readiness_snapshot_wired=true" \
  "skill_governance_activation_allowed=false" \
  "bounded_built_in_skill_runtime_implementation_available=true" \
  "compiled_built_in_skill_count=3" \
  "compile_time_skill_signer_evidence_available=true" \
  "skill_artifact_cryptographic_verification_performed=false" \
  "fixed_governance_middleware_implementation_available=true" \
  "governance_middleware_stage_count=9" \
  "governance_middleware_order_fixed=true" \
  "skill_lifecycle_store_implemented=false" \
  "skill_revocation_configured=false" \
  "skill_rollback_configured=false" \
  "skill_sandbox_configured=false" \
  "governance_production_authorities_wired=false" \
  "skill_route_owner_registry_wired=false" \
  "skill_governance_middleware_production_wired=false" \
  "skill_governance_audit_persistence_wired=false" \
  "skill_dispatcher_production_wired=false" \
  "skill_dynamic_loading_enabled=false" \
  "raw_skill_input_stored=false" \
  "raw_skill_output_stored=false" \
  "skill_network_access_enabled=false" \
  "skill_governance_activation_blockers=ARTIFACT_CRYPTO_VERIFIER_NOT_CONFIGURED"; do
  if ! grep -Fq "$marker" <<<"$RUNTIME_LOG"; then
    echo "Runtime Skill/Governance readiness missing marker: $marker" >&2
    exit 1
  fi
done
for marker in \
  "runtime_acceptance_snapshot_wired=true" \
  "core_software_baseline_ready=true" \
  "r7_application_integration_complete=true" \
  "client2_binder_migration_complete=true" \
  "api33_end_to_end_acceptance_complete=true" \
  "production_activation_allowed=false" \
  "target_hardware_validated=false" \
  "target_system_integration_owner_resolved=false" \
  "typed_binder_integrated=true" \
  "trusted_governance_integrated=true" \
  "durable_workflow_foundation_ready=true" \
  "room_schema_version=4" \
  "standard_artifact_count=3" \
  "signature_protected_service_count=3" \
  "runtime_acceptance_blockers=TARGET_SYSTEM_INTEGRATION_OWNER_UNRESOLVED"; do
  if ! grep -Fq "$marker" <<<"$RUNTIME_LOG"; then
    echo "Runtime acceptance missing marker: $marker" >&2
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

TOOL_MANIFEST_NONCE="$(date +%s%N)"
TOOL_MANIFEST_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.tools.ToolManifestProbeActivity \
  --es nonce "$TOOL_MANIFEST_NONCE")"
if ! grep -Fq "Status: ok" <<<"$TOOL_MANIFEST_PROBE_OUTPUT"; then
  echo "$TOOL_MANIFEST_PROBE_OUTPUT" >&2
  echo "Tool manifest debug probe did not start successfully" >&2
  exit 1
fi
TOOL_MANIFEST_PROBE_PASSED=false
for _ in {1..40}; do
  TOOL_MANIFEST_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbToolManifest:I)"
  if grep -Fq \
      "nonce=$TOOL_MANIFEST_NONCE tool_manifest_probe_complete=true" \
      <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "tool_manifest_contract_defined=true" \
        <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "tool_manifest_schema_version=1" <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "tool_manifest_contract_digest_verified=true" \
        <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "tool_schema_input_output_verified=true" \
        <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "tool_schema_unknown_field_rejected=true" \
        <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "tool_schema_type_bounds_verified=true" \
        <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "tool_manifest_health_fail_closed=true" \
        <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "tool_manifest_android13_arm64_verified=true" \
        <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "tool_registry_published=false" <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "tool_resolver_published=false" <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "tool_execution_enabled=false" <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "production_tool_artifact_loaded=false" \
        <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "vehicle_readback_accessed=false" <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "network_accessed=false" <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "production_ready=false" <<<"$TOOL_MANIFEST_LOG" \
      && grep -Fq "target_hardware_validated=false" <<<"$TOOL_MANIFEST_LOG"; then
    TOOL_MANIFEST_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$TOOL_MANIFEST_PROBE_PASSED" != true ]]; then
  echo "$TOOL_MANIFEST_LOG" >&2
  echo "Tool manifest probe did not pass" >&2
  exit 1
fi

TOOL_REGISTRY_NONCE="$(date +%s%N)"
TOOL_REGISTRY_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.tools.ToolRegistryProbeActivity \
  --es nonce "$TOOL_REGISTRY_NONCE")"
if ! grep -Fq "Status: ok" <<<"$TOOL_REGISTRY_PROBE_OUTPUT"; then
  echo "$TOOL_REGISTRY_PROBE_OUTPUT" >&2
  echo "Tool registry debug probe did not start successfully" >&2
  exit 1
fi
TOOL_REGISTRY_PROBE_PASSED=false
for _ in {1..40}; do
  TOOL_REGISTRY_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbToolRegistry:I)"
  if grep -Fq \
      "nonce=$TOOL_REGISTRY_NONCE tool_registry_probe_complete=true" \
      <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "tool_registry_contract_defined=true" \
        <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "tool_resolver_contract_defined=true" \
        <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "tool_health_dynamic_snapshot_defined=true" \
        <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "tool_registry_probe_registration_count=2" \
        <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "tool_registry_digest_verified=true" \
        <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "tool_registry_version_conflict_rejected=true" \
        <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "tool_resolver_highest_version_deterministic=true" \
        <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "tool_resolver_states_separated=true" \
        <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "tool_resolver_unhealthy_no_fallback=true" \
        <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "tool_health_fail_closed=true" \
        <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "tool_registry_android13_arm64_verified=true" \
        <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "tool_registry_published=false" <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "tool_resolver_published=false" <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "tool_registry_runtime_wired=false" <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "tool_execution_enabled=false" <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "production_tool_registered=false" \
        <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "vehicle_readback_accessed=false" <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "network_accessed=false" <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "production_ready=false" <<<"$TOOL_REGISTRY_LOG" \
      && grep -Fq "target_hardware_validated=false" <<<"$TOOL_REGISTRY_LOG"; then
    TOOL_REGISTRY_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$TOOL_REGISTRY_PROBE_PASSED" != true ]]; then
  echo "$TOOL_REGISTRY_LOG" >&2
  echo "Tool registry probe did not pass" >&2
  exit 1
fi

TOOL_RULE_SOLVER_NONCE="$(date +%s%N)"
TOOL_RULE_SOLVER_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.tools.ToolRuleSolverProbeActivity \
  --es nonce "$TOOL_RULE_SOLVER_NONCE")"
if ! grep -Fq "Status: ok" <<<"$TOOL_RULE_SOLVER_PROBE_OUTPUT"; then
  echo "$TOOL_RULE_SOLVER_PROBE_OUTPUT" >&2
  echo "Tool rule solver debug probe did not start successfully" >&2
  exit 1
fi
TOOL_RULE_SOLVER_PROBE_PASSED=false
for _ in {1..40}; do
  TOOL_RULE_SOLVER_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbToolRuleSolver:I)"
  if grep -Fq \
      "nonce=$TOOL_RULE_SOLVER_NONCE tool_rule_solver_probe_complete=true" \
      <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "tool_rule_set_contract_defined=true" \
        <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "tool_rule_type_count=6" <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "tool_rule_set_digest_verified=true" \
        <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "tool_rule_init_child_conditional_verified=true" \
        <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "tool_rule_model_intersection_fail_closed=true" \
        <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "tool_rule_terminal_requirements_verified=true" \
        <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "tool_rule_approval_annotation_fail_closed=true" \
        <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "tool_rule_solver_android13_arm64_verified=true" \
        <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "tool_rule_solver_published=false" \
        <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "tool_rule_solver_runtime_wired=false" \
        <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "tool_approval_authority_available=false" \
        <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "tool_execution_enabled=false" <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "production_tool_registered=false" \
        <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "vehicle_readback_accessed=false" \
        <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "model_invoked=false" <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "network_accessed=false" <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "production_ready=false" <<<"$TOOL_RULE_SOLVER_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$TOOL_RULE_SOLVER_LOG"; then
    TOOL_RULE_SOLVER_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$TOOL_RULE_SOLVER_PROBE_PASSED" != true ]]; then
  echo "$TOOL_RULE_SOLVER_LOG" >&2
  echo "Tool rule solver probe did not pass" >&2
  exit 1
fi

TOOL_EXECUTOR_NONCE="$(date +%s%N)"
TOOL_EXECUTOR_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.tools.ToolExecutorProbeActivity \
  --es nonce "$TOOL_EXECUTOR_NONCE")"
if ! grep -Fq "Status: ok" <<<"$TOOL_EXECUTOR_PROBE_OUTPUT"; then
  echo "$TOOL_EXECUTOR_PROBE_OUTPUT" >&2
  echo "Tool executor debug probe did not start successfully" >&2
  exit 1
fi
TOOL_EXECUTOR_PROBE_PASSED=false
for _ in {1..40}; do
  TOOL_EXECUTOR_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbToolExecutor:I)"
  if grep -Fq \
      "nonce=$TOOL_EXECUTOR_NONCE tool_executor_probe_complete=true" \
      <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "tool_executor_contract_defined=true" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "tool_invocation_context_defined=true" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "built_in_allowlist_enforced=true" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "built_in_signer_artifact_bound=true" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "tool_executor_success_verified=true" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "tool_executor_deadline_cancel_verified=true" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "tool_executor_output_limit_verified=true" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "tool_executor_audit_bounded_verified=true" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "tool_executor_android13_arm64_verified=true" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "tool_executor_runtime_wired=false" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "tool_execution_enabled=false" <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "production_tool_execution_enabled=false" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "production_tool_registered=false" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "tool_approval_authority_available=false" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "os_virtualization_enabled=false" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "subprocess_started=false" <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "dynamic_class_loading_enabled=false" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "vehicle_readback_accessed=false" \
        <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "model_invoked=false" <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "network_accessed=false" <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "production_ready=false" <<<"$TOOL_EXECUTOR_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$TOOL_EXECUTOR_LOG"; then
    TOOL_EXECUTOR_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$TOOL_EXECUTOR_PROBE_PASSED" != true ]]; then
  echo "$TOOL_EXECUTOR_LOG" >&2
  echo "Tool executor probe did not pass" >&2
  exit 1
fi

SKILL_PACKAGE_VERIFIER_NONCE="$(date +%s%N)"
SKILL_PACKAGE_VERIFIER_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.skills.SkillArtifactVerifierProbeActivity \
  --es nonce "$SKILL_PACKAGE_VERIFIER_NONCE")"
if ! grep -Fq "Status: ok" <<<"$SKILL_PACKAGE_VERIFIER_PROBE_OUTPUT"; then
  echo "$SKILL_PACKAGE_VERIFIER_PROBE_OUTPUT" >&2
  echo "Skill package verifier debug probe did not start successfully" >&2
  exit 1
fi
SKILL_PACKAGE_VERIFIER_PROBE_PASSED=false
for _ in {1..40}; do
  SKILL_PACKAGE_VERIFIER_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbSkillVerifier:I)"
  if grep -Fq \
      "nonce=$SKILL_PACKAGE_VERIFIER_NONCE skill_package_verifier_probe_complete=true" \
      <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "skill_artifact_hash_verified=true" \
        <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "skill_manifest_digest_verified=true" \
        <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "skill_signer_policy_verified=true" \
        <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "skill_runtime_version_verified=true" \
        <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "skill_capability_policy_verified=true" \
        <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "skill_revocation_downgrade_fail_closed=true" \
        <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "skill_package_verifier_android13_arm64_verified=true" \
        <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "trusted_skill_evidence_source_configured=false" \
        <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "package_signature_cryptographically_verified=false" \
        <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "dynamic_skill_loading_enabled=false" \
        <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "skill_execution_enabled=false" \
        <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "skill_package_verifier_runtime_wired=false" \
        <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" \
        <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "vehicle_readback_accessed=false" \
        <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "model_invoked=false" <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "network_accessed=false" <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "production_ready=false" <<<"$SKILL_PACKAGE_VERIFIER_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$SKILL_PACKAGE_VERIFIER_LOG"; then
    SKILL_PACKAGE_VERIFIER_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$SKILL_PACKAGE_VERIFIER_PROBE_PASSED" != true ]]; then
  echo "$SKILL_PACKAGE_VERIFIER_LOG" >&2
  echo "Skill package verifier probe did not pass" >&2
  exit 1
fi

WORKING_MEMORY_NONCE="$(date +%s%N)"
WORKING_MEMORY_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.memory.WorkingMemoryStoreProbeActivity \
  --es nonce "$WORKING_MEMORY_NONCE")"
if ! grep -Fq "Status: ok" <<<"$WORKING_MEMORY_PROBE_OUTPUT"; then
  echo "$WORKING_MEMORY_PROBE_OUTPUT" >&2
  echo "Working Memory store debug probe did not start successfully" >&2
  exit 1
fi
WORKING_MEMORY_PROBE_PASSED=false
for _ in {1..40}; do
  WORKING_MEMORY_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbWorkingMemory:I)"
  if grep -Fq \
      "nonce=$WORKING_MEMORY_NONCE working_memory_store_probe_complete=true" \
      <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "working_memory_session_scope_verified=true" \
        <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "working_memory_ttl_verified=true" \
        <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "working_memory_item_limit_verified=true" \
        <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "working_memory_byte_limit_verified=true" \
        <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "working_memory_token_limit_verified=true" \
        <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "working_memory_terminal_cleanup_verified=true" \
        <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "working_memory_payload_zeroized_on_cleanup=true" \
        <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "working_memory_android13_arm64_verified=true" \
        <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "working_memory_process_local=true" \
        <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "working_memory_persistence_wired=false" \
        <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "working_memory_runtime_wired=false" \
        <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "working_memory_model_context_published=false" \
        <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "working_memory_tokenizer_verified=false" \
        <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "working_memory_content_logged=false" \
        <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "graph_execution_enabled=false" <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "vehicle_readback_accessed=false" <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "model_invoked=false" <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "network_accessed=false" <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "production_ready=false" <<<"$WORKING_MEMORY_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$WORKING_MEMORY_LOG"; then
    WORKING_MEMORY_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$WORKING_MEMORY_PROBE_PASSED" != true ]]; then
  echo "$WORKING_MEMORY_LOG" >&2
  echo "Working Memory store probe did not pass" >&2
  exit 1
fi

PROFILE_MEMORY_NONCE="$(date +%s%N)"
PROFILE_MEMORY_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.memory.ProfileMemoryStoreProbeActivity \
  --es nonce "$PROFILE_MEMORY_NONCE")"
if ! grep -Fq "Status: ok" <<<"$PROFILE_MEMORY_PROBE_OUTPUT"; then
  echo "$PROFILE_MEMORY_PROBE_OUTPUT" >&2
  echo "Profile Memory store debug probe did not start successfully" >&2
  exit 1
fi
PROFILE_MEMORY_PROBE_PASSED=false
for _ in {1..40}; do
  PROFILE_MEMORY_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbProfileMemory:I)"
  if grep -Fq \
      "nonce=$PROFILE_MEMORY_NONCE profile_memory_store_probe_complete=true" \
      <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_explicit_consent_verified=true" \
        <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_field_allowlist_verified=true" \
        <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_user_seat_scope_verified=true" \
        <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_read_update_verified=true" \
        <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_delete_verified=true" \
        <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_export_verified=true" \
        <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_consent_revocation_fail_closed=true" \
        <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_encryption_owner_gate_verified=true" \
        <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_sealed_payload_zeroized=true" \
        <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_android13_arm64_verified=true" \
        <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_process_local=true" <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_durable_storage_wired=false" \
        <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_production_encryption_owner_configured=false" \
        <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_consent_authority_production_wired=false" \
        <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_runtime_wired=false" <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "profile_memory_content_logged=false" <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "graph_execution_enabled=false" <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "vehicle_readback_accessed=false" <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "model_invoked=false" <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "network_accessed=false" <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "production_ready=false" <<<"$PROFILE_MEMORY_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$PROFILE_MEMORY_LOG"; then
    PROFILE_MEMORY_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$PROFILE_MEMORY_PROBE_PASSED" != true ]]; then
  echo "$PROFILE_MEMORY_LOG" >&2
  echo "Profile Memory store probe did not pass" >&2
  exit 1
fi

EPISODIC_MEMORY_NONCE="$(date +%s%N)"
EPISODIC_MEMORY_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.memory.EpisodicMemoryStoreProbeActivity \
  --es nonce "$EPISODIC_MEMORY_NONCE")"
if ! grep -Fq "Status: ok" <<<"$EPISODIC_MEMORY_PROBE_OUTPUT"; then
  echo "$EPISODIC_MEMORY_PROBE_OUTPUT" >&2
  echo "Episodic Memory store debug probe did not start successfully" >&2
  exit 1
fi
EPISODIC_MEMORY_PROBE_PASSED=false
for _ in {1..40}; do
  EPISODIC_MEMORY_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbEpisodicMemory:I)"
  if grep -Fq \
      "nonce=$EPISODIC_MEMORY_NONCE episodic_memory_store_probe_complete=true" \
      <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_summary_result_only_verified=true" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_owner_isolation_verified=true" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_read_fail_closed=true" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_policy_fail_closed=true" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_retention_verified=true" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_capacity_verified=true" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_erase_verified=true" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_erase_fail_closed=true" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_android13_arm64_verified=true" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_process_local=true" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_raw_continuous_signal_stored=false" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_arbitrary_payload_stored=false" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_persistence_wired=false" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_runtime_wired=false" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_model_context_published=false" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_production_policy_authority_wired=false" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_production_read_authority_wired=false" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_production_erase_authority_wired=false" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "episodic_memory_content_logged=false" \
        <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "graph_execution_enabled=false" <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "vehicle_readback_accessed=false" <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "model_invoked=false" <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "network_accessed=false" <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "production_ready=false" <<<"$EPISODIC_MEMORY_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$EPISODIC_MEMORY_LOG"; then
    EPISODIC_MEMORY_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$EPISODIC_MEMORY_PROBE_PASSED" != true ]]; then
  echo "$EPISODIC_MEMORY_LOG" >&2
  echo "Episodic Memory store probe did not pass" >&2
  exit 1
fi

CONTEXT_BUDGET_NONCE="$(date +%s%N)"
CONTEXT_BUDGET_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.memory.ContextBudgetManagerProbeActivity \
  --es nonce "$CONTEXT_BUDGET_NONCE")"
if ! grep -Fq "Status: ok" <<<"$CONTEXT_BUDGET_PROBE_OUTPUT"; then
  echo "$CONTEXT_BUDGET_PROBE_OUTPUT" >&2
  echo "Context Budget manager debug probe did not start successfully" >&2
  exit 1
fi
CONTEXT_BUDGET_PROBE_PASSED=false
for _ in {1..40}; do
  CONTEXT_BUDGET_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbContextBudget:I)"
  if grep -Fq \
      "nonce=$CONTEXT_BUDGET_NONCE context_budget_manager_probe_complete=true" \
      <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "context_budget_category_allocation_verified=true" \
        <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "context_budget_dual_limit_verified=true" \
        <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "context_budget_deterministic_overflow_verified=true" \
        <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "context_budget_required_fail_closed=true" \
        <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "context_budget_android13_arm64_verified=true" \
        <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "context_budget_decision_only=true" <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "context_budget_text_payload_accepted=false" \
        <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "context_budget_tokenizer_wired=false" \
        <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "context_budget_summarizer_wired=false" \
        <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "context_budget_production_authority_wired=false" \
        <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "context_budget_runtime_wired=false" <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "context_budget_content_logged=false" <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "graph_execution_enabled=false" <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "vehicle_readback_accessed=false" <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "model_invoked=false" <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "network_accessed=false" <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "production_ready=false" <<<"$CONTEXT_BUDGET_LOG" \
      && grep -Fq "target_hardware_validated=false" <<<"$CONTEXT_BUDGET_LOG"; then
    CONTEXT_BUDGET_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$CONTEXT_BUDGET_PROBE_PASSED" != true ]]; then
  echo "$CONTEXT_BUDGET_LOG" >&2
  echo "Context Budget manager probe did not pass" >&2
  exit 1
fi

MEMORY_CONSENT_NONCE="$(date +%s%N)"
MEMORY_CONSENT_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.memory.MemoryConsentHmiActivity \
  --ez automated true \
  --es nonce "$MEMORY_CONSENT_NONCE")"
if ! grep -Fq "Status: ok" <<<"$MEMORY_CONSENT_PROBE_OUTPUT"; then
  echo "$MEMORY_CONSENT_PROBE_OUTPUT" >&2
  echo "Memory consent HMI debug probe did not start successfully" >&2
  exit 1
fi
MEMORY_CONSENT_PROBE_PASSED=false
for _ in {1..40}; do
  MEMORY_CONSENT_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbMemoryConsent:I)"
  if grep -Fq \
      "nonce=$MEMORY_CONSENT_NONCE memory_consent_hmi_probe_complete=true" \
      <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "memory_consent_source_visibility_verified=true" \
        <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "memory_consent_disable_verified=true" \
        <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "memory_consent_preference_clear_verified=true" \
        <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "memory_consent_moving_restriction_verified=true" \
        <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "memory_consent_android13_arm64_verified=true" \
        <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "memory_consent_hmi_projection_only=true" \
        <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "memory_consent_repository_mutation_wired=false" \
        <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "memory_consent_production_authority_wired=false" \
        <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "memory_consent_runtime_wired=false" <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "memory_consent_model_context_published=false" \
        <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "memory_consent_content_logged=false" <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "graph_execution_enabled=false" <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "vehicle_readback_accessed=false" <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "model_invoked=false" <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "network_accessed=false" <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "production_ready=false" <<<"$MEMORY_CONSENT_LOG" \
      && grep -Fq "target_hardware_validated=false" <<<"$MEMORY_CONSENT_LOG"; then
    MEMORY_CONSENT_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$MEMORY_CONSENT_PROBE_PASSED" != true ]]; then
  echo "$MEMORY_CONSENT_LOG" >&2
  echo "Memory consent HMI probe did not pass" >&2
  exit 1
fi

EVENT_BROKER_NONCE="$(date +%s%N)"
EVENT_BROKER_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.events.EventBrokerProbeActivity \
  --es nonce "$EVENT_BROKER_NONCE")"
if ! grep -Fq "Status: ok" <<<"$EVENT_BROKER_PROBE_OUTPUT"; then
  echo "$EVENT_BROKER_PROBE_OUTPUT" >&2
  echo "Event Broker debug probe did not start successfully" >&2
  exit 1
fi
EVENT_BROKER_PROBE_PASSED=false
for _ in {1..40}; do
  EVENT_BROKER_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbEventBroker:I)"
  if grep -Fq \
      "nonce=$EVENT_BROKER_NONCE event_broker_probe_complete=true" \
      <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "event_broker_typed_topics_verified=true" \
        <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "event_broker_append_before_notify_verified=true" \
        <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "event_broker_bounded_replay_filter_verified=true" \
        <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "event_broker_identity_policy_verified=true" \
        <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "event_broker_subscription_lifecycle_verified=true" \
        <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "event_broker_android13_arm64_verified=true" \
        <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "event_broker_process_local=true" <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "event_broker_durable_persistence_wired=false" \
        <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "event_broker_dds_transport_wired=false" \
        <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "event_broker_production_published=false" \
        <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "event_broker_runtime_wired=false" <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "graph_execution_enabled=false" <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "vehicle_readback_accessed=false" <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "model_invoked=false" <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "network_accessed=false" <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "production_ready=false" <<<"$EVENT_BROKER_LOG" \
      && grep -Fq "target_hardware_validated=false" <<<"$EVENT_BROKER_LOG"; then
    EVENT_BROKER_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$EVENT_BROKER_PROBE_PASSED" != true ]]; then
  echo "$EVENT_BROKER_LOG" >&2
  echo "Event Broker probe did not pass" >&2
  exit 1
fi

EVENT_QOS_NONCE="$(date +%s%N)"
EVENT_QOS_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.events.EventBackpressureProbeActivity \
  --es nonce "$EVENT_QOS_NONCE")"
if ! grep -Fq "Status: ok" <<<"$EVENT_QOS_PROBE_OUTPUT"; then
  echo "$EVENT_QOS_PROBE_OUTPUT" >&2
  echo "Event Backpressure/QoS debug probe did not start successfully" >&2
  exit 1
fi
EVENT_QOS_PROBE_PASSED=false
for _ in {1..40}; do
  EVENT_QOS_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbEventQoS:I)"
  if grep -Fq "nonce=$EVENT_QOS_NONCE event_qos_probe_complete=true" \
      <<<"$EVENT_QOS_LOG" \
      && grep -Fq "event_qos_policies_verified=true" <<<"$EVENT_QOS_LOG" \
      && grep -Fq "event_qos_critical_no_silent_drop_verified=true" \
        <<<"$EVENT_QOS_LOG" \
      && grep -Fq "event_qos_deadline_priority_verified=true" \
        <<<"$EVENT_QOS_LOG" \
      && grep -Fq "event_qos_consumer_isolation_verified=true" \
        <<<"$EVENT_QOS_LOG" \
      && grep -Fq "event_qos_android13_arm64_verified=true" \
        <<<"$EVENT_QOS_LOG" \
      && grep -Fq "event_qos_process_local=true" <<<"$EVENT_QOS_LOG" \
      && grep -Fq "event_qos_broker_wired=false" <<<"$EVENT_QOS_LOG" \
      && grep -Fq "event_qos_durable_persistence_wired=false" \
        <<<"$EVENT_QOS_LOG" \
      && grep -Fq "event_qos_production_middleware_wired=false" \
        <<<"$EVENT_QOS_LOG" \
      && grep -Fq "graph_execution_enabled=false" <<<"$EVENT_QOS_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$EVENT_QOS_LOG" \
      && grep -Fq "vehicle_readback_accessed=false" <<<"$EVENT_QOS_LOG" \
      && grep -Fq "model_invoked=false" <<<"$EVENT_QOS_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$EVENT_QOS_LOG" \
      && grep -Fq "network_accessed=false" <<<"$EVENT_QOS_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$EVENT_QOS_LOG" \
      && grep -Fq "production_ready=false" <<<"$EVENT_QOS_LOG" \
      && grep -Fq "target_hardware_validated=false" <<<"$EVENT_QOS_LOG"; then
    EVENT_QOS_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$EVENT_QOS_PROBE_PASSED" != true ]]; then
  echo "$EVENT_QOS_LOG" >&2
  echo "Event Backpressure/QoS probe did not pass" >&2
  exit 1
fi

TRIGGER_ENGINE_NONCE="$(date +%s%N)"
TRIGGER_ENGINE_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.events.TriggerEngineProbeActivity \
  --es nonce "$TRIGGER_ENGINE_NONCE")"
if ! grep -Fq "Status: ok" <<<"$TRIGGER_ENGINE_PROBE_OUTPUT"; then
  echo "$TRIGGER_ENGINE_PROBE_OUTPUT" >&2
  echo "TriggerEngine debug probe did not start successfully" >&2
  exit 1
fi
TRIGGER_ENGINE_PROBE_PASSED=false
for _ in {1..40}; do
  TRIGGER_ENGINE_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbTriggerEngine:I)"
  if grep -Fq "nonce=$TRIGGER_ENGINE_NONCE trigger_engine_probe_complete=true" \
      <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "trigger_rule_manifest_verified=true" \
        <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "trigger_threshold_window_debounce_verified=true" \
        <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "trigger_cooldown_scope_verified=true" \
        <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "trigger_input_fail_closed_verified=true" \
        <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "trigger_suggestion_only_verified=true" \
        <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "trigger_engine_android13_arm64_verified=true" \
        <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "trigger_engine_process_local=true" <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "trigger_cooldown_persistence_wired=false" \
        <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "trigger_source_adapter_wired=false" \
        <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "trigger_auto_execution_enabled=false" \
        <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "trigger_runtime_wired=false" <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "graph_execution_enabled=false" <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "vehicle_readback_accessed=false" <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "model_invoked=false" <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "network_accessed=false" <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "production_ready=false" <<<"$TRIGGER_ENGINE_LOG" \
      && grep -Fq "target_hardware_validated=false" <<<"$TRIGGER_ENGINE_LOG"; then
    TRIGGER_ENGINE_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$TRIGGER_ENGINE_PROBE_PASSED" != true ]]; then
  echo "$TRIGGER_ENGINE_LOG" >&2
  echo "TriggerEngine probe did not pass" >&2
  exit 1
fi

PROACTIVE_CONSENT_NONCE="$(date +%s%N)"
PROACTIVE_CONSENT_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.events.ProactiveConsentPolicyProbeActivity \
  --es nonce "$PROACTIVE_CONSENT_NONCE")"
if ! grep -Fq "Status: ok" <<<"$PROACTIVE_CONSENT_PROBE_OUTPUT"; then
  echo "$PROACTIVE_CONSENT_PROBE_OUTPUT" >&2
  echo "ProactiveConsentPolicy debug probe did not start successfully" >&2
  exit 1
fi
PROACTIVE_CONSENT_PROBE_PASSED=false
for _ in {1..40}; do
  PROACTIVE_CONSENT_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbProactiveConsent:I)"
  if grep -Fq "nonce=$PROACTIVE_CONSENT_NONCE proactive_consent_probe_complete=true" \
      <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "proactive_grant_binding_verified=true" \
        <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "proactive_high_critical_generic_grant_blocked=true" \
        <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "proactive_grant_ttl_revoke_verified=true" \
        <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "proactive_policy_fail_closed_verified=true" \
        <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "proactive_consent_android13_arm64_verified=true" \
        <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "proactive_policy_process_local=true" \
        <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "proactive_grant_persistence_wired=false" \
        <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "proactive_consent_authority_wired=false" \
        <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "proactive_auto_execution_enabled=false" \
        <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "proactive_runtime_wired=false" <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "graph_execution_enabled=false" <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "vehicle_readback_accessed=false" <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "model_invoked=false" <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "network_accessed=false" <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "production_ready=false" <<<"$PROACTIVE_CONSENT_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$PROACTIVE_CONSENT_LOG"; then
    PROACTIVE_CONSENT_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$PROACTIVE_CONSENT_PROBE_PASSED" != true ]]; then
  echo "$PROACTIVE_CONSENT_LOG" >&2
  echo "ProactiveConsentPolicy probe did not pass" >&2
  exit 1
fi

CONTEXT_SOURCE_NONCE="$(date +%s%N)"
CONTEXT_SOURCE_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.events.ContextSourceAdaptersProbeActivity \
  --es nonce "$CONTEXT_SOURCE_NONCE")"
if ! grep -Fq "Status: ok" <<<"$CONTEXT_SOURCE_PROBE_OUTPUT"; then
  echo "$CONTEXT_SOURCE_PROBE_OUTPUT" >&2
  echo "Context source adapters debug probe did not start successfully" >&2
  exit 1
fi
CONTEXT_SOURCE_PROBE_PASSED=false
for _ in {1..40}; do
  CONTEXT_SOURCE_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbContextSources:I)"
  if grep -Fq "nonce=$CONTEXT_SOURCE_NONCE context_source_probe_complete=true" \
      <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "context_source_allowlist_verified=true" \
        <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "context_source_runtime_health_verified=true" \
        <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "context_source_simulated_vehicle_verified=true" \
        <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "context_source_time_verified=true" \
        <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "context_source_freshness_quality_verified=true" \
        <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "context_source_fail_closed_verified=true" \
        <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "context_source_android13_arm64_verified=true" \
        <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "context_source_count=3" <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "context_source_production_registry_published=false" \
        <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "context_source_runtime_wired=false" \
        <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "context_source_trigger_engine_wired=false" \
        <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "vehicle_signal_provider_wired=false" \
        <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "vehicle_property_mapping_configured=false" \
        <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "graph_execution_enabled=false" <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "model_invoked=false" <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "network_accessed=false" <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "production_ready=false" <<<"$CONTEXT_SOURCE_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$CONTEXT_SOURCE_LOG"; then
    CONTEXT_SOURCE_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$CONTEXT_SOURCE_PROBE_PASSED" != true ]]; then
  echo "$CONTEXT_SOURCE_LOG" >&2
  echo "Context source adapters probe did not pass" >&2
  exit 1
fi

ACTIVE_SUGGESTION_NONCE="$(date +%s%N)"
ACTIVE_SUGGESTION_PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.suggestion.ActiveSuggestionHmiActivity \
  --ez automated true \
  --es nonce "$ACTIVE_SUGGESTION_NONCE")"
if ! grep -Fq "Status: ok" <<<"$ACTIVE_SUGGESTION_PROBE_OUTPUT"; then
  echo "$ACTIVE_SUGGESTION_PROBE_OUTPUT" >&2
  echo "Active suggestion UX debug probe did not start successfully" >&2
  exit 1
fi
ACTIVE_SUGGESTION_PROBE_PASSED=false
for _ in {1..40}; do
  ACTIVE_SUGGESTION_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbActiveSuggestion:I)"
  if grep -Fq \
      "nonce=$ACTIVE_SUGGESTION_NONCE active_suggestion_hmi_probe_complete=true" \
      <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "active_suggestion_full_card_verified=true" \
        <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "active_suggestion_merge_replay_verified=true" \
        <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "active_suggestion_moving_minimal_verified=true" \
        <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "active_suggestion_never_ask_verified=true" \
        <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "active_suggestion_android13_arm64_verified=true" \
        <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "active_suggestion_hmi_projection_only=true" \
        <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "active_suggestion_production_source_wired=false" \
        <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "active_suggestion_preference_repository_wired=false" \
        <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "active_suggestion_voice_engine_wired=false" \
        <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "trigger_engine_wired=false" <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "graph_execution_enabled=false" <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "effect_dispatch_enabled=false" <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "vehicle_readback_accessed=false" \
        <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "model_invoked=false" <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "npu_accessed=false" <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "network_accessed=false" <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "hardware_accessed=false" <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "production_ready=false" <<<"$ACTIVE_SUGGESTION_LOG" \
      && grep -Fq "target_hardware_validated=false" \
        <<<"$ACTIVE_SUGGESTION_LOG"; then
    ACTIVE_SUGGESTION_PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
if [[ "$ACTIVE_SUGGESTION_PROBE_PASSED" != true ]]; then
  echo "$ACTIVE_SUGGESTION_LOG" >&2
  echo "Active suggestion UX probe did not pass" >&2
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
  "skill_governance_readiness_diagnostic_verified=true" \
  "runtime_acceptance_diagnostic_verified=true" \
  "native_runtime_apk_verified=true" \
  "native_runtime_process_wired=true" \
  "native_runtime_load_verified=true" \
  "native_runtime_lifecycle_verified=true" \
  "native_runtime_dumpsys_verified=true" \
  "native_runtime_diagnostic_verified=true" \
  "native_runtime_abi_version=1" \
  "native_runtime_abis=arm64-v8a,x86_64" \
  "native_software_provider_available=false" \
  "native_vendor_npu_provider_available=false" \
  "native_runtime_dispatch_enabled=false" \
  "native_hardware_accessed=false" \
  "vehicle_signal_schema_defined=true" \
  "vehicle_signal_path_allowlist_count=12" \
  "vehicle_signal_schema_android13_arm64_verified=true" \
  "vehicle_signal_provider_wired=false" \
  "vehicle_property_mapping_configured=false" \
  "vehicle_capability_catalog_defined=true" \
  "vehicle_capability_count=8" \
  "vehicle_capability_catalog_android13_arm64_verified=true" \
  "vehicle_production_capability_authorized_count=0" \
  "vehicle_capability_adapter_registry_wired=false" \
  "vehicle_digital_twin_store_defined=true" \
  "vehicle_digital_twin_android13_arm64_verified=true" \
  "vehicle_digital_twin_persistence_wired=false" \
  "vehicle_digital_twin_adapter_wired=false" \
  "context_snapshot_defined=true" \
  "context_snapshot_android13_arm64_verified=true" \
  "context_snapshot_production_trusted=false" \
  "context_snapshot_production_wired=false" \
  "context_source_adapter_contract_defined=true" \
  "context_source_count=3" \
  "context_source_allowlist_verified=true" \
  "context_source_runtime_health_verified=true" \
  "context_source_simulated_vehicle_verified=true" \
  "context_source_time_verified=true" \
  "context_source_freshness_quality_verified=true" \
  "context_source_fail_closed_verified=true" \
  "context_source_android13_arm64_verified=true" \
  "context_source_production_registry_published=false" \
  "context_source_runtime_wired=false" \
  "context_source_trigger_engine_wired=false" \
  "active_suggestion_controller_defined=true" \
  "active_suggestion_full_card_verified=true" \
  "active_suggestion_merge_replay_verified=true" \
  "active_suggestion_moving_minimal_verified=true" \
  "active_suggestion_never_ask_verified=true" \
  "active_suggestion_android13_arm64_verified=true" \
  "active_suggestion_hmi_projection_only=true" \
  "active_suggestion_production_source_wired=false" \
  "active_suggestion_preference_repository_wired=false" \
  "active_suggestion_voice_engine_wired=false" \
  "scenario_manifest_schema_version=1" \
  "scenario_catalog_count=3" \
  "scenario_manifest_android13_arm64_verified=true" \
  "scenario_manifest_artifact_crypto_verified=false" \
  "scenario_catalog_production_trusted=false" \
  "scenario_runtime_wired=false" \
  "scenario_graph_execution_enabled=false" \
  "scenario_resolver_defined=true" \
  "scenario_resolver_android13_arm64_verified=true" \
  "scenario_resolver_model_invoked=false" \
  "scenario_resolver_runtime_wired=false" \
  "scenario_compiler_wired=false" \
  "scenario_plan_compiler_defined=true" \
  "scenario_plan_compiler_android13_arm64_verified=true" \
  "scenario_plan_compiler_runtime_wired=false" \
  "scenario_plan_runtime_published=false" \
  "simulated_effect_adapter_base_defined=true" \
  "simulated_effect_adapter_android13_arm64_verified=true" \
  "simulated_effect_adapter_debug_only=true" \
  "simulated_effect_adapter_production_registered=false" \
  "simulated_effect_adapter_runtime_wired=false" \
  "simulated_hvac_adapter_defined=true" \
  "simulated_hvac_android13_arm64_verified=true" \
  "simulated_hvac_debug_only=true" \
  "simulated_hvac_production_registered=false" \
  "simulated_hvac_runtime_wired=false" \
  "simulated_seat_adapter_defined=true" \
  "simulated_seat_android13_arm64_verified=true" \
  "simulated_seat_debug_only=true" \
  "simulated_seat_production_registered=false" \
  "simulated_seat_runtime_wired=false" \
  "simulated_media_adapter_defined=true" \
  "simulated_navigation_adapter_defined=true" \
  "simulated_media_nav_android13_arm64_verified=true" \
  "simulated_media_nav_debug_only=true" \
  "simulated_media_nav_production_registered=false" \
  "simulated_media_nav_runtime_wired=false" \
  "external_activity_started=false" \
  "location_uploaded=false" \
  "network_accessed=false" \
  "debug_simulation_controller_defined=true" \
  "debug_simulation_controller_aidl_version=1" \
  "debug_simulation_controller_signature_permission_enforced=true" \
  "debug_simulation_controller_capability_enforced=true" \
  "debug_simulation_controller_state_signal_fault_clock_reset_verified=true" \
  "debug_simulation_controller_audit_bounded_verified=true" \
  "debug_simulation_controller_android13_arm64_verified=true" \
  "debug_simulation_controller_debug_only=true" \
  "debug_simulation_controller_production_exported=false" \
  "debug_simulation_controller_runtime_wired=false" \
  "agent_graph_runtime_defined=true" \
  "agent_graph_state_machine_verified=true" \
  "agent_graph_same_session_fifo_verified=true" \
  "agent_graph_cross_session_bounded_verified=true" \
  "agent_graph_partial_terminal_verified=true" \
  "agent_graph_deadline_verified=true" \
  "agent_graph_compensation_fail_closed_verified=true" \
  "agent_graph_event_projection_bounded=true" \
  "agent_graph_android13_arm64_verified=true" \
  "agent_graph_executor_dispatch_enabled=false" \
  "agent_graph_runtime_persistence_wired=false" \
  "agent_graph_runtime_binder_published=false" \
  "agent_graph_runtime_production_wired=false" \
  "typed_node_executor_contract_defined=true" \
  "typed_node_executor_schema_count=11" \
  "typed_node_executor_debug_count=7" \
  "typed_node_executor_exact_class_verified=true" \
  "typed_node_executor_context_verified=true" \
  "typed_node_executor_policy_approval_verified=true" \
  "typed_node_executor_effect_fail_closed_verified=true" \
  "typed_node_executor_verification_verified=true" \
  "typed_node_executor_summary_verified=true" \
  "typed_node_executor_unsupported_fail_closed_verified=true" \
  "typed_node_executor_android13_arm64_verified=true" \
  "typed_node_executor_graph_dispatch_enabled=false" \
  "typed_node_executor_production_wired=false" \
  "checkpoint_serializer_defined=true" \
  "checkpoint_serializer_registered_dto_verified=true" \
  "checkpoint_serializer_canonical_digest_verified=true" \
  "checkpoint_serializer_malformed_unknown_rejected=true" \
  "checkpoint_serializer_size_depth_limit_verified=true" \
  "checkpoint_serializer_security_corpus_verified=true" \
  "checkpoint_serializer_android13_arm64_verified=true" \
  "checkpoint_serializer_java_serialization_enabled=false" \
  "node_retry_policy_defined=true" \
  "node_timeout_policy_defined=true" \
  "backoff_deterministic_bounded_verified=true" \
  "timeout_deadline_clamp_verified=true" \
  "retry_attempt_budget_verified=true" \
  "effect_idempotency_reconcile_gate_verified=true" \
  "retry_deadline_fail_closed_verified=true" \
  "retry_timeout_policy_android13_arm64_verified=true" \
  "retry_timeout_policy_runtime_wired=false" \
  "approval_interrupt_record_defined=true" \
  "approval_interrupt_binding_verified=true" \
  "approval_interrupt_checkpoint_roundtrip_verified=true" \
  "approval_interrupt_trusted_decision_verified=true" \
  "approval_resume_owner_plan_context_policy_verified=true" \
  "approval_resume_safety_revalidation_verified=true" \
  "approval_resume_expiry_verified=true" \
  "approval_interrupt_android13_arm64_verified=true" \
  "approval_interrupt_persistence_wired=false" \
  "approval_grant_service_published=false" \
  "effect_batch_defined=true" \
  "effect_dependency_plan_verified=true" \
  "effect_resource_conflict_serialized=true" \
  "effect_adapter_registry_profile_isolation_verified=true" \
  "effect_prepare_all_required_verified=true" \
  "effect_optional_degradation_verified=true" \
  "effect_independent_observation_verified=true" \
  "effect_coordinator_android13_arm64_verified=true" \
  "effect_coordinator_graph_wired=false" \
  "effect_coordinator_persistence_wired=false" \
  "production_effect_adapter_registered=false" \
  "production_effect_dispatch_enabled=false" \
  "effect_verification_reconciliation_wired=false" \
  "effect_verifier_defined=true" \
  "effect_verification_policies_verified=true" \
  "effect_state_separation_verified=true" \
  "effect_unknown_reconciliation_verified=true" \
  "effect_verified_redispatch_blocked=true" \
  "effect_production_readback_fail_closed=true" \
  "effect_verification_android13_arm64_verified=true" \
  "effect_verification_reconciliation_runtime_wired=false" \
  "effect_verification_scheduler_wired=false" \
  "effect_verification_persistence_wired=false" \
  "effect_verification_production_readback_wired=false" \
  "effect_verification_graph_wired=false" \
  "compensation_planner_defined=true" \
  "compensation_absolute_before_verified=true" \
  "compensation_reverse_dependency_verified=true" \
  "compensation_irreversible_rejected=true" \
  "undo_ttl_governance_verified=true" \
  "undo_new_governed_task_verified=true" \
  "undo_idempotent_replay_verified=true" \
  "undo_production_fail_closed=true" \
  "compensation_undo_android13_arm64_verified=true" \
  "compensation_undo_runtime_wired=false" \
  "compensation_undo_persistence_wired=false" \
  "undo_binder_service_published=false" \
  "compensation_dispatch_enabled=false" \
  "production_compensation_authority_wired=false" \
  "effect_dispatch_enabled=false" \
  "graph_restart_reconciler_defined=true" \
  "graph_restart_room_v4_repository_verified=true" \
  "graph_restart_waiting_recovered=true" \
  "graph_restart_executing_reconciled=true" \
  "graph_restart_unknown_effect_reconciled=true" \
  "graph_restart_approval_undo_revalidation_verified=true" \
  "graph_restart_checkpoint_mismatch_stuck=true" \
  "graph_restart_continue_after_revalidate_verified=true" \
  "graph_restart_process_death_verified=true" \
  "graph_restart_idempotent_reopen_verified=true" \
  "graph_restart_audit_exactly_once_verified=true" \
  "graph_restart_historical_digest_replay_verified=true" \
  "graph_restart_side_effect_count=0" \
  "graph_restart_android13_arm64_verified=true" \
  "graph_restart_repository_implementation_available=true" \
  "graph_restart_runtime_wired=false" \
  "graph_restart_binder_published=false" \
  "graph_restart_executor_dispatch_enabled=false" \
  "graph_restart_effect_dispatch_enabled=false" \
  "graph_restart_production_wired=false" \
  "agent_graph_runtime_persistence_wired=false" \
  "production_effect_dispatch_enabled=false" \
  "network_accessed=false" \
  "model_invoked=false" \
  "room_schema_version=4" \
  "room_table_count=13" \
  "room_wal_enabled=true" \
  "room_migration_1_2_verified=true" \
  "room_migration_2_3_verified=true" \
  "room_migration_3_4_verified=true" \
  "legacy_task_preserved=true" \
  "legacy_approval_preserved=true" \
  "legacy_event_cursor_preserved=true" \
  "legacy_runtime_session_preserved=true" \
  "legacy_session_v1_exposure_blocked=true" \
  "event_cursor_schema_v3_verified=true" \
  "event_cursor_schema_ready=true" \
  "room_schema_v4_verified=true" \
  "room_v4_foreign_keys_verified=true" \
  "room_v4_query_index_verified=true" \
  "room_v4_crash_transaction_rollback_verified=true" \
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
  "skill_governance_readiness_snapshot_wired=true" \
  "skill_governance_readiness_log_verified=true" \
  "skill_governance_readiness_dumpsys_verified=true" \
  "skill_governance_activation_allowed=false" \
  "bounded_built_in_skill_runtime_implementation_available=true" \
  "compiled_built_in_skill_count=3" \
  "compile_time_skill_signer_evidence_available=true" \
  "skill_artifact_cryptographic_verification_performed=false" \
  "fixed_governance_middleware_implementation_available=true" \
  "governance_middleware_stage_count=9" \
  "governance_middleware_order_fixed=true" \
  "skill_lifecycle_store_implemented=false" \
  "skill_revocation_configured=false" \
  "skill_rollback_configured=false" \
  "skill_sandbox_configured=false" \
  "governance_production_authorities_wired=false" \
  "skill_route_owner_registry_wired=false" \
  "skill_governance_middleware_production_wired=false" \
  "skill_governance_audit_persistence_wired=false" \
  "skill_dispatcher_production_wired=false" \
  "skill_dynamic_loading_enabled=false" \
  "raw_skill_output_stored=false" \
  "skill_network_access_enabled=false" \
  "skill_governance_activation_blockers=ARTIFACT_CRYPTO_VERIFIER_NOT_CONFIGURED,SKILL_LIFECYCLE_STORE_NOT_IMPLEMENTED,SKILL_REVOCATION_NOT_CONFIGURED,SKILL_ROLLBACK_NOT_CONFIGURED,SKILL_SANDBOX_NOT_CONFIGURED,GOVERNANCE_AUTHORITIES_NOT_WIRED,ROUTE_OWNER_REGISTRY_NOT_WIRED,MIDDLEWARE_CHAIN_NOT_WIRED,AUDIT_PERSISTENCE_NOT_WIRED,SKILL_DISPATCHER_NOT_WIRED" \
  "runtime_acceptance_snapshot_wired=true" \
  "runtime_acceptance_log_verified=true" \
  "runtime_acceptance_dumpsys_verified=true" \
  "core_software_baseline_ready=true" \
  "r7_application_integration_complete=true" \
  "client2_binder_migration_complete=true" \
  "api33_end_to_end_acceptance_complete=true" \
  "production_activation_allowed=false" \
  "target_hardware_validated=false" \
  "target_system_integration_owner_resolved=false" \
  "typed_binder_integrated=true" \
  "trusted_governance_integrated=true" \
  "durable_workflow_foundation_ready=true" \
  "standard_artifact_count=3" \
  "signature_protected_service_count=3" \
  "runtime_acceptance_blockers=TARGET_SYSTEM_INTEGRATION_OWNER_UNRESOLVED,PRODUCTION_EFFECT_DELIVERY_BLOCKED,PRODUCTION_MODEL_RUNTIME_BLOCKED,PRODUCTION_EVENT_RUNTIME_BLOCKED,PRODUCTION_MEMORY_RUNTIME_BLOCKED,PRODUCTION_SKILL_GOVERNANCE_BLOCKED,TARGET_HARDWARE_NOT_VALIDATED" \
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
  "model_contract_v2_defined=true" \
  "model_request_v2_fields_verified=true" \
  "model_result_v2_binding_verified=true" \
  "model_privacy_fallback_fail_closed=true" \
  "model_raw_content_accepted=false" \
  "model_provider_registry_wired=false" \
  "model_policy_router_wired=false" \
  "model_contract_v2_android13_arm64_verified=true" \
  "model_provider_registry_defined=true" \
  "model_provider_catalog_verified=true" \
  "model_provider_count=4" \
  "model_provider_health_freshness_verified=true" \
  "model_provider_health_replay_verified=true" \
  "model_provider_availability_separation_verified=true" \
  "model_provider_placeholder_fail_closed=true" \
  "model_contract_test_available_count=1" \
  "model_development_available_count=1" \
  "model_production_ready_count=0" \
  "model_provider_registry_android13_arm64_verified=true" \
  "model_provider_registry_runtime_wired=false" \
  "model_policy_router_defined=true" \
  "model_policy_router_privacy_network_thermal_verified=true" \
  "model_policy_router_latency_capability_quota_verified=true" \
  "model_policy_router_fallback_bounded=true" \
  "model_policy_router_no_action_authority=true" \
  "model_policy_router_android13_arm64_verified=true" \
  "model_policy_router_runtime_wired=false" \
  "provider_invoked=false" \
  "model_invoked=false" \
  "network_accessed=false" \
  "local_model_provider_verified=true" \
  "local_model_provider_lifecycle_verified=true" \
  "local_model_provider_stream_limit_verified=true" \
  "local_model_provider_cancel_verified=true" \
  "local_model_provider_deadline_verified=true" \
  "local_model_provider_overflow_rejected=true" \
  "local_model_provider_profile_boundary_verified=true" \
  "local_model_provider_registry_boundary_verified=true" \
  "local_model_provider_debug_only=true" \
  "local_model_provider_release_source_absent=true" \
  "local_model_provider_runtime_wired=false" \
  "local_model_provider_vendor_npu_fallback_enabled=false" \
  "production_inference_enabled=false" \
  "raw_model_content_logged=false" \
  "structured_model_output_verified=true" \
  "model_output_catalog_binding_verified=true" \
  "model_output_unknown_capability_rejected=true" \
  "model_output_no_action_authority=true" \
  "model_output_schema_runtime_wired=false" \
  "structured_model_output_android13_arm64_verified=true" \
  "security_aidl_parcel_inventory_complete=true" \
  "security_host_path_oversize_aggregate_verified=true" \
  "security_android_debug_probe_available=true" \
  "security_android_debug_probe_executed=true" \
  "security_android13_arm64_verified=true" \
  "security_coverage_guided_fuzz_complete=false" \
  "security_binder_calling_uid_spoof_android_verified=false" \
  "security_package_signature_cryptographically_verified=false" \
  "scenario_evaluation_verified=true" \
  "evaluation_corpus_verified=true" \
  "evaluation_metrics_verified=true" \
  "evaluation_boundary_verified=true" \
  "evaluation_case_count=12" \
  "intent_accuracy_permille=1000" \
  "unsafe_proposal_rate_permille=0" \
  "invalid_schema_rate_permille=0" \
  "fallback_rate_permille=0" \
  "scenario_evaluation_runtime_wired=false" \
  "raw_evaluation_content_logged=false" \
  "scenario_evaluation_android13_arm64_verified=true" \
  "model_resource_admission_verified=true" \
  "foreground_vehicle_priority_verified=true" \
  "thermal_degradation_verified=true" \
  "thermal_resource_fail_closed_verified=true" \
  "admission_boundary_verified=true" \
  "resource_admission_runtime_wired=false" \
  "model_resource_admission_android13_arm64_verified=true" \
  "performance_budget_contract_verified=true" \
  "performance_budget_catalog_verified=true" \
  "performance_budget_report_validation_verified=true" \
  "performance_budget_boundary_verified=true" \
  "performance_budget_category_count=7" \
  "performance_budget_metric_count=10" \
  "performance_budget_target_measurement_complete=false" \
  "performance_budget_runtime_wired=false" \
  "performance_budget_android13_arm64_verified=true" \
  "stability_fault_matrix_contract_verified=true" \
  "stability_matrix_verified=true" \
  "stability_report_validation_verified=true" \
  "stability_boundary_verified=true" \
  "stability_workload_count=3" \
  "stability_fault_count=6" \
  "stability_matrix_case_count=18" \
  "stability_target_72h_complete=false" \
  "stability_fault_injection_runtime_wired=false" \
  "stability_android13_arm64_verified=true" \
  "privacy_redacted_audit_projection_verified=true" \
  "privacy_surface_count=12" \
  "privacy_unresolved_surface_count=2" \
  "privacy_current_policy_admitted=false" \
  "privacy_raw_user_text_logged=false" \
  "privacy_raw_model_output_logged=false" \
  "privacy_raw_vehicle_payload_logged=false" \
  "privacy_location_logged=false" \
  "privacy_owner_reference_logged=false" \
  "privacy_authorization_digest_logged=false" \
  "privacy_consent_digest_logged=false" \
  "privacy_repository_mutation_wired=false" \
  "privacy_runtime_lifecycle_wiring_complete=false" \
  "privacy_android_debug_probe_available=true" \
  "privacy_android_debug_probe_executed=true" \
  "privacy_android13_arm64_verified=true" \
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
  "skill_runtime_contract_verified=true" \
  "skill_catalog_verified=true" \
  "skill_signer_allowlist_verified=true" \
  "skill_manifest_schema_verified=true" \
  "skill_invocation_idempotency_verified=true" \
  "skill_capability_policy_verified=true" \
  "skill_safety_state_verified=true" \
  "skill_owner_isolation_verified=true" \
  "skill_cancel_idempotency_verified=true" \
  "skill_record_bounds_verified=true" \
  "skill_process_only=true" \
  "skill_manifest_signer_evidence_compile_time_only=true" \
  "skill_dynamic_loading_enabled=false" \
  "skill_cryptographic_artifact_verification_performed=false" \
  "skill_production_service_wired=false" \
  "raw_skill_input_stored=false" \
  "skill_network_access_enabled=false" \
  "governance_middleware_contract_verified=true" \
  "governance_middleware_order_verified=true" \
  "governance_middleware_allow_path_verified=true" \
  "governance_middleware_first_rejection_verified=true" \
  "governance_middleware_audit_finalizer_verified=true" \
  "governance_middleware_privacy_verified=true" \
  "governance_middleware_policy_verified=true" \
  "governance_middleware_qos_verified=true" \
  "governance_middleware_output_guard_verified=true" \
  "governance_middleware_audit_bounds_verified=true" \
  "governance_middleware_process_only=true" \
  "governance_middleware_production_wired=false" \
  "governance_dispatch_execution_enabled=false" \
  "governance_service_dispatch_triggered=false" \
  "raw_governance_input_stored=false" \
  "raw_governance_output_stored=false" \
  "governance_audit_persistence_wired=false" \
  "governance_network_access_enabled=false" \
  "tool_manifest_contract_defined=true" \
  "tool_manifest_schema_version=1" \
  "tool_manifest_contract_digest_verified=true" \
  "tool_schema_input_output_verified=true" \
  "tool_schema_unknown_field_rejected=true" \
  "tool_schema_type_bounds_verified=true" \
  "tool_manifest_health_fail_closed=true" \
  "tool_manifest_android13_arm64_verified=true" \
  "tool_registry_contract_defined=true" \
  "tool_resolver_contract_defined=true" \
  "tool_health_dynamic_snapshot_defined=true" \
  "tool_registry_probe_registration_count=2" \
  "tool_registry_digest_verified=true" \
  "tool_registry_version_conflict_rejected=true" \
  "tool_resolver_highest_version_deterministic=true" \
  "tool_resolver_states_separated=true" \
  "tool_resolver_unhealthy_no_fallback=true" \
  "tool_health_fail_closed=true" \
  "tool_registry_android13_arm64_verified=true" \
  "tool_registry_published=false" \
  "tool_resolver_published=false" \
  "tool_registry_runtime_wired=false" \
  "tool_rule_set_contract_defined=true" \
  "tool_rule_type_count=6" \
  "tool_rule_set_digest_verified=true" \
  "tool_rule_init_child_conditional_verified=true" \
  "tool_rule_model_intersection_fail_closed=true" \
  "tool_rule_terminal_requirements_verified=true" \
  "tool_rule_approval_annotation_fail_closed=true" \
  "tool_rule_solver_android13_arm64_verified=true" \
  "tool_rule_solver_published=false" \
  "tool_rule_solver_runtime_wired=false" \
  "tool_executor_contract_defined=true" \
  "tool_invocation_context_defined=true" \
  "built_in_allowlist_enforced=true" \
  "built_in_signer_artifact_bound=true" \
  "tool_executor_success_verified=true" \
  "tool_executor_deadline_cancel_verified=true" \
  "tool_executor_output_limit_verified=true" \
  "tool_executor_audit_bounded_verified=true" \
  "tool_executor_android13_arm64_verified=true" \
  "tool_executor_runtime_wired=false" \
  "skill_artifact_hash_verified=true" \
  "skill_manifest_digest_verified=true" \
  "skill_signer_policy_verified=true" \
  "skill_runtime_version_verified=true" \
  "skill_capability_policy_verified=true" \
  "skill_revocation_downgrade_fail_closed=true" \
  "skill_package_verifier_android13_arm64_verified=true" \
  "trusted_skill_evidence_source_configured=false" \
  "package_signature_cryptographically_verified=false" \
  "dynamic_skill_loading_enabled=false" \
  "skill_execution_enabled=false" \
  "skill_package_verifier_runtime_wired=false" \
  "working_memory_store_defined=true" \
  "working_memory_session_scope_verified=true" \
  "working_memory_ttl_verified=true" \
  "working_memory_item_limit_verified=true" \
  "working_memory_byte_limit_verified=true" \
  "working_memory_token_limit_verified=true" \
  "working_memory_terminal_cleanup_verified=true" \
  "working_memory_payload_zeroized_on_cleanup=true" \
  "working_memory_android13_arm64_verified=true" \
  "working_memory_process_local=true" \
  "working_memory_persistence_wired=false" \
  "working_memory_runtime_wired=false" \
  "working_memory_model_context_published=false" \
  "working_memory_tokenizer_verified=false" \
  "working_memory_content_logged=false" \
  "profile_memory_store_defined=true" \
  "profile_memory_explicit_consent_verified=true" \
  "profile_memory_field_allowlist_verified=true" \
  "profile_memory_user_seat_scope_verified=true" \
  "profile_memory_read_update_verified=true" \
  "profile_memory_delete_verified=true" \
  "profile_memory_export_verified=true" \
  "profile_memory_consent_revocation_fail_closed=true" \
  "profile_memory_encryption_owner_gate_verified=true" \
  "profile_memory_sealed_payload_zeroized=true" \
  "profile_memory_android13_arm64_verified=true" \
  "profile_memory_process_local=true" \
  "profile_memory_durable_storage_wired=false" \
  "profile_memory_production_encryption_owner_configured=false" \
  "profile_memory_consent_authority_production_wired=false" \
  "profile_memory_runtime_wired=false" \
  "profile_memory_content_logged=false" \
  "episodic_memory_store_defined=true" \
  "episodic_memory_summary_result_only_verified=true" \
  "episodic_memory_owner_isolation_verified=true" \
  "episodic_memory_read_fail_closed=true" \
  "episodic_memory_policy_fail_closed=true" \
  "episodic_memory_retention_verified=true" \
  "episodic_memory_capacity_verified=true" \
  "episodic_memory_erase_verified=true" \
  "episodic_memory_erase_fail_closed=true" \
  "episodic_memory_android13_arm64_verified=true" \
  "episodic_memory_process_local=true" \
  "episodic_memory_raw_continuous_signal_stored=false" \
  "episodic_memory_arbitrary_payload_stored=false" \
  "episodic_memory_persistence_wired=false" \
  "episodic_memory_runtime_wired=false" \
  "episodic_memory_model_context_published=false" \
  "episodic_memory_production_policy_authority_wired=false" \
  "episodic_memory_production_read_authority_wired=false" \
  "episodic_memory_production_erase_authority_wired=false" \
  "episodic_memory_content_logged=false" \
  "context_budget_manager_defined=true" \
  "context_budget_category_allocation_verified=true" \
  "context_budget_dual_limit_verified=true" \
  "context_budget_deterministic_overflow_verified=true" \
  "context_budget_required_fail_closed=true" \
  "context_budget_android13_arm64_verified=true" \
  "context_budget_decision_only=true" \
  "context_budget_text_payload_accepted=false" \
  "context_budget_tokenizer_wired=false" \
  "context_budget_summarizer_wired=false" \
  "context_budget_production_authority_wired=false" \
  "context_budget_runtime_wired=false" \
  "context_budget_content_logged=false" \
  "memory_consent_controller_defined=true" \
  "memory_consent_source_visibility_verified=true" \
  "memory_consent_disable_verified=true" \
  "memory_consent_preference_clear_verified=true" \
  "memory_consent_moving_restriction_verified=true" \
  "memory_consent_android13_arm64_verified=true" \
  "memory_consent_hmi_projection_only=true" \
  "memory_consent_repository_mutation_wired=false" \
  "memory_consent_production_authority_wired=false" \
  "memory_consent_runtime_wired=false" \
  "memory_consent_model_context_published=false" \
  "memory_consent_content_logged=false" \
  "event_broker_interface_defined=true" \
  "event_broker_typed_topics_verified=true" \
  "event_broker_append_before_notify_verified=true" \
  "event_broker_bounded_replay_filter_verified=true" \
  "event_broker_identity_policy_verified=true" \
  "event_broker_subscription_lifecycle_verified=true" \
  "event_broker_android13_arm64_verified=true" \
  "event_broker_process_local=true" \
  "event_broker_durable_persistence_wired=false" \
  "event_broker_dds_transport_wired=false" \
  "event_broker_production_published=false" \
  "event_broker_runtime_wired=false" \
  "event_qos_contract_defined=true" \
  "event_qos_policies_verified=true" \
  "event_qos_critical_no_silent_drop_verified=true" \
  "event_qos_deadline_priority_verified=true" \
  "event_qos_consumer_isolation_verified=true" \
  "event_qos_android13_arm64_verified=true" \
  "event_qos_process_local=true" \
  "event_qos_broker_wired=false" \
  "event_qos_durable_persistence_wired=false" \
  "event_qos_production_middleware_wired=false" \
  "trigger_rule_manifest_defined=true" \
  "trigger_rule_manifest_verified=true" \
  "trigger_threshold_window_debounce_verified=true" \
  "trigger_cooldown_scope_verified=true" \
  "trigger_input_fail_closed_verified=true" \
  "trigger_suggestion_only_verified=true" \
  "trigger_engine_android13_arm64_verified=true" \
  "trigger_engine_process_local=true" \
  "trigger_cooldown_persistence_wired=false" \
  "trigger_source_adapter_wired=false" \
  "trigger_auto_execution_enabled=false" \
  "trigger_runtime_wired=false" \
  "proactive_consent_policy_defined=true" \
  "proactive_grant_binding_verified=true" \
  "proactive_high_critical_generic_grant_blocked=true" \
  "proactive_grant_ttl_revoke_verified=true" \
  "proactive_policy_fail_closed_verified=true" \
  "proactive_consent_android13_arm64_verified=true" \
  "proactive_policy_process_local=true" \
  "proactive_grant_persistence_wired=false" \
  "proactive_consent_authority_wired=false" \
  "proactive_auto_execution_enabled=false" \
  "proactive_runtime_wired=false" \
  "tool_approval_authority_available=false" \
  "tool_execution_enabled=false" \
  "production_tool_execution_enabled=false" \
  "production_tool_registered=false" \
  "production_tool_artifact_loaded=false" \
  "os_virtualization_enabled=false" \
  "subprocess_started=false" \
  "dynamic_class_loading_enabled=false" \
  "vehicle_readback_accessed=false" \
  "npu_accessed=false" \
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

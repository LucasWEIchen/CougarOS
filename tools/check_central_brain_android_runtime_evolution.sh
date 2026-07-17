#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android runtime evolution pattern '$pattern' in $path" >&2
    exit 1
  fi
}

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android runtime evolution file: $path" >&2
    exit 1
  fi
}

PLAN="docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md"
for state in contract_defined prototype_implemented android_integrated hardware_validated production_qualified; do
  require_text "$PLAN" "$state"
done

for issue in ISSUE-021 ISSUE-022 ISSUE-023 ISSUE-024 ISSUE-025; do
  require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "$issue"
  require_text "$PLAN" "$issue"
done

for deviation in DEV-018 DEV-019; do
  require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "$deviation"
  require_text "$PLAN" "$deviation"
done

require_text "$PLAN" "只聚焦 Android"
require_text "$PLAN" "不开发 Linux 前端"
require_text "$PLAN" "不修改厂商 Android Framework"
require_text "$PLAN" "Vendor NPU provider 保持 empty adapter"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "Android Runtime Evolution Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "2026-07-12 当前实施阶段"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "| R7 |"

for path in \
  central-brain/android-runtime/README.md \
  central-brain/android-runtime/central-brain-sdk/build.gradle.kts \
  central-brain/android-runtime/runtime-service/build.gradle.kts \
  central-brain/android-runtime/demo-hmi/build.gradle.kts \
  tools/build_central_brain_android_runtime.sh \
  tools/install_central_brain_android_runtime.sh \
  tools/check_central_brain_android_aidl_contract.sh \
  tools/check_central_brain_android_session_contract.sh \
  tools/check_central_brain_android_plan_contract.sh \
  tools/check_central_brain_android_event_contract.sh \
  tools/check_central_brain_android_effect_contract.sh \
  tools/check_central_brain_android_room_v4.sh \
  tools/check_central_brain_runtime_contract_v2.sh \
  tools/check_central_brain_android_binder_runtime.sh \
  tools/check_central_brain_android_binder_lifecycle.sh \
  tools/check_central_brain_android_job_supervisor.sh \
  tools/check_central_brain_android_capability_policy.sh \
  tools/check_central_brain_android_action_governance.sh \
  tools/check_central_brain_android_durable_schema.sh \
  tools/check_central_brain_android_durable_repository.sh \
  tools/check_central_brain_android_durable_runtime_wiring.sh \
  tools/check_central_brain_android_durable_approval.sh \
  tools/check_central_brain_android_restart_reconciliation.sh \
  tools/check_central_brain_android_effect_outbox.sh \
  tools/check_central_brain_android_effect_outbox_terminal.sh \
  tools/check_central_brain_android_effect_adapter_contract.sh \
  tools/check_central_brain_android_effect_activation_gate.sh \
  tools/check_central_brain_android_effect_gate_wiring.sh \
  tools/check_central_brain_android_delivery_handoff.sh \
  tools/check_central_brain_blackbox_engineering_plan.sh \
  tools/check_central_brain_native_runtime.sh \
  tools/check_central_brain_android_native_runtime_integration.sh \
  tools/check_central_brain_android_blackbox_preflight.sh \
  tools/check_central_brain_android_hybrid_delivery.sh \
  tools/check_central_brain_windows_adb_compatibility.sh \
  tools/check_central_brain_python_prototype_retirement.sh \
  tools/verify_central_brain_native_runtime_apk.sh \
  tools/test_central_brain_android_native_runtime.sh \
  tools/preflight_central_brain_android13_blackbox.sh \
  tools/test_central_brain_android_blackbox_acceptance.sh \
  tools/test_central_brain_android_blackbox_signer_guard.sh \
  tools/package_central_brain_android_hybrid_delivery.sh \
  tools/install_central_brain_android_hybrid_delivery.sh \
  tools/test_central_brain_android_capability_policy.sh \
  tools/test_central_brain_android_binder_lifecycle.sh \
  tools/check_central_brain_android_runtime_gradle.sh; do
  require_file "$path"
done

require_text "$PLAN" "R1A Gradle foundation"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R1A Android Gradle foundation trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "central-brain-sdk-debug.aar"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R1A Gradle Foundation Driver/HAL Evidence"
require_text "$PLAN" "R1B device lifecycle check"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R1B Android device lifecycle trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "r1_api33_exit_criteria_met=false"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R1B Device Lifecycle Driver/HAL Evidence"
require_text "$PLAN" "R1C API 33 exit"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R1C Android 13 exit trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "r1_api33_exit_criteria_met=true"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R1C API 33 Exit Driver/HAL Evidence"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "| R1 | Android Gradle 多模块交付骨架 | AI SDK AAR、Runtime Service APK、Demo HMI APK | 已完成"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R1 Gradle 多模块、API 33"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R1 SDK AAR、Runtime Service APK、Demo HMI APK"
require_file "docs/CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md"
require_text "$PLAN" "R2A compiled AIDL contract"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R2A compiled AIDL contract trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R2A AIDL Contract Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R2 Typed AIDL Contract"
require_text "$PLAN" "R2B typed Binder runtime"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R2B typed Binder runtime trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "typed_binder_connected=true"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R2B Binder Runtime Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R2C 已通过 API 33"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R2C"
require_text "$PLAN" "R2C Binder lifecycle/race instrumentation"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R2C Binder lifecycle and race trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "r2_binder_exit_criteria_met=true"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R2C Binder Lifecycle Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "| R2 | Typed/async Protocol Binding | production/diagnostic AIDL、Parcelable、callback/cancel/death | 已完成"
require_text "docs/CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md" "R2C Lifecycle And Race Evidence"
require_text "central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainSdk.java" 'MATURITY = "android_integrated"'
require_text "$PLAN" "R3A Job Supervisor foundation"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R3A Job Supervisor foundation trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "trusted_caller_identity_resolved=true"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R3A Job Supervisor Driver/HAL Boundary"
require_text "$PLAN" "R3B capability policy"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R3B capability policy trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "unknown_client_default_deny_verified=true"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R3B Capability Policy Driver/HAL Boundary"
require_text "$PLAN" "R3C1 action governance core"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R3C1 action governance core trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R3C1 Action Governance Core"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R3C1 Action Governance Core Driver/HAL Boundary"
require_text "$PLAN" "R3C2 typed Governance Binder"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R3C2 typed Governance Binder trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R3C2 Typed Governance Binder"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R3C2 Governance Binder Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "| R3 | Android Runtime 核心 | Job Supervisor、可信 Binder 身份、capability/policy | 已完成"
require_text "central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainSdk.java" 'EVOLUTION_STAGE = "R4_DURABLE_WORKFLOW"'
require_text "$PLAN" "R4A Room durable schema"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4A Room durable schema trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4A Room Durable Schema"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4A Room Schema Driver/HAL Boundary"
require_text "$PLAN" "R4B1 durable task admission"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4B1 durable task admission trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4B1 Durable Task Admission"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4B1 Durable Repository Driver/HAL Boundary"
require_text "$PLAN" "R4B2 durable Runtime wiring"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4B2 durable Runtime wiring trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4B2 Durable Runtime Wiring"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4B2 Durable Runtime Driver/HAL Boundary"
require_text "$PLAN" "R4B3 durable approval"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4B3 durable approval trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4B3 Durable Approval"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4B3 Durable Approval Driver/HAL Boundary"
require_text "$PLAN" "R4C1 fail-closed restart reconciliation"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4C1 fail-closed restart reconciliation trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4C1 Fail-Closed Restart Reconciliation"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4C1 Restart Reconciliation Driver/HAL Boundary"
require_text "$PLAN" "R4C2A effect prepare and claim"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4C2A effect prepare and claim trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4C2A Effect Prepare And Claim"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4C2A Effect Outbox Driver/HAL Boundary"
require_text "$PLAN" "R4C2B effect retry and terminal states"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4C2B effect retry and terminal state trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4C2B Effect Retry And Terminal States"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4C2B Effect Terminal State Driver/HAL Boundary"
require_text "$PLAN" "R4C3A effect adapter contract and fault matrix"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4C3A effect adapter contract trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4C3A Effect Adapter Contract"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4C3A Effect Adapter Driver/HAL Boundary"
require_text "$PLAN" "R4C3B effect material source and activation gate"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4C3B effect material activation trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4C3B Effect Material Activation Gate"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4C3B Effect Material Activation Driver/HAL Boundary"
require_text "$PLAN" "R4C3C production fail-closed activation visibility"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4C3C production fail-closed activation visibility trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4C3C Production Fail-Closed Activation Visibility"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4C3C Production Gate Visibility Driver/HAL Boundary"

bash "$ROOT_DIR/tools/check_central_brain_android_job_supervisor.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_session_contract.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_plan_contract.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_event_contract.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_effect_contract.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_room_v4.sh"
bash "$ROOT_DIR/tools/check_central_brain_runtime_contract_v2.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_vehicle_signal_schema.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_vehicle_capability_catalog.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_vehicle_digital_twin.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_context_snapshot.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_scenario_manifest.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_scenario_resolver.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_scenario_plan_compiler.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_simulated_effect_adapter.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_simulated_hvac_adapter.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_simulated_seat_adapter.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_simulated_media_navigation_adapters.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_debug_simulation_controller.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_agent_graph_runtime.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_typed_node_executors.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_checkpoint_serializer.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_retry_timeout_policy.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_approval_interrupt.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_effect_coordinator.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_effect_verification.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_compensation_undo.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_graph_restart_recovery.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_capability_policy.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_action_governance.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_durable_schema.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_durable_repository.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_durable_runtime_wiring.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_durable_approval.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_restart_reconciliation.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_effect_outbox.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_effect_outbox_terminal.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_effect_adapter_contract.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_effect_activation_gate.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_effect_gate_wiring.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_model_provider_contract.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_inference_scheduler.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_deterministic_model_provider.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_test_model_router.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_model_runtime_readiness.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_target_deployment.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_event_runtime.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_event_persistence_schema.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_durable_event_repository.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_event_runtime_readiness.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_memory_lifecycle.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_memory_runtime_readiness.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_built_in_skill_runtime.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_governance_middleware.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_skill_governance_readiness.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_runtime_acceptance.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_binder.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_hmi_reducer.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_intent_shell.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_hvac_surface.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_seat_surface.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_execution_timeline.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_recovery_ux.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_driving_restriction.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_engineer_simulation.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_scenario_sync.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_accessibility_display.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_p4_acceptance.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_tool_manifest.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_application_acceptance.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_delivery_handoff.sh"
bash "$ROOT_DIR/tools/check_central_brain_blackbox_engineering_plan.sh"
bash "$ROOT_DIR/tools/check_central_brain_native_runtime.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_native_runtime_integration.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_blackbox_preflight.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_hybrid_delivery.sh"
bash "$ROOT_DIR/tools/check_central_brain_windows_adb_compatibility.sh"
bash "$ROOT_DIR/tools/check_central_brain_github_remote_testing.sh"
bash "$ROOT_DIR/tools/check_central_brain_python_prototype_retirement.sh"
bash "$ROOT_DIR/tools/check_central_brain_aios_stage2_design.sh"
bash "$ROOT_DIR/tools/check_central_brain_root_readme.sh"

bash "$ROOT_DIR/tools/check_central_brain_android_runtime_gradle.sh"

echo "Central Brain Android runtime evolution baseline check passed"

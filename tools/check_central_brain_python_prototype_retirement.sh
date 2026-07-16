#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005/006, NV-F-001/011/012,
# NV-G-003/005/006/007, NV-P-002, KH-003/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

retired_paths=(
  central-brain/android-console
  central-brain/backend
  central-brain/bindings
  central-brain/deploy/linux
  central-brain/linux-cli
  central-brain/contracts/central_brain_api.json
  central-brain/contracts/central_brain_prototype_closure_plan.json
  central-brain/contracts/central_brain_prototype_completion_audit.json
  central-brain/contracts/central_brain_prototype_handoff_manifest.json
  docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md
  docs/CENTRAL_BRAIN_ARCHITECTURE_EXECUTION_PLAN.md
  docs/CENTRAL_BRAIN_KAKACLAW_REFERENCE_TEST_PLAN.md
  docs/CENTRAL_BRAIN_PLATFORM_DELTA.md
  docs/CENTRAL_BRAIN_PRODUCT_DESIGN.md
  docs/CENTRAL_BRAIN_PROTOTYPE_CLOSURE_PLAN.md
  docs/CENTRAL_BRAIN_PROTOTYPE_COMPLETION_AUDIT.md
  docs/CENTRAL_BRAIN_PROTOTYPE_HANDOFF_MANIFEST.md
  docs/CENTRAL_BRAIN_PROTOTYPE_MODULE_INTERFACE_MAP.md
  docs/CENTRAL_BRAIN_PROTOTYPE_USAGE.md
  docs/CENTRAL_BRAIN_REQUIREMENTS_BREAKDOWN.md
  docs/CENTRAL_BRAIN_SOFTWARE_DETAILED_DESIGN.md
  tools/build_central_brain_console.sh
  tools/install_central_brain_console.sh
  tools/run_central_brain_backend.sh
  tools/check_central_brain_binding_artifacts.sh
  tools/check_central_brain_linux_package_profile.sh
  tools/check_central_brain_linux_systemd_hardening.sh
  tools/check_central_brain_software_detailed_design.sh
  tools/smoke_central_brain_ollama_simulated_npu.sh
  tools/smoke_central_brain_semantic_gateway.sh
  tools/test_central_brain_bus.sh
)

for relative in "${retired_paths[@]}"; do
  if [[ -e "$ROOT_DIR/$relative" ]]; then
    echo "retired Python prototype path still exists: $relative" >&2
    exit 1
  fi
done

mapfile -t central_python < <(find "$ROOT_DIR/central-brain" -type f -name '*.py' -print | sort)
if (( ${#central_python[@]} != 0 )); then
  printf 'Python runtime files remain under central-brain:\n%s\n' "${central_python[*]}" >&2
  exit 1
fi

required_paths=(
  central-brain/android-runtime/central-brain-sdk/src/main/aidl/com/centralbrain/sdk/production/ICentralBrainRuntime.aidl
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProvider.java
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProviderProfiles.java
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/scheduler/InferenceResourceScheduler.java
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EmptyEffectMaterialSource.java
  central-brain/android-runtime/native-runtime/src/main/cpp/include/central_brain_native.h
  central-brain/android-runtime/native-runtime/src/main/cpp/central_brain_native.c
  apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/Client2ScenarioBridge.java
  central-brain/contracts/central_brain_android_b3_blackbox_acceptance.json
  central-brain/contracts/central_brain_android_r7c_acceptance.json
  central-brain/contracts/central_brain_github_remote_testing.json
  docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md
  docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md
  docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md
  tools/central_brain_android_delivery.py
  tools/central_brain_android_hybrid_delivery.py
)

for relative in "${required_paths[@]}"; do
  [[ -f "$ROOT_DIR/$relative" ]] || {
    echo "retained Android/hardware asset is missing: $relative" >&2
    exit 1
  }
done

active_files=(
  README.md
  central-brain/README.md
  docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md
  docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md
  docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md
  docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md
  docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md
  docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md
  docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md
  docs/CENTRAL_BRAIN_ROADMAP.md
  docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md
  docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md
  docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md
  docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md
  docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md
  docs/CENTRAL_BRAIN_MODULE_USE_CASE_DIAGRAM.md
  docs/CENTRAL_BRAIN_CLIENT2_APK_REVERSE_DEMO.md
  .github/workflows/central-brain-remote-test-contract.yml
)

for relative in "${active_files[@]}"; do
  [[ -f "$ROOT_DIR/$relative" ]] || {
    echo "active Android baseline file is missing: $relative" >&2
    exit 1
  }
done

if rg -n \
    'central-brain/(backend|android-console|bindings/(android|linux)|linux-cli|deploy/linux)|central_brain_prototype_(handoff|completion|closure)|CENTRAL_BRAIN_(PROTOTYPE_|SOFTWARE_DETAILED_DESIGN|PLATFORM_DELTA|ANDROID_SYSTEM_SERVICE_INTEGRATION|KAKACLAW_REFERENCE_TEST_PLAN)|CENTRAL_BRAIN_SIMULATED_NPU_BACKEND|ollama_simulated_npu' \
    "${active_files[@]/#/$ROOT_DIR/}"; then
  echo "active Android baseline still references retired Python prototype assets" >&2
  exit 1
fi

grep -Fq 'python_prototype_runtime_maintained=false' "$ROOT_DIR/README.md"
grep -Fq 'vendor.npu.empty' \
  "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProviderProfiles.java"
grep -Fq 'target_hardware_validated=false' "$ROOT_DIR/docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md"
grep -Fq 'driver_development_triggered=false' "$ROOT_DIR/docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md"
grep -Fq '不开发 Hypervisor' "$ROOT_DIR/docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md"

printf '%s\n' \
  'Central Brain Python prototype retirement check passed' \
  'python_prototype_runtime_maintained=false' \
  'central_brain_python_runtime_file_count=0' \
  'android_model_npu_contracts_retained=true' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'driver_development_triggered=false' \
  'virtualization_development_triggered=false'

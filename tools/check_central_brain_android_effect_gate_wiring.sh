#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004, FW-U-004/005, NV-F-001, NV-G-006/007, DEL-001/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SNAPSHOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectDeliveryActivationSnapshot.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
DIAGNOSTIC="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/DiagnosticProbeActivity.java"
SDK="central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainSdk.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android production effect gate file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android production effect gate pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in "$SNAPSHOT" "$RUNTIME" "$DIAGNOSTIC" "$PROBE" "$SDK" "$INSTALLER"; do
  require_file "$path"
done

require_text "$SNAPSHOT" "EffectDeliveryActivationSnapshot current()"
require_text "$SNAPSHOT" "new EmptyEffectMaterialSource()"
require_text "$SNAPSHOT" "evaluate("
require_text "$SNAPSHOT" "null,"
require_text "$SNAPSHOT" "current empty effect delivery configuration must remain blocked"
require_text "$SNAPSHOT" "isActivationAllowed()"
require_text "$SNAPSHOT" "isAdapterConfigured()"
require_text "$SNAPSHOT" "isMaterialDurable()"
require_text "$SNAPSHOT" "isApplyEnabled()"
require_text "$SNAPSHOT" "isStatusQueryEnabled()"
require_text "$SNAPSHOT" "diagnosticDetail()"

require_text "$RUNTIME" "EffectDeliveryActivationSnapshot.current()"
require_text "$RUNTIME" "evolution_stage="
require_text "$RUNTIME" "production_effect_activation_gate_wired=true"
require_text "$RUNTIME" "production_effect_delivery_activation_allowed="
require_text "$RUNTIME" "production_effect_adapter_configured="
require_text "$RUNTIME" "production_effect_material_source="
require_text "$RUNTIME" "production_effect_apply_enabled="
require_text "$RUNTIME" "production_effect_status_query_enabled="
require_text "$RUNTIME" "production_effect_activation_blockers="
require_text "$RUNTIME" "protected void dump(FileDescriptor fd, PrintWriter writer, String[] args)"

require_text "$DIAGNOSTIC" "EffectDeliveryActivationSnapshot.current()"
require_text "$DIAGNOSTIC" '"effect-delivery-activation"'
require_text "$DIAGNOSTIC" '"blocked"'
require_text "$DIAGNOSTIC" "effectDeliveryActivation.diagnosticDetail()"
require_text "$PROBE" "effect_delivery_activation_diagnostic_verified="
require_text "$PROBE" "material_source=empty.effect.material"
require_text "$PROBE" "ADAPTER_MISSING"
require_text "$PROBE" "MATERIAL_SOURCE_EMPTY"

if grep -Eq 'EffectAdapter|EffectMaterialSource|DurableEffectRepository|resolveInvocation|queryStatus' \
    "$ROOT_DIR/$RUNTIME"; then
  echo "production Runtime must only consume the immutable activation snapshot" >&2
  exit 1
fi
if grep -Eq 'resolveInvocation|queryStatus|\.apply\(' "$ROOT_DIR/$DIAGNOSTIC"; then
  echo "production diagnostics must remain observation-only" >&2
  exit 1
fi

for flag in \
  "effect_delivery_activation_diagnostic_verified=true" \
  "evolution_stage=R4_DURABLE_WORKFLOW" \
  "production_effect_activation_gate_wired=true" \
  "production_effect_delivery_activation_allowed=false" \
  "production_effect_adapter_configured=false" \
  "production_effect_material_source_id=empty.effect.material" \
  "production_effect_material_durable=false" \
  "production_effect_apply_enabled=false" \
  "production_effect_status_query_enabled=false" \
  "production_effect_gate_dumpsys_verified=true"; do
  require_text "$INSTALLER" "$flag"
done
require_text "$INSTALLER" "dumpsys activity service"
require_text "$INSTALLER" "production_effect_activation_blockers=ADAPTER_MISSING"
require_text "$INSTALLER" "r4_durable_workflow_exit_criteria_met="
require_text "$SDK" 'EVOLUTION_STAGE = "R4_DURABLE_WORKFLOW"'
require_text "$SDK" 'MATURITY = "android_integrated"'

if grep -R -Eiq \
    'ioctl|sysfs|/dev/|VehicleHal|CarPropertyManager|vendor sdk|SharedMemory|SocketCAN' \
    "$ROOT_DIR/$SNAPSHOT" "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$DIAGNOSTIC"; then
  echo "R4C3C production gate visibility unexpectedly references hardware" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R4C3C Production Fail-Closed Activation Visibility"
require_text "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md" "R4C3C production fail-closed activation visibility"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4C3C production fail-closed activation visibility trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R4C3C Production Activation Snapshot"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4C3C Production Fail-Closed Activation Visibility"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4C3C Production Gate Visibility Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R4C3C 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R4C3C 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "R4_DURABLE_WORKFLOW"

echo "Central Brain Android production effect gate wiring check passed"

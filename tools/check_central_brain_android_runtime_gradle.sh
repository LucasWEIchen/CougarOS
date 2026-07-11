#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/central-brain/android-runtime"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android runtime Gradle file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  central-brain/android-runtime/settings.gradle.kts \
  central-brain/android-runtime/build.gradle.kts \
  central-brain/android-runtime/gradle.properties \
  central-brain/android-runtime/gradle/libs.versions.toml \
  central-brain/android-runtime/gradle/wrapper/gradle-wrapper.jar \
  central-brain/android-runtime/gradle/wrapper/gradle-wrapper.properties \
  central-brain/android-runtime/gradlew \
  central-brain/android-runtime/central-brain-sdk/build.gradle.kts \
  central-brain/android-runtime/central-brain-sdk/src/main/AndroidManifest.xml \
  central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainSdk.java \
  central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainClient.java \
  central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainGovernanceClient.java \
  central-brain/android-runtime/runtime-service/build.gradle.kts \
  central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/CentralBrainDatabase.java \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/DurableTaskRepository.java \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/DurableDigest.java \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/DurableApprovalRepository.java \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/DurableEffectRepository.java \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectAdapter.java \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectAdapterContract.java \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectStatusReconciler.java \
  central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml \
  central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/RuntimeProbeActivity.java \
  central-brain/android-runtime/demo-hmi/build.gradle.kts \
  central-brain/android-runtime/demo-hmi/src/main/AndroidManifest.xml \
  central-brain/android-runtime/demo-hmi/src/main/java/com/centralbrain/demo/DemoActivity.java \
  central-brain/android-runtime/README.md \
  tools/build_central_brain_android_runtime.sh \
  tools/install_central_brain_android_runtime.sh \
  tools/check_central_brain_android_aidl_contract.sh \
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
  tools/test_central_brain_android_capability_policy.sh \
  tools/test_central_brain_android_binder_lifecycle.sh; do
  require_file "$path"
done

for module in central-brain-sdk runtime-service demo-hmi; do
  require_text "central-brain/android-runtime/settings.gradle.kts" "include(\":$module\")"
done

require_text "central-brain/android-runtime/gradle/libs.versions.toml" 'agp = "8.10.1"'
require_text "central-brain/android-runtime/gradle/wrapper/gradle-wrapper.properties" "gradle-8.11.1-bin.zip"
require_text "central-brain/android-runtime/gradle/wrapper/gradle-wrapper.properties" "distributionSha256Sum=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6"
for module in central-brain-sdk runtime-service demo-hmi; do
  require_text "central-brain/android-runtime/$module/build.gradle.kts" "minSdk = 33"
done
require_text "central-brain/android-runtime/runtime-service/build.gradle.kts" 'applicationId = "com.centralbrain.runtime"'
require_text "central-brain/android-runtime/demo-hmi/build.gradle.kts" 'applicationId = "com.centralbrain.demo"'
require_text "central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml" 'android:permission="com.centralbrain.permission.BIND_RUNTIME"'
require_text "central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java" "ICentralBrainRuntime.Stub"
require_text "central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java" "hardware_accessed=false"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" 'android:permission="android.permission.DUMP"'
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" 'android:exported="true"'
require_text "central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/RuntimeProbeActivity.java" "BuildConfig.DEBUG"
require_text "tools/install_central_brain_android_runtime.sh" "--require-api-33"
require_text "tools/install_central_brain_android_runtime.sh" "r1_api33_exit_criteria_met"
require_text "central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainSdk.java" 'EVOLUTION_STAGE = "R3_TRUSTED_GOVERNANCE"'
require_text "central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainSdk.java" 'MATURITY = "android_integrated"'
require_text "central-brain/android-runtime/central-brain-sdk/build.gradle.kts" "aidl = true"
require_text "central-brain/android-runtime/README.md" "central_brain_api33_x86_64"
require_text "central-brain/android-runtime/README.md" 'typed Protocol Binding is `android_integrated`'
require_text "central-brain/android-runtime/README.md" "command-line tools understand SDK XML up to version 3"
require_text "central-brain/android-runtime/settings.gradle.kts" 'include(":policy-probe")'
require_text "central-brain/android-runtime/policy-probe/build.gradle.kts" 'applicationId = "com.centralbrain.policyprobe"'
require_text "central-brain/android-runtime/policy-probe/build.gradle.kts" "minSdk = 33"

if grep -R -Fq "android.permission.INTERNET" "$RUNTIME_DIR"; then
  echo "Android runtime modules must not request network access" >&2
  exit 1
fi

if grep -Fq "RuntimeProbeActivity" "$RUNTIME_DIR/runtime-service/src/main/AndroidManifest.xml"; then
  echo "the ADB lifecycle probe must remain debug-only" >&2
  exit 1
fi

bash "$ROOT_DIR/tools/check_central_brain_android_aidl_contract.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_binder_runtime.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_binder_lifecycle.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_job_supervisor.sh"
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

echo "Central Brain Android runtime Gradle foundation check passed"

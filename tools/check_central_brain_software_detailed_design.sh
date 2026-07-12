#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/002/003/004/005/006, FW-U-001/002/003/004/005/006/007/008,
# FW-S-001/002/003/004/005/006, NV-F-001/011/012, NV-G-001/002/003/004/005/006/007,
# NV-P-001/002/003/004/005/006, HW-002, KH-003/006/007, DEL-001/002/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOC="$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_DETAILED_DESIGN.md"
SETTINGS="$ROOT_DIR/central-brain/android-runtime/settings.gradle.kts"
RUNTIME_AIDL="$ROOT_DIR/central-brain/android-runtime/central-brain-sdk/src/main/aidl/com/centralbrain/sdk/production/ICentralBrainRuntime.aidl"
GOVERNANCE_AIDL="$ROOT_DIR/central-brain/android-runtime/central-brain-sdk/src/main/aidl/com/centralbrain/sdk/governance/ICentralBrainGovernance.aidl"
DIAGNOSTICS_AIDL="$ROOT_DIR/central-brain/android-runtime/central-brain-sdk/src/main/aidl/com/centralbrain/sdk/diagnostics/ICentralBrainDiagnostics.aidl"
NATIVE_HEADER="$ROOT_DIR/central-brain/android-runtime/native-runtime/src/main/cpp/include/central_brain_native.h"
CLIENT2_BRIDGE="$ROOT_DIR/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/Client2ScenarioBridge.java"
OLLAMA_ADAPTER="$ROOT_DIR/central-brain/backend/ollama_simulated_npu.py"

for file in "$DOC" "$SETTINGS" "$RUNTIME_AIDL" "$GOVERNANCE_AIDL" \
    "$DIAGNOSTICS_AIDL" "$NATIVE_HEADER" "$CLIENT2_BRIDGE" "$OLLAMA_ADAPTER"; do
  [[ -f "$file" ]] || { echo "software detailed design source missing: $file" >&2; exit 1; }
done

require_text() {
  local marker="$1"
  grep -Fq -- "$marker" "$DOC" \
    || { echo "software detailed design marker missing: $marker" >&2; exit 1; }
}

for heading in \
  '# Central Brain 软件详细设计' \
  '## 2. 范围、成熟度与硬边界' \
  '## 3. 部署与进程设计' \
  '## 4. 跨模块通用设计' \
  '## 5. Android SDK 与 AIDL 详细设计' \
  '## 6. Android Runtime Service 详细设计' \
  '## 7. 身份与 Capability Policy' \
  '## 8. Job Supervisor 与 Scheduler' \
  '## 9. Room 持久化与 Durable Workflow' \
  '## 10. Governance、Effect 与安全状态' \
  '## 11. Model Runtime' \
  '## 12. Event、Memory 与 Skill' \
  '## 13. Native Runtime C/JNI' \
  '## 14. Demo HMI、Policy Probe 与 Client2' \
  '## 15. Python 语义原型' \
  '## 16. Protocol Binding' \
  '## 17. Hardware、Driver/HAL 与虚拟化空接口' \
  '## 18. 扩展开发操作手册' \
  '## 19. 构建与验证矩阵' \
  '## 20. 已知限制与未实现项' \
  '## 21. Code Review Checklist' \
  '## 22. 文档维护'; do
  require_text "$heading"
done

for marker in \
  '用户提供的架构图仍是最高层需求基线' \
  '`production_ready` | `false`' \
  '`target_hardware_validated` | `false`' \
  '`driver_development_triggered` | `false`' \
  '`virtualization_development_triggered` | `false`' \
  '`effect_delivery_activation_allowed` | `false`' \
  'Python REST gateway 不得成为' \
  '当前类有完整 contract test，但 `CentralBrainRuntimeService` 未将 production task 接入 Scheduler' \
  'AIDL 故意不提供 `grantApproval`' \
  'Linux “gRPC” 当前是 JSON TCP sample' \
  'Runtime Service 未实例化它' \
  '不开发 Hypervisor' \
  'bash tools/check_central_brain_software_detailed_design.sh'; do
  require_text "$marker"
done

python3 -B - "$ROOT_DIR" "$DOC" "$SETTINGS" "$RUNTIME_AIDL" \
    "$GOVERNANCE_AIDL" "$DIAGNOSTICS_AIDL" "$NATIVE_HEADER" \
    "$CLIENT2_BRIDGE" "$OLLAMA_ADAPTER" <<'PY'
import pathlib
import re
import sys

(
    root_raw,
    doc_raw,
    settings_raw,
    runtime_aidl_raw,
    governance_aidl_raw,
    diagnostics_aidl_raw,
    native_header_raw,
    client2_raw,
    ollama_raw,
) = sys.argv[1:]

root = pathlib.Path(root_raw)
doc = pathlib.Path(doc_raw).read_text(encoding="utf-8")
settings = pathlib.Path(settings_raw).read_text(encoding="utf-8")
runtime_aidl = pathlib.Path(runtime_aidl_raw).read_text(encoding="utf-8")
governance_aidl = pathlib.Path(governance_aidl_raw).read_text(encoding="utf-8")
diagnostics_aidl = pathlib.Path(diagnostics_aidl_raw).read_text(encoding="utf-8")
native_header = pathlib.Path(native_header_raw).read_text(encoding="utf-8")
client2 = pathlib.Path(client2_raw).read_text(encoding="utf-8")
ollama = pathlib.Path(ollama_raw).read_text(encoding="utf-8")

if len(doc.splitlines()) < 900:
    raise SystemExit("software detailed design is unexpectedly short")

modules = re.findall(r'include\(":([a-z0-9-]+)"\)', settings)
expected_modules = {
    "central-brain-sdk",
    "native-runtime",
    "runtime-service",
    "demo-hmi",
    "policy-probe",
}
if set(modules) != expected_modules:
    raise SystemExit(f"unexpected Android Gradle modules: {modules}")
for module in modules:
    if f"`{module}" not in doc:
        raise SystemExit(f"Gradle module missing from detailed design: {module}")

aidl_methods = {
    "runtime": re.findall(
        r"^(?:\s*)(?:int|String|TaskHandle|boolean|TaskUpdate)\s+(\w+)\(",
        runtime_aidl,
        flags=re.MULTILINE,
    ),
    "governance": re.findall(
        r"^(?:\s*)(?:int|String|ActionDecision|ApprovalHandle|ApprovalStatus|boolean)\s+(\w+)\(",
        governance_aidl,
        flags=re.MULTILINE,
    ),
    "diagnostics": re.findall(
        r"^(?:\s*)(?:int|String|DiagnosticPage)\s+(\w+)\(",
        diagnostics_aidl,
        flags=re.MULTILINE,
    ),
}
for family, methods in aidl_methods.items():
    if not methods:
        raise SystemExit(f"no {family} AIDL methods found")
    for method in methods:
        if method not in doc:
            raise SystemExit(f"{family} AIDL method missing from detailed design: {method}")

native_functions = re.findall(
    r"CB_API\s+(?:cb_status_t\s+|const char\s*\*\s*)(cb_[a-z0-9_]+)\(",
    native_header,
)
if len(native_functions) != 6:
    raise SystemExit(f"unexpected C ABI function set: {native_functions}")
for function in native_functions:
    if function not in doc:
        raise SystemExit(f"C ABI function missing from detailed design: {function}")

scenario_block = client2.split("Arrays.asList(", 1)[1].split(")));", 1)[0]
scenarios = re.findall(r'"([a-z]+\.[a-z]+)"', scenario_block)
if len(scenarios) != 12:
    raise SystemExit(f"unexpected Client2 scenario set: {scenarios}")
for scenario in scenarios:
    if scenario not in doc:
        raise SystemExit(f"Client2 scenario missing from detailed design: {scenario}")

environment_variables = sorted(set(re.findall(r'"(CENTRAL_BRAIN_[A-Z0-9_]+)"', ollama)))
expected_environment_variables = {
    "CENTRAL_BRAIN_SIMULATED_NPU_BACKEND",
    "CENTRAL_BRAIN_OLLAMA_URL",
    "CENTRAL_BRAIN_OLLAMA_MODEL",
    "CENTRAL_BRAIN_OLLAMA_TIMEOUT_MS",
    "CENTRAL_BRAIN_OLLAMA_NUM_PREDICT",
    "CENTRAL_BRAIN_OLLAMA_THINK",
}
if set(environment_variables) != expected_environment_variables:
    raise SystemExit(f"unexpected Ollama environment variables: {environment_variables}")
for variable in environment_variables:
    if f"`{variable}`" not in doc:
        raise SystemExit(f"Ollama variable missing from detailed design: {variable}")

required_paths = (
    "central-brain/backend/mock_npu_service.py",
    "central-brain/backend/runtime_governance.py",
    "central-brain/backend/ai_sdk.py",
    "central-brain/backend/agent_scenarios.py",
    "central-brain/backend/ollama_simulated_npu.py",
    "central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl",
    "central-brain/bindings/linux/ipc/central_brain_ipc_daemon.py",
    "central-brain/bindings/linux/ipc/central_brain_governance_daemon.py",
    "central-brain/bindings/linux/grpc/central_brain_grpc_server.py",
    "central-brain/bindings/linux/proto/central_brain_gateway.proto",
    "central-brain/linux-cli/central_brain_cli.py",
)
for relative in required_paths:
    if not (root / relative).is_file():
        raise SystemExit(f"detailed-design mapped source missing: {relative}")
    name = pathlib.Path(relative).name
    if name not in doc and relative not in doc:
        raise SystemExit(f"mapped source absent from detailed design: {relative}")

for forbidden in (
    "`production_ready` | `true`",
    "`target_hardware_validated` | `true`",
    "`vendor_npu_provider_available` | `true`",
    "`hardware_accessed` | `true`",
):
    if forbidden in doc:
        raise SystemExit(f"detailed design makes an unsupported readiness claim: {forbidden}")

print(f"software_detailed_design_lines={len(doc.splitlines())}")
print(f"software_detailed_design_gradle_modules={len(modules)}")
print(f"software_detailed_design_aidl_methods={sum(map(len, aidl_methods.values()))}")
print(f"software_detailed_design_native_functions={len(native_functions)}")
print(f"software_detailed_design_client2_scenarios={len(scenarios)}")
print(f"software_detailed_design_ollama_variables={len(environment_variables)}")
PY

printf '%s\n' \
  'Central Brain software detailed design check passed' \
  'software_detailed_design_documented=true' \
  'android_python_module_boundaries_documented=true' \
  'production_ready=false' \
  'target_hardware_validated=false'

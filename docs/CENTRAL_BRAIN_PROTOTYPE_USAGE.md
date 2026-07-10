# Central Brain Python Prototype Usage Guide

更新时间：2026-07-11

本文面向使用 Android 和 Linux 系统的座舱域软件工程师，说明如何启动、访问、验证当前 Central Brain Python 原型。当前原型严格按架构图需求基线交付：Android 为主路径，Linux 同步提供 CLI、IPC、gRPC/RPC contract sample；真实 PCIe NPU、量产 Driver/HAL、量产 Android system service、真实 DDS/broker/high-rate data plane 和虚拟化实现不在当前 Python 原型范围内。

## 1. 使用对象与范围

当前原型提供这些可用面：

- Python HTTP semantic gateway：本地 mock NPU/AI base 与 Uni Info Bus、SOA、Governance、Driver/HAL gap visibility、hardware empty-interface registry 的只读或 contract-only 入口。
- Android Console：普通 debug APK，通过 Binder/AIDL sample 访问同一套语义网关。模拟器访问宿主机服务时使用 `http://10.0.2.2:8787`。
- Linux CLI：面向 Linux 座舱域工程师的命令行同步交付入口。
- Linux IPC：Unix socket daemon/client active sample。
- Linux gRPC/RPC JSON contract sample：用于表达服务边界和跨进程 contract shape。
- 交付约束：`production_ready=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`、`service_dispatch_triggered=false`。

关键 Req ID 覆盖：

- Android/Linux 交付：`DEL-001`、`DEL-002`、`DEL-003`、`DEL-004`、`DEL-005`
- 中央大脑/中间层：`XSC-001`、`XSC-002`、`XSC-003`、`XSC-004`、`XSC-005`、`XSC-006`
- Driver/HAL 与硬件接口：`KH-003`、`KH-006`、`HW-002`
- 原型收口：`FW-S-006`、`NV-F-012`、`PY-CL-001`、`PY-CL-002`

## 2. 启动后端

从仓库根目录启动：

```bash
cd /home/normad400/appDev
bash tools/run_central_brain_backend.sh
```

等价的显式 Python 启动方式：

```bash
cd /home/normad400/appDev
python3 central-brain/backend/mock_npu_service.py --host 0.0.0.0 --port 8787
```

如果只在本机 Linux shell 中验证，可以限制到 loopback：

```bash
python3 central-brain/backend/mock_npu_service.py --host 127.0.0.1 --port 8787
```

常用环境变量：

```bash
CENTRAL_BRAIN_PORT=8787
CENTRAL_BRAIN_AUDIT_LOG=/tmp/central-brain-audit.jsonl
```

注意：Android 模拟器需要访问宿主机服务，因此后端应监听 `0.0.0.0:8787` 或至少对模拟器可达。

## 3. 启用 Ollama 仿真 NPU

如果当前环境已有 Ollama，可以把它作为用户态 simulated NPU 模型运行时。它只替代 Python mock 推理结果，不代表真实 PCIe NPU、Driver/HAL、vendor SDK、DMA、共享内存、Safety Runtime 或虚拟化可用。

先确认 Ollama 可达：

```bash
curl -s http://127.0.0.1:11434/api/tags | python3 -m json.tool
```

再用 Ollama 后端启动 Central Brain：

```bash
cd /home/normad400/appDev
CENTRAL_BRAIN_SIMULATED_NPU_BACKEND=ollama \
CENTRAL_BRAIN_OLLAMA_URL=http://127.0.0.1:11434 \
CENTRAL_BRAIN_OLLAMA_MODEL=qwen3.5:27b-optimized \
CENTRAL_BRAIN_OLLAMA_TIMEOUT_MS=120000 \
bash tools/run_central_brain_backend.sh
```

验证状态：

```bash
curl -s http://127.0.0.1:8787/npu/status | python3 -m json.tool
```

直接推理：

```bash
curl -s http://127.0.0.1:8787/ai/infer \
  -H 'Content-Type: application/json' \
  -d '{"runtime":"ollama","model":"central-intent-v0","input":{"utterance":"query vehicle state"},"policy":{"safety_state_required":"normal","timeout_ms":2000}}' \
  | python3 -m json.tool
```

Linux CLI 也能通过 SOA `npu-inference` 走同一后端：

```bash
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 \
CENTRAL_BRAIN_SIMULATED_NPU_BACKEND=ollama \
CENTRAL_BRAIN_OLLAMA_MODEL=qwen3.5:27b-optimized \
python3 central-brain/linux-cli/central_brain_cli.py infer
```

期望边界字段仍保持：

```text
runtime=ollama-simulated-npu
simulated_npu_backend=ollama
hardware_accessed=false
driver_development_triggered=false
virtualization_development_triggered=false
production_ready=false
```

## 4. 确认原型当前状态

先检查健康状态：

```bash
curl -s http://127.0.0.1:8787/health | python3 -m json.tool
```

再读取当前 Python 原型完成摘要：

```bash
curl -s http://127.0.0.1:8787/prototype/completion-summary | python3 -m json.tool
```

也可以通过 Linux CLI 读取同一份 evidence：

```bash
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 \
  python3 central-brain/linux-cli/central_brain_cli.py prototype-completion-summary
```

期望看到的关键边界字段：

```text
python_prototype_current_scope_complete=true
prototype_handoff_ready=true
production_ready=false
hardware_accessed=false
driver_development_triggered=false
virtualization_development_triggered=false
service_dispatch_triggered=false
```

这些字段的含义是：当前 Python 原型范围已可交付给 Android/Linux 座舱域工程师试用和对接，但不代表量产系统已经完成。

## 5. Linux CLI 常用入口

所有命令默认读取 `CENTRAL_BRAIN_BASE_URL`，未设置时默认为 `http://127.0.0.1:8787`。

```bash
export CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787
```

原型与交付状态：

```bash
python3 central-brain/linux-cli/central_brain_cli.py prototype-completion-summary
python3 central-brain/linux-cli/central_brain_cli.py prototype-readiness
python3 central-brain/linux-cli/central_brain_cli.py delivery-readiness
python3 central-brain/linux-cli/central_brain_cli.py binding-readiness
python3 central-brain/linux-cli/central_brain_cli.py observability-readiness
```

Uni Info Bus 和事件入口：

```bash
python3 central-brain/linux-cli/central_brain_cli.py state
python3 central-brain/linux-cli/central_brain_cli.py events
python3 central-brain/linux-cli/central_brain_cli.py event-publish
python3 central-brain/linux-cli/central_brain_cli.py event-recent
python3 central-brain/linux-cli/central_brain_cli.py event-subscriptions
python3 central-brain/linux-cli/central_brain_cli.py event-subscribe-request
python3 central-brain/linux-cli/central_brain_cli.py event-subscribe-cancel
```

AI SDK、Agent、Skill、Memory mock：

```bash
python3 central-brain/linux-cli/central_brain_cli.py ai-sdk
python3 central-brain/linux-cli/central_brain_cli.py agent-plan
python3 central-brain/linux-cli/central_brain_cli.py agent-execute
python3 central-brain/linux-cli/central_brain_cli.py skill-invoke
python3 central-brain/linux-cli/central_brain_cli.py memory-query
```

SOA 与治理：

```bash
python3 central-brain/linux-cli/central_brain_cli.py service-contracts
python3 central-brain/linux-cli/central_brain_cli.py soa-extension-closure-summary
python3 central-brain/linux-cli/central_brain_cli.py governance-precheck
python3 central-brain/linux-cli/central_brain_cli.py governance-backend-contract
python3 central-brain/linux-cli/central_brain_cli.py governance-migration-check
python3 central-brain/linux-cli/central_brain_cli.py governance-deployment-plan
```

Driver/HAL 与硬件空接口：

```bash
python3 central-brain/linux-cli/central_brain_cli.py driver-gaps
python3 central-brain/linux-cli/central_brain_cli.py hardware-interfaces
python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-activation-checklist
python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-owner-decision-status
```

Vehicle Signal：

```bash
python3 central-brain/linux-cli/central_brain_cli.py vehicle-signals
python3 central-brain/linux-cli/central_brain_cli.py vehicle-signal-activation
python3 central-brain/linux-cli/central_brain_cli.py vehicle-signal-validation
```

## 6. Android Console 使用方式

启动后端后，构建 Android debug APK：

```bash
cd /home/normad400/appDev
bash tools/build_central_brain_console.sh
```

产物路径：

```text
central-brain/android-console/out/central-brain-console.debug.apk
```

安装到已启动的 Android 模拟器或设备：

```bash
bash tools/install_central_brain_console.sh
```

如果手动安装：

```bash
adb install -r central-brain/android-console/out/central-brain-console.debug.apk
```

Android Console 通过 Binder sample 调用后端。常用按钮与接口对应关系：

- `Complete` -> Binder `getPrototypeCompletionSummaryJson` -> `GET /prototype/completion-summary`
- `Prototype` -> Binder `getPrototypeReadinessJson` -> `GET /prototype/readiness`
- `Observability` -> Binder `getObservabilityReadinessJson` -> `GET /observability/readiness`
- `Delivery` -> Binder `getDeliveryReadinessJson` -> `GET /delivery/readiness`
- `Driver Gaps` -> Binder `getDriverHalGapsJson` -> `GET /native/driver-gaps`
- `Hardware IF` -> Binder `getHardwareInterfacesJson` -> `GET /hardware/interfaces`
- `HW Gate` -> Binder `getHardwareInterfaceActivationChecklistJson` -> `GET /hardware/interfaces/activation-checklist`
- `Vehicle` / `Vehicle Gate` / `Vehicle Valid` -> Vehicle Signal catalog、activation、validation 只读入口

注意：当前 Android 交付是普通 App + Binder sample，用于模拟 App layer 到中间层/AI base 的联通；不是量产 privileged/system service 部署。

## 7. Linux IPC 使用方式

优先用 smoke script 验证 IPC 样例：

```bash
bash tools/smoke_central_brain_linux_ipc.sh
```

手动运行时，先启动后端，再启动 IPC daemon：

```bash
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_daemon.py \
  --socket-path /tmp/central_brain_gateway.sock
```

在另一个 shell 调用 IPC client：

```bash
python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py \
  --socket-path /tmp/central_brain_gateway.sock prototype-completion-summary

python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py \
  --socket-path /tmp/central_brain_gateway.sock hardware-interfaces
```

IPC 样例表达 Linux 进程间 contract shape；它不是量产 broker，也不激活真实硬件或 Driver/HAL。

## 8. Linux gRPC/RPC JSON Contract Sample

优先用 smoke script 验证：

```bash
bash tools/smoke_central_brain_linux_grpc.sh
```

手动运行时，先启动后端，再启动 RPC server：

```bash
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_server.py \
  --host 127.0.0.1 --port 18788
```

在另一个 shell 调用 RPC client：

```bash
python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py \
  --host 127.0.0.1 --port 18788 prototype-completion-summary

python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py \
  --host 127.0.0.1 --port 18788 delivery-readiness
```

## 9. 交付前验证

从仓库根目录执行：

```bash
python3 -m json.tool central-brain/contracts/central_brain_api.json >/dev/null
python3 -m json.tool central-brain/contracts/central_brain_prototype_handoff_manifest.json >/dev/null
python3 -m json.tool central-brain/contracts/central_brain_prototype_completion_audit.json >/dev/null
python3 -m json.tool central-brain/contracts/central_brain_prototype_closure_plan.json >/dev/null
bash tools/check_central_brain_binding_artifacts.sh
bash tools/check_central_brain_delivery_docs.sh
bash tools/check_central_brain_android_system_service_docs.sh
bash tools/check_central_brain_virtualization_docs.sh
bash tools/smoke_central_brain_semantic_gateway.sh
bash tools/smoke_central_brain_audit_persistence.sh
bash tools/smoke_central_brain_qos.sh
bash tools/smoke_central_brain_ollama_simulated_npu.sh
bash tools/smoke_central_brain_linux_ipc.sh
bash tools/smoke_central_brain_linux_grpc.sh
bash tools/build_central_brain_console.sh
git diff --check
```

验证目标：

- contract JSON 可解析。
- Android Binder/AIDL、Console、Linux CLI、IPC、gRPC/RPC 的接口映射没有漂移。
- 交付文档仍覆盖 Android 主路径和 Linux 同步路径。
- Driver/HAL 与虚拟化边界没有被误实现。
- Python 原型 smoke test 仍可启动和返回 expected evidence。

## 10. 当前边界与不能做的事

当前 Python 原型不做这些事：

- 不开发虚拟机或虚拟化 runtime。
- 不访问真实 PCIe NPU、camera、audio、vehicle bus、DDS、TSN、shared memory、safety runtime。
- 不新增量产 Driver/HAL；只记录接口支持、缺口、owner decision、activation checklist 和 evidence contract。
- 不部署量产 Android system/privileged service。
- 不提供量产日志、指标、追踪、审批、review queue 或硬件激活流程。
- 不代表量产性能、实时性、安全隔离或功能安全认证状态。

如果目标 Android/Linux 环境缺少必要用户态桥接、device node、ioctl、sysfs、vendor SDK 或权限模型，应先更新 `docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md` 和相关 deviation/issue 文档，再决定是否新增 Driver/HAL 开发量。

## 11. 常用参考文件

- `central-brain/contracts/central_brain_api.json`：完整 REST、Android Binder、Linux CLI/IPC/gRPC 映射。
- `central-brain/contracts/central_brain_prototype_handoff_manifest.json`：原型 handoff manifest。
- `central-brain/contracts/central_brain_prototype_completion_audit.json`：当前 Python 原型完成审计。
- `central-brain/contracts/central_brain_prototype_closure_plan.json`：闭环计划与剩余生产化边界。
- `docs/CENTRAL_BRAIN_PROTOTYPE_HANDOFF_MANIFEST.md`：handoff 文档。
- `docs/CENTRAL_BRAIN_PROTOTYPE_COMPLETION_AUDIT.md`：完成审计文档。
- `docs/CENTRAL_BRAIN_PROTOTYPE_CLOSURE_PLAN.md`：原型 closure plan。
- `docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md`：Driver/HAL 接口支持与缺口跟踪。
- `docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md`：Android/Linux 交付目标与当前状态。

# Central Brain Prototype

这个目录承载车载中央大脑软件生态的第一阶段原型。

## 目录

- `contracts/`：Android 前端、中间层和后端之间的服务契约。
- `backend/`：WSL 本地 mock NPU 后端，模拟 PCIe NPU runtime。
- `backend/ai_sdk.py`：AI SDK/Agent facade mock，输出 policy-aware task graph。
- `backend/hardware_interfaces.py`：硬件依赖空接口注册表，覆盖 NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、Shared memory/Safety Runtime 的预留方法和 no-hardware-access 边界。
- `backend/native_adapters.py`：Native adapter 注册表，表达 AIOS Kernel、SOA Service Adapter、Vehicle Signal Adapter、Model Runtime Adapter 的 Android/Linux 交付边界和 Driver/HAL gap backlog。
- `backend/prototype_readiness.py`：Python 原型成熟度总览，汇总模块状态、Android/Linux 绑定可见性、开放偏差、开放问题和下一步候选增量。
- `backend/vehicle_signals.py`：Vehicle/Body Signal 只读目录、读桥激活准入条件和读桥校验证据 envelope，表达 VSS-style signal catalog、ECU/Signal Adapter 边界、Android/Linux 绑定可见性和 DRV-GAP-002 链接。
- `bindings/`：Android Binder/AIDL service/client sample、Linux IPC/gRPC 等协议绑定契约、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample。
- `deploy/linux/`：Linux systemd 部署样例、环境模板和平台部署说明。
- `android-console/`：普通 Android App 原型，用于模拟器验证 App -> Binder -> AI SDK/Uni Info Bus/SOA semantic gateway -> 后端联通。
- `linux-cli/`：Linux 同步交付 CLI 示例，调用同一套 Uni Info Bus/SOA 语义入口。
- `../docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md`：Android system/privileged service 集成约束，记录 DEL-001/003/004 与 NV-P-002 目标部署假设。
- `../docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md`：Hypervisor/Safety 接口约束，记录 HV-001..003 非开发范围。

## 第一阶段运行方式

启动后端：

```bash
bash tools/run_central_brain_backend.sh
```

构建 Android APK：

```bash
bash tools/build_central_brain_console.sh
```

安装到已启动的模拟器或设备：

```bash
bash tools/install_central_brain_console.sh
```

Android 模拟器访问宿主机服务时使用 `10.0.2.2:8787`。

运行语义网关 smoke test：

```bash
bash tools/smoke_central_brain_semantic_gateway.sh
```

Linux CLI 示例：

```bash
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py state
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py events
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-publish
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-recent
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscriptions
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscribe-request
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscribe-cancel
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-transport-readiness
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-decision-matrix
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-activation-checklist
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-callback-watch-shape
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-cursor-replay-storage
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-backpressure-qos-evidence
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-readiness-rollup
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-activation-evidence
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-activation-evidence-status
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-activation-evidence-retention-checklist
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py extensions
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py infer
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py ai-sdk
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py agent-plan
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py agent-execute
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py skill-invoke
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py memory-query
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py action-request
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py governance-precheck
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py governance-backend-contract
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py governance-migration-check
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py governance-deployment-plan
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py binding-detail
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py binding-readiness
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py delivery-readiness
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py prototype-readiness
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py native-adapters-detail
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py driver-gaps
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interfaces
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-activation-checklist
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-owner-decision-status
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-owner-decision-evidence
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py vehicle-signals
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py vehicle-signal-activation
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py vehicle-signal-validation
bash tools/check_central_brain_binding_artifacts.sh
bash tools/check_central_brain_delivery_docs.sh
bash tools/check_central_brain_android_system_service_docs.sh
bash tools/check_central_brain_virtualization_docs.sh
bash tools/smoke_central_brain_audit_persistence.sh
bash tools/smoke_central_brain_qos.sh
bash tools/smoke_central_brain_linux_ipc.sh
bash tools/smoke_central_brain_linux_grpc.sh
```

Linux IPC sample：

```bash
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 \
  CENTRAL_BRAIN_GOVERNANCE_SOCKET=/tmp/central_brain_governance.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_governance_daemon.py --socket-path /tmp/central_brain_governance.sock
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 \
  CENTRAL_BRAIN_GOVERNANCE_SOCKET=/tmp/central_brain_governance.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_daemon.py --socket-path /tmp/central_brain_gateway.sock
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py state
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py extensions
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscriptions
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscribe-request
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscribe-cancel
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-readiness-rollup
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-activation-evidence
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-activation-evidence-status
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-activation-evidence-retention-checklist
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py governance-precheck
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py governance-backend-contract
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py governance-migration-check
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py binding-readiness
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py delivery-readiness
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py prototype-readiness
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interfaces
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-activation-checklist
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-owner-decision-status
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py vehicle-signals
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py vehicle-signal-activation
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py vehicle-signal-validation
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py governance
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py audit
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py infer-denied
```

Linux gRPC/RPC contract sample：

```bash
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 \
  CENTRAL_BRAIN_GOVERNANCE_SOCKET=/tmp/central_brain_governance.sock \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_server.py --host 127.0.0.1 --port 18788
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py state
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py extensions
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscriptions
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscribe-request
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscribe-cancel
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-readiness-rollup
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-activation-evidence
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-activation-evidence-status
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-activation-evidence-retention-checklist
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py infer-denied
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py governance-backend-contract
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py governance-migration-check
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py binding-readiness
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py delivery-readiness
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py prototype-readiness
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interfaces
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-activation-checklist
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-owner-decision-status
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py vehicle-signals
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py vehicle-signal-activation
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py vehicle-signal-validation
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py governance
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py audit
```

Linux systemd deployment sample：

```bash
sed -n '1,220p' central-brain/deploy/linux/README.md
bash tools/check_central_brain_delivery_docs.sh
bash tools/check_central_brain_linux_systemd_hardening.sh
```

## 设计边界

当前实现是可运行骨架，不是最终车载中间件。后续会逐步替换为：

- Android/AAOS AIDL 系统服务，当前已有 Binder service/client sample。
- VSS/VHAL 信号适配。
- SOME/IP、DDS、MQTT、真实 gRPC runtime。
- 真实 PCIe NPU 驱动和供应商 runtime。
- 安全状态、权限治理、Trace、OTA 和诊断闭环。

当前 Android/Linux 示例主路径已经使用：

- `GET /uib/state`：Uni Info Bus State，覆盖 XSC-002、FW-U-002。
- `GET /uib/events/topics`、`POST /uib/events/publish`、`GET /uib/events/recent`：Uni Info Bus Event active mock，覆盖 XSC-002、FW-U-003、XSC-006、NV-P-006。
- `GET /uib/events/subscriptions`、`POST /uib/events/subscriptions/request`、`POST /uib/events/subscriptions/cancel`、`GET /uib/events/subscriptions/transport-readiness`、`GET /uib/events/subscriptions/decision-matrix`、`GET /uib/events/subscriptions/activation-checklist`、`GET /uib/events/subscriptions/callback-watch-shape`、`GET /uib/events/subscriptions/cursor-replay-storage`、`GET /uib/events/subscriptions/backpressure-qos-evidence`、`GET /uib/events/subscriptions/readiness-rollup`、`POST /uib/events/subscriptions/activation-evidence`、`GET /uib/events/subscriptions/activation-evidence/status`、`GET /uib/events/subscriptions/activation-evidence/retention-checklist`：Uni Info Bus Event subscription lifecycle、transport readiness、owner decision matrix、activation evidence checklist、callback/watch API shape、cursor/replay storage、backpressure/QoS evidence、readiness rollup、activation evidence intake、review status and retention checklist contract，暴露 lifecycle、cursor/replay、filter、QoS/backpressure、governance、request/cancel、callback/watch readiness、broker/cursor/backpressure owner decision matrix、activation gates、evidence reference envelope、review status、retention policy owner、evidence URI rules、delete/export semantics、callback/watch shape、overflow schema、per-caller throttling、replay rate、ack timeout、readiness blockers、Android/Linux parity 和 no-persistence/no-broker/no-DDS 边界，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004；当前不分配量产 owner，不选择 transport，不持久化 evidence，不读取 evidence store，不创建 review queue，不创建 delete/export workflow，不关闭 readiness gate，不激活事件 QoS，不实现真实订阅 broker、callback/watch、SSE/WebSocket、DDS runtime 或高频数据面。
- `GET /uib/extensions`：Uni Info Bus Extension registry contract，暴露扩展语义对象、schema 状态、治理规则、binding 可见性和 no-dispatch 边界，覆盖 XSC-002、FW-U-008、XSC-005、XSC-006、NV-P-002、NV-P-003、DEL-001、DEL-002；当前不实现动态插件 runtime。
- `POST /uib/actions/request`：Uni Info Bus Action active mock，执行 Permission/Safety State 检查并明确不 dispatch 到 Driver/HAL，覆盖 XSC-002、FW-U-004、FW-U-007、XSC-005、NV-G-005、DEL-001、DEL-002。
- `GET /ai/sdk/capabilities`、`POST /agent/plan`、`POST /agent/execute`、`GET /skills`、`POST /skills/{skill_id}/invoke`、`POST /memory/query`：AI SDK/Agent facade、policy-aware task graph、execute/Skill/Memory contract mock，覆盖 XSC-001、APP-004、NV-F-001、FW-U-006、FW-U-007、DEL-001、DEL-002。
- `POST /soa/invoke`：SOA 服务入口，覆盖 XSC-003、FW-S-004、FW-S-005。
- `GET /governance/runtime`：Runtime & Governance 状态，覆盖 XSC-005、NV-G-001..007。
- `POST /governance/precheck`：Runtime & Governance discovery、Policy/Safety State、Lifecycle、QoS 只检查不调用路径，默认不消费 QoS 窗口且不 dispatch 到 Driver/HAL，覆盖 XSC-005、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、DEL-001、DEL-002。
- `GET /governance/backend-contract`：共享 Runtime & Governance 后端目标契约，固定 Android Binder、Linux IPC 和 Linux gRPC/RPC 未来替换时共用的 `governance.precheck`、`governance.runtime.get`、`audit.recent.get` 操作边界，覆盖 XSC-005、XSC-006、NV-G-001..007、NV-P-002、NV-P-003、DEL-001、DEL-002；当前不是量产多进程治理后端。
- `GET /governance/migration-check`：共享 Runtime & Governance 后端替换 readiness 检查，固定 SOA precheck、Policy/QoS 不复制、runtime/audit 只读诊断和非目标边界，覆盖 XSC-005、XSC-006、NV-G-001、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-003、DEL-001、DEL-002、DEL-003、DEL-004；当前明确 `production_backend_ready=false`。
- `GET /governance/deployment-plan`：共享 Runtime & Governance 后端部署计划 contract，固定 Android system/privileged service、Linux daemon 和 true gRPC/RPC 三类目标部署形态、身份输入、开放决策和非目标边界，覆盖 XSC-005、XSC-006、NV-G-001..007、NV-P-002、NV-P-003、DEL-001、DEL-002、DEL-003、DEL-004；当前仍不实现量产治理后端。
- `GET /soa/contracts`：SOA service contract 可见性，返回 contract、版本、Policy/Safety State、QoS、Lifecycle、schema source 和 no-dispatch 边界，覆盖 XSC-003、FW-S-001..005、NV-G-001..003、DEL-001、DEL-002；当前不 dispatch service、不消费 QoS、不访问 Driver/HAL、车辆总线或虚拟化层。
- `GET /audit/recent`：SOA 调用审计记录；设置 `CENTRAL_BRAIN_AUDIT_LOG` 后可从 JSONL 恢复最近记录，覆盖 XSC-005、NV-G-007、DEL-002。
- `/soa/invoke` QoS fixed-window 检查：对受控服务执行 NV-G-004 限流，超限时返回 `qos_decision=deny` 并写入 `qos_rejected` audit，覆盖 XSC-005、NV-G-004、NV-G-007、FW-S-005、DEL-002。
- `POST /policy/evaluate`：Policy/Safety State 评估入口，覆盖 FW-U-007、FW-S-005、NV-G-005。
- `GET /bindings`：Protocol Binding 状态，覆盖 XSC-006、NV-P-001..006。
- `GET /bindings/detail`：Protocol Binding artifact 详情，覆盖 XSC-006、NV-P-002、NV-P-003、DEL-001、DEL-002。
- `GET /bindings/readiness`：Protocol Binding readiness，返回 Android Binder、Linux IPC、Linux gRPC/RPC、REST、MQTT、SOME/IP、DDS 的当前状态、阻塞项、验证命令和非目标边界，覆盖 XSC-006、NV-P-001..006、DEL-001、DEL-002、DEL-003、DEL-004；当前明确 `production_ready=false`，不实现量产 transport。
- `GET /delivery/readiness`：Android/Linux delivery readiness，返回 Android debug Console/Binder、Android system service note、Linux CLI、Linux IPC、Linux gRPC/RPC、Linux systemd/package profile、Driver/HAL gap backlog 和虚拟化约束的交付状态、验证 bundle、阻塞项和非目标边界，覆盖 DEL-001..005、XSC-001..006；当前明确 `production_ready=false`，不实现 Android system service、真实 gRPC runtime、量产包管理、生产共享治理后端、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
- `GET /prototype/readiness`：Python prototype readiness，返回 AI SDK、Uni Info Bus、SOA、Runtime & Governance、Protocol Binding、Native adapters/Driver-HAL backlog 和 hardware empty-interface registry 的成熟度状态、Android 主路径、Linux 同步路径、开放偏差、开放问题和下一步候选增量，覆盖 XSC-001..006、DEL-001..005、HW-002、KH-003、KH-006、KH-007；当前明确 `production_ready=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`，不 dispatch SOA service、不访问硬件、不开发 Driver/HAL 或虚拟化层。
- `GET /hardware/interfaces`：硬件依赖空接口注册表，返回 NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、Shared memory/Safety Runtime 的 reserved methods、Android 主路径、Linux 同步路径和触发条件，覆盖 XSC-004、XSC-006、HW-002、KH-001、KH-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005；当前明确 `hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`。
- `GET /hardware/interfaces/activation-checklist`：硬件接口激活清单，返回 `HW-ACT-001..008` owner、Android ABI、Linux ABI、Driver/HAL gap review、Safety/Policy、smoke evidence、rollback/fault 和 no-hardware-access 门禁，覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005；Android Binder `getHardwareInterfaceActivationChecklistJson`、Linux CLI `hardware-interface-activation-checklist`、Linux IPC `hardware.interfaces.activation.checklist` 和 Linux gRPC/RPC `GetHardwareInterfaceActivationChecklist` 同步可见，当前明确 `activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`。
- `GET /hardware/interfaces/owner-decision-status`：硬件接口 owner 决策状态汇总，返回 `HW-ODS-001..008` target owner、Android ABI owner、Linux ABI owner、Driver/HAL gap owner、Safety/Policy owner、target smoke evidence owner、rollback/fault semantics owner 和 no-hardware-access-in-prototype 状态，覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005；Android Binder `getHardwareInterfaceOwnerDecisionStatusJson`、Linux CLI `hardware-interface-owner-decision-status`、Linux IPC `hardware.interfaces.owner.decision.status` 和 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionStatus` 同步可见，当前明确 `activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`。
- `POST /hardware/interfaces/owner-decision-evidence`：硬件接口 owner decision evidence intake，校验 `HW-ODE-001..008` target interface、target gate、evidence reference、reviewer identity、Runtime & Governance policy check、no-gate-auto-close 和 Android/Linux parity 门禁，覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005；Android Binder `submitHardwareInterfaceOwnerDecisionEvidenceJson`、Android Console `HW Evidence`、Linux CLI `hardware-interface-owner-decision-evidence`、Linux IPC `hardware.interfaces.owner.decision.evidence` 和 Linux gRPC/RPC `SubmitHardwareInterfaceOwnerDecisionEvidence` 同步可见，当前明确 `owner_decision_evidence_persisted=false`、`review_queue_updated=false`、`owner_assigned=false`、`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`。
- `GET /vehicle/signals`：Vehicle/Body Signal 只读目录，返回 VSS-style signal catalog、访问级别、governance tag、ECU/Signal Adapter 边界和 DRV-GAP-002 链接，覆盖 XSC-002、XSC-004、XSC-006、NV-F-004、NV-F-005、NV-P-002、NV-P-003、DEL-001、DEL-002、DEL-005；当前明确 `dbc_arxml_loaded=false`、`real_vehicle_bus_connected=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`，不加载 DBC/ARXML、不连接 VHAL/SocketCAN/vendor gateway、不访问真实车辆总线。
- `GET /vehicle/signals/activation`：Vehicle Signal 读桥激活准入条件，返回 DBC/ARXML、Android VHAL/vendor AIDL、Linux SocketCAN、vendor gateway/SOME-IP 四类来源的必备输入、Android/Linux 激活路径、最小开发触发条件和 `VS-ACT-001..005` 门禁，覆盖 XSC-002、XSC-004、XSC-006、NV-F-003、NV-F-004、NV-F-005、NV-P-001、NV-P-002、NV-P-003、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005；当前明确 `read_bridge_activated=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`。
- `GET /vehicle/signals/validation`：Vehicle Signal 读桥校验证据 envelope，返回 schema source metadata、Vehicle Signal Adapter owner、platform ABI owner、Android/Linux parity、DRV-GAP-002 evidence 和 no-write-before-read-bridge 门禁，覆盖 XSC-002、XSC-004、XSC-006、NV-F-003、NV-F-004、NV-F-005、NV-P-001、NV-P-002、NV-P-003、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005；当前明确 `read_bridge_activated=false`、`schema_source_attached=false`、`adapter_owner_confirmed=false`、`parity_evidence_attached=false`、`drv_gap_002_evidence_attached=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`。
- Android Console Binder path：debug APK 绑定 `CentralBrainGatewayBinderService`，通过 `CentralBrainGatewayClient` 调用 Uni Info Bus State、AI SDK/Agent task plan 和 Prototype readiness；service sample 仍代理 REST prototype gateway，覆盖 XSC-001、APP-004、XSC-002、XSC-003、XSC-006、NV-P-002、NV-P-005、DEL-001。
- Android Binder service/client sample：`ICentralBrainGateway` 映射 `/uib/*`、`/ai/sdk/capabilities`、`/agent/plan`、`/agent/execute`、`/skills`、`/memory/query`、`/soa/*`、`/policy/evaluate`、`/governance/precheck`、`/governance/backend-contract`、`/governance/migration-check`、`/governance/deployment-plan`、`/governance/runtime`、`/bindings/detail`、`/bindings/readiness`、`/delivery/readiness`、`/prototype/readiness`、`/native/adapters/detail`、`/native/driver-gaps`、`/hardware/interfaces`、`/hardware/interfaces/activation-checklist`、`/hardware/interfaces/owner-decision-status`、`/hardware/interfaces/owner-decision-evidence`、`/vehicle/signals`、`/vehicle/signals/activation`、`/vehicle/signals/validation`，含 `getEventSubscriptionReadinessRollupJson`、`submitEventSubscriptionActivationEvidenceJson`、`getEventSubscriptionActivationEvidenceStatusJson`、`getEventSubscriptionActivationEvidenceRetentionChecklistJson`、`getUibExtensionsJson`、`getServiceContractsJson`、`getBindingReadinessJson`、`getDeliveryReadinessJson`、`getPrototypeReadinessJson`、`getHardwareInterfacesJson`、`getHardwareInterfaceActivationChecklistJson`、`getHardwareInterfaceOwnerDecisionStatusJson`、`submitHardwareInterfaceOwnerDecisionEvidenceJson`、`getVehicleSignalsJson`、`getVehicleSignalActivationJson` 和 `getVehicleSignalValidationJson`，覆盖 XSC-001、XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、APP-004、FW-U-003、FW-U-004、FW-U-006、FW-U-008、FW-S-004、HW-002、NV-F-003、NV-F-004、NV-F-005、NV-G-003、NV-P-002、NV-P-003、NV-P-006、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-003、DEL-004、DEL-005。
- Android system/privileged service integration note：`docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md` 记录 manifest/signature permission、Binder identity 到 Policy、SELinux/deployment 假设和验证检查项，覆盖 DEL-001、DEL-003、DEL-004、XSC-002、XSC-003、XSC-005、XSC-006、NV-P-002、NV-P-005、FW-U-007、FW-S-005、NV-G-005；本轮不开发 Android framework patch、priv-app 签名配置、SELinux policy、Driver/HAL 或虚拟化层。
- Linux shared governance daemon sample：`central_brain_governance_daemon.py` 通过 Unix socket 提供 `governance.precheck`、`governance.runtime.get` 和 `audit.recent.get`，`central_brain_governance_client.py` 供 Linux IPC/gRPC 复用 shared precheck envelope 并直接查看治理状态/审计，覆盖 XSC-005、XSC-006、NV-G-001、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-003、DEL-002。
- Linux Unix socket IPC sample：`uib.*`、`soa.*`、`policy.*`、`governance.*`、`bindings.*`、`delivery.*`、`prototype.readiness.get`、`hardware.interfaces.get`、`hardware.interfaces.activation.checklist`、`hardware.interfaces.owner.decision.status`、`hardware.interfaces.owner.decision.evidence`、`vehicle.signals.list`、`vehicle.signals.activation.get`、`vehicle.signals.validation.get` 和 `audit.*` 本地 IPC envelope；`uib.events.subscriptions.readiness.rollup` 暴露 Event subscription readiness blockers，`uib.events.subscriptions.activation.evidence.retention.checklist` 暴露 activation evidence retention checklist，`soa.contracts.get` 暴露 SOA contract，`bindings.readiness.get` 暴露 Protocol Binding readiness，`delivery.readiness.get` 暴露 Android/Linux delivery readiness，`prototype.readiness.get` 暴露 Python prototype readiness，`hardware.interfaces.get` 暴露硬件依赖空接口，`hardware.interfaces.activation.checklist` 暴露硬件接口激活前 owner/ABI/smoke 门禁，`hardware.interfaces.owner.decision.status` 暴露硬件接口 owner 决策状态，`hardware.interfaces.owner.decision.evidence` 暴露硬件 owner evidence intake，`vehicle.signals.list` 暴露只读信号目录，`vehicle.signals.activation.get` 暴露读桥激活准入条件，`vehicle.signals.validation.get` 暴露读桥校验证据 envelope，`soa.service.invoke` 在转发到 REST prototype 前优先通过 shared governance client 执行 Runtime & Governance daemon precheck，不可用时回退本地 precheck，`governance.runtime.get` 与 `audit.recent.get` 先走同一 shared diagnostic socket 再回退 REST，覆盖 XSC-003、XSC-004、XSC-005、XSC-006、FW-U-003、FW-U-004、FW-S-004、HW-002、NV-F-003、NV-F-004、NV-F-005、KH-003、KH-006、KH-007、NV-G-001、NV-G-002、NV-G-003、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-001..006、DEL-002、DEL-003、DEL-004、DEL-005。
- Linux gRPC/RPC JSON contract sample：`central_brain_gateway.proto`、`central_brain_grpc_server.py` 和 `central_brain_grpc_client.py` 验证 GatewayRequest/GatewayResponse、RPC 名称映射、`GetEventSubscriptionReadinessRollup`、`SubmitEventSubscriptionActivationEvidence`、`GetEventSubscriptionActivationEvidenceStatus`、`GetEventSubscriptionActivationEvidenceRetentionChecklist`、`GetUibExtensions`、`GetServiceContracts`、`GetBindingReadiness`、`GetDeliveryReadiness`、`GetPrototypeReadiness`、`GetHardwareInterfaces`、`GetHardwareInterfaceActivationChecklist`、`GetHardwareInterfaceOwnerDecisionStatus`、`SubmitHardwareInterfaceOwnerDecisionEvidence`、`GetVehicleSignals`、`GetVehicleSignalActivation`、`GetVehicleSignalValidation`、`InvokeService` shared governance client precheck，以及 `GetRuntimeGovernance`/`GetRecentAudit` shared diagnostic path；当前环境无 `grpcio`，所以使用标准库 JSON TCP wrapper，覆盖 XSC-001、XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、APP-004、FW-U-003、FW-U-004、FW-U-006、FW-U-008、FW-S-004、HW-002、NV-F-003、NV-F-004、NV-F-005、NV-G-003、NV-P-003、KH-003、KH-006、KH-007、DEL-002、DEL-003、DEL-004、DEL-005。
- `GET /native/adapters`：Native adapter 注册表，覆盖 XSC-004、NV-F-001、NV-F-003、NV-F-004、NV-F-008、NV-F-009、NV-F-011。
- `GET /native/adapters/detail`：Android/Linux 原生适配交付边界与 Driver/HAL 依赖说明，覆盖 XSC-004、DEL-001、DEL-002、DEL-005。
- `GET /native/driver-gaps`：Driver/HAL gap backlog，列出 NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、Shared memory/Safety Runtime 的触发条件与最小新增开发量；Android Binder `getDriverHalGapsJson` 和 Linux CLI `driver-gaps` 共用该 contract，覆盖 KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- `docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md`：外置 PCIe NPU 的 Model Runtime Adapter、Driver/HAL、Safety 状态、错误码和 Android/Linux 集成检查点，覆盖 HW-002、NV-F-011、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- Linux systemd deployment sample：`central-brain/deploy/linux/` 覆盖 DEL-002、DEL-003、DEL-004、XSC-002、XSC-003、XSC-005、XSC-006、NV-P-002、NV-P-003；`tools/check_central_brain_linux_systemd_hardening.sh` 验证 systemd unit 的最小 sandbox 和写路径约束。
- Virtualization/Safety constraints：`docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md` 覆盖 HV-001、HV-002、HV-003、FW-S-005、NV-G-005、NV-F-009、KH-007、DEL-004；只记录接口约束和部署假设，不开发虚拟化层。

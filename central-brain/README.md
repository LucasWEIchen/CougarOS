# Central Brain Prototype

这个目录承载车载中央大脑软件生态的第一阶段原型。

## 目录

- `contracts/`：Android 前端、中间层和后端之间的服务契约。
- `backend/`：WSL 本地 mock NPU 后端，模拟 PCIe NPU runtime。
- `backend/ai_sdk.py`：AI SDK/Agent facade mock，输出 policy-aware task graph。
- `backend/native_adapters.py`：Native adapter 注册表，表达 AIOS Kernel、SOA Service Adapter、Vehicle Signal Adapter、Model Runtime Adapter 的 Android/Linux 交付边界和 Driver/HAL gap backlog。
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
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py infer
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py ai-sdk
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py agent-plan
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py agent-execute
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py skill-invoke
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py memory-query
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py action-request
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py governance-precheck
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py binding-detail
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py native-adapters-detail
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py driver-gaps
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
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py governance-precheck
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
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py infer-denied
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
- `POST /uib/actions/request`：Uni Info Bus Action active mock，执行 Permission/Safety State 检查并明确不 dispatch 到 Driver/HAL，覆盖 XSC-002、FW-U-004、FW-U-007、XSC-005、NV-G-005、DEL-001、DEL-002。
- `GET /ai/sdk/capabilities`、`POST /agent/plan`、`POST /agent/execute`、`GET /skills`、`POST /skills/{skill_id}/invoke`、`POST /memory/query`：AI SDK/Agent facade、policy-aware task graph、execute/Skill/Memory contract mock，覆盖 XSC-001、APP-004、NV-F-001、FW-U-006、FW-U-007、DEL-001、DEL-002。
- `POST /soa/invoke`：SOA 服务入口，覆盖 XSC-003、FW-S-004、FW-S-005。
- `GET /governance/runtime`：Runtime & Governance 状态，覆盖 XSC-005、NV-G-001..007。
- `POST /governance/precheck`：Runtime & Governance discovery、Policy/Safety State、Lifecycle、QoS 只检查不调用路径，默认不消费 QoS 窗口且不 dispatch 到 Driver/HAL，覆盖 XSC-005、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、DEL-001、DEL-002。
- `GET /audit/recent`：SOA 调用审计记录；设置 `CENTRAL_BRAIN_AUDIT_LOG` 后可从 JSONL 恢复最近记录，覆盖 XSC-005、NV-G-007、DEL-002。
- `/soa/invoke` QoS fixed-window 检查：对受控服务执行 NV-G-004 限流，超限时返回 `qos_decision=deny` 并写入 `qos_rejected` audit，覆盖 XSC-005、NV-G-004、NV-G-007、FW-S-005、DEL-002。
- `POST /policy/evaluate`：Policy/Safety State 评估入口，覆盖 FW-U-007、FW-S-005、NV-G-005。
- `GET /bindings`：Protocol Binding 状态，覆盖 XSC-006、NV-P-001..006。
- `GET /bindings/detail`：Protocol Binding artifact 详情，覆盖 XSC-006、NV-P-002、NV-P-003、DEL-001、DEL-002。
- Android Console Binder path：debug APK 绑定 `CentralBrainGatewayBinderService`，通过 `CentralBrainGatewayClient` 调用 Uni Info Bus State 与 AI SDK/Agent task plan；service sample 仍代理 REST prototype gateway，覆盖 XSC-001、APP-004、XSC-002、XSC-003、XSC-006、NV-P-002、NV-P-005、DEL-001。
- Android Binder service/client sample：`ICentralBrainGateway` 映射 `/uib/*`、`/ai/sdk/capabilities`、`/agent/plan`、`/agent/execute`、`/skills`、`/memory/query`、`/soa/*`、`/policy/evaluate`、`/governance/precheck`、`/governance/runtime`、`/bindings/detail`、`/native/adapters/detail`、`/native/driver-gaps`，覆盖 XSC-001、XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、APP-004、FW-U-003、FW-U-004、FW-U-006、NV-P-002、NV-P-006、KH-003、KH-006、DEL-001、DEL-005。
- Android system/privileged service integration note：`docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md` 记录 manifest/signature permission、Binder identity 到 Policy、SELinux/deployment 假设和验证检查项，覆盖 DEL-001、DEL-003、DEL-004、XSC-002、XSC-003、XSC-005、XSC-006、NV-P-002、NV-P-005、FW-U-007、FW-S-005、NV-G-005；本轮不开发 Android framework patch、priv-app 签名配置、SELinux policy、Driver/HAL 或虚拟化层。
- Linux shared governance daemon sample：`central_brain_governance_daemon.py` 通过 Unix socket 提供 `governance.precheck`，可供 Linux IPC `soa.service.invoke` 转发前复用，覆盖 XSC-005、XSC-006、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、DEL-002。
- Linux Unix socket IPC sample：`uib.*`、`soa.*`、`policy.*`、`governance.*` 和 `audit.*` 本地 IPC envelope；`soa.service.invoke` 在转发到 REST prototype 前优先执行 shared Runtime & Governance daemon precheck，不可用时回退本地 precheck，覆盖 XSC-005、XSC-006、FW-U-003、FW-U-004、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-006、DEL-002。
- Linux gRPC/RPC JSON contract sample：`central_brain_gateway.proto`、`central_brain_grpc_server.py` 和 `central_brain_grpc_client.py` 验证 GatewayRequest/GatewayResponse、RPC 名称映射和 `InvokeService` shared governance precheck；当前环境无 `grpcio`，所以使用标准库 JSON TCP wrapper，覆盖 XSC-001、XSC-002、XSC-003、XSC-005、XSC-006、APP-004、FW-U-003、FW-U-004、FW-U-006、NV-P-003、DEL-002。
- `GET /native/adapters`：Native adapter 注册表，覆盖 XSC-004、NV-F-001、NV-F-003、NV-F-004、NV-F-008、NV-F-009、NV-F-011。
- `GET /native/adapters/detail`：Android/Linux 原生适配交付边界与 Driver/HAL 依赖说明，覆盖 XSC-004、DEL-001、DEL-002、DEL-005。
- `GET /native/driver-gaps`：Driver/HAL gap backlog，列出 NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、Shared memory/Safety Runtime 的触发条件与最小新增开发量；Android Binder `getDriverHalGapsJson` 和 Linux CLI `driver-gaps` 共用该 contract，覆盖 KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- `docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md`：外置 PCIe NPU 的 Model Runtime Adapter、Driver/HAL、Safety 状态、错误码和 Android/Linux 集成检查点，覆盖 HW-002、NV-F-011、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- Linux systemd deployment sample：`central-brain/deploy/linux/` 覆盖 DEL-002、DEL-003、DEL-004、XSC-002、XSC-003、XSC-005、XSC-006、NV-P-002、NV-P-003；`tools/check_central_brain_linux_systemd_hardening.sh` 验证 systemd unit 的最小 sandbox 和写路径约束。
- Virtualization/Safety constraints：`docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md` 覆盖 HV-001、HV-002、HV-003、FW-S-005、NV-G-005、NV-F-009、KH-007、DEL-004；只记录接口约束和部署假设，不开发虚拟化层。

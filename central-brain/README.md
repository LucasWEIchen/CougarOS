# Central Brain Prototype

这个目录承载车载中央大脑软件生态的第一阶段原型。

## 目录

- `contracts/`：Android 前端、中间层和后端之间的服务契约。
- `backend/`：WSL 本地 mock NPU 后端，模拟 PCIe NPU runtime。
- `backend/native_adapters.py`：Native adapter 注册表，表达 AIOS Kernel、SOA Service Adapter、Vehicle Signal Adapter、Model Runtime Adapter 的 Android/Linux 交付边界。
- `bindings/`：Android Binder/AIDL service/client sample、Linux IPC/gRPC 等协议绑定契约与 Linux IPC active sample。
- `deploy/linux/`：Linux systemd 部署样例、环境模板和平台部署说明。
- `android-console/`：普通 Android App 原型，用于模拟器验证应用层和后端联通。
- `linux-cli/`：Linux 同步交付 CLI 示例，调用同一套 Uni Info Bus/SOA 语义入口。
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
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py binding-detail
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py native-adapters-detail
bash tools/check_central_brain_binding_artifacts.sh
bash tools/check_central_brain_delivery_docs.sh
bash tools/check_central_brain_virtualization_docs.sh
bash tools/smoke_central_brain_linux_ipc.sh
```

Linux IPC sample：

```bash
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_daemon.py --socket-path /tmp/central_brain_gateway.sock
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py state
```

Linux systemd deployment sample：

```bash
sed -n '1,220p' central-brain/deploy/linux/README.md
bash tools/check_central_brain_delivery_docs.sh
```

## 设计边界

当前实现是可运行骨架，不是最终车载中间件。后续会逐步替换为：

- Android/AAOS AIDL 系统服务，当前已有 Binder service/client sample。
- VSS/VHAL 信号适配。
- SOME/IP、DDS、MQTT、gRPC 协议绑定。
- 真实 PCIe NPU 驱动和供应商 runtime。
- 安全状态、权限治理、Trace、OTA 和诊断闭环。

当前 Android/Linux 示例主路径已经使用：

- `GET /uib/state`：Uni Info Bus State，覆盖 XSC-002、FW-U-002。
- `GET /uib/events/topics`、`POST /uib/events/publish`、`GET /uib/events/recent`：Uni Info Bus Event active mock，覆盖 XSC-002、FW-U-003、XSC-006、NV-P-006。
- `POST /soa/invoke`：SOA 服务入口，覆盖 XSC-003、FW-S-004、FW-S-005。
- `GET /governance/runtime`：Runtime & Governance 状态，覆盖 XSC-005、NV-G-001..007。
- `GET /audit/recent`：SOA 调用审计记录，覆盖 XSC-005、NV-G-007。
- `POST /policy/evaluate`：Policy/Safety State 评估入口，覆盖 FW-U-007、FW-S-005、NV-G-005。
- `GET /bindings`：Protocol Binding 状态，覆盖 XSC-006、NV-P-001..006。
- `GET /bindings/detail`：Protocol Binding artifact 详情，覆盖 XSC-006、NV-P-002、NV-P-003、DEL-001、DEL-002。
- Android Binder service/client sample：`ICentralBrainGateway` 映射 `/uib/*`、`/soa/*`、`/policy/evaluate`、`/governance/runtime`、`/bindings/detail`、`/native/adapters/detail`，覆盖 XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、FW-U-003、NV-P-002、NV-P-006、DEL-001。
- Linux Unix socket IPC sample：`uib.*`、`soa.*`、`policy.*`、`governance.*` 和 `audit.*` 本地 IPC envelope，覆盖 XSC-006、FW-U-003、NV-P-002、NV-P-006、DEL-002。
- `GET /native/adapters`：Native adapter 注册表，覆盖 XSC-004、NV-F-001、NV-F-003、NV-F-004、NV-F-008、NV-F-009、NV-F-011。
- `GET /native/adapters/detail`：Android/Linux 原生适配交付边界与 Driver/HAL 依赖说明，覆盖 XSC-004、DEL-001、DEL-002、DEL-005。
- `docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md`：外置 PCIe NPU 的 Model Runtime Adapter、Driver/HAL、Safety 状态、错误码和 Android/Linux 集成检查点，覆盖 HW-002、NV-F-011、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- Linux systemd deployment sample：`central-brain/deploy/linux/` 覆盖 DEL-002、DEL-003、DEL-004、XSC-002、XSC-003、XSC-005、XSC-006、NV-P-002。
- Virtualization/Safety constraints：`docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md` 覆盖 HV-001、HV-002、HV-003、FW-S-005、NV-G-005、NV-F-009、KH-007、DEL-004；只记录接口约束和部署假设，不开发虚拟化层。

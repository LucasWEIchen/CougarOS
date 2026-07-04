# Central Brain Prototype

这个目录承载车载中央大脑软件生态的第一阶段原型。

## 目录

- `contracts/`：Android 前端、中间层和后端之间的服务契约。
- `backend/`：WSL 本地 mock NPU 后端，模拟 PCIe NPU runtime。
- `android-console/`：普通 Android App 原型，用于模拟器验证应用层和后端联通。
- `linux-cli/`：Linux 同步交付 CLI 示例，调用同一套 Uni Info Bus/SOA 语义入口。

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
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py infer
```

## 设计边界

当前实现是可运行骨架，不是最终车载中间件。后续会逐步替换为：

- Android/AAOS AIDL 系统服务。
- VSS/VHAL 信号适配。
- SOME/IP、DDS、MQTT、gRPC 协议绑定。
- 真实 PCIe NPU 驱动和供应商 runtime。
- 安全状态、权限治理、Trace、OTA 和诊断闭环。

当前 Android/Linux 示例主路径已经使用：

- `GET /uib/state`：Uni Info Bus State，覆盖 XSC-002、FW-U-002。
- `POST /soa/invoke`：SOA 服务入口，覆盖 XSC-003、FW-S-004、FW-S-005。
- `GET /governance/runtime`：Runtime & Governance 状态，覆盖 XSC-005、NV-G-001..007。
- `GET /bindings`：Protocol Binding 状态，覆盖 XSC-006、NV-P-001..006。

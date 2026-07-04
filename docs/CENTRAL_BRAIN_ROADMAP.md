# 车载中央大脑路线图与进展

更新时间：2026-07-04

## 长期任务拆解

| 阶段 | 目标 | 主要交付物 | 状态 |
| --- | --- | --- | --- |
| M0 | 建立方向、文档、原型骨架 | 产品设计、架构设计、资料纪要、Android 原型、mock NPU 后端 | 已完成 |
| M0.1 | PM 级需求拆解和接口设计 | 需求拆解、接口设计、KaKaClaw 参考产品概念映射 | 已完成 |
| A0 | 架构图需求基线化 | 需求矩阵、偏差表、疑点表、按图执行计划 | 已完成 |
| A1 | Uni Info Bus 语义接口 mock | Context/State/Event/Action/Service/Tool/Permission contract 与 client | Android/Linux 主路径初版 |
| A2 | SOA 服务入口 mock | Business/Foundation/Atomic/Contract/Safety State | SOA invoke 初版 |
| A3 | Runtime & Governance mock | Registry、Discovery、Schema、QoS、Policy、Lifecycle、Audit | active prototype 初版 |
| A4 | Protocol Binding 分层 | REST 下沉为 binding，IPC/gRPC/MQTT/SOME-IP/DDS stub | Android/Linux contract skeleton |
| A5 | Native adapters mock | AIOS Kernel、Service Adapter、Vehicle Signal、Model Runtime Adapter | adapter registry 初版 |
| A6 | Kernel/HAL/NPU 设计落地 | Driver/HAL/NPU runtime design、PCIe 接入路径 | 待开始 |
| A6.1 | 驱动接口支持矩阵 | Android/Linux 驱动能力、缺口、最小新增开发量 | 初版完成 |
| A7 | Hypervisor/Safety 接口约束 | ASIL/QM domain map、跨 VM 通信假设；不开发虚拟化 | 待开始 |
| A8 | 应用层扩展 | 座舱、Agent、Cluster/TBOX、ADAS、诊断视图 | 待开始 |
| A9 | Android/Linux 双平台交付 | Android APK/SDK sample、Linux CLI/daemon sample、平台差异说明 | 待开始 |

## M0 任务清单

- [x] 读取并归档用户提供的软件架构图。
- [x] 调研 AAOS/VHAL/AIDL/VSS/SOME-IP/DDS/NPU 接入资料。
- [x] 核对现有 Android SDK、AVD、Git、OpenCLAW 环境。
- [x] 创建长期任务分支。
- [x] 编写产品设计文档初版。
- [x] 编写软件架构设计文档初版。
- [x] 编写资料纪要。
- [x] 创建 Android Console 原型。
- [x] 创建 mock NPU 后端。
- [x] 构建 Android APK。
- [x] 启动 mock 后端。
- [x] 安装 APK 到本地模拟器。
- [x] 验证 App 访问 `/health` 返回 `Health HTTP 200`。
- [x] 验证 App 触发 `/ai/infer` 返回 `Inference HTTP 200`。
- [x] 形成 M0 Git 提交。

## 当前工程策略

- 不把现有 APK 逆向产物作为本任务第一阶段的修改对象。
- 所有新系统代码放在 `central-brain/`。
- 构建和运行脚本放在 `tools/`。
- 文档放在 `docs/CENTRAL_BRAIN_*`。
- 后续每完成一个可运行增量，都创建 Git 提交。
- 架构图是最高优先级需求基线；所有产品参考、接口扩展和 mock 实现都必须映射回图中模块。
- 新增或保留任何软件偏差，必须同步更新 `docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md`。
- 发现图中边界不清或工程风险，必须同步更新 `docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md`。

## 最近进展

### 2026-07-04

- 确认图片可通过 WSL 路径读取，并归档到 `docs/assets/central_brain_architecture_source.png`。
- 确认当前仓库已有 Android 逆向和模拟器工具链。
- 确认 OpenCLAW 绝对路径可用：`/home/normad400/.npm-global/bin/openclaw`。
- 创建分支：`codex/central-brain-ecosystem`。
- 新增中央大脑文档和第一阶段代码骨架。
- 构建并签名 Android APK：`central-brain/android-console/out/central-brain-console.debug.apk`。
- 启动 WSL mock 后端并验证 `/health`、`/vehicle/state`、`/ai/infer`。
- 在本地 AVD `cabin_client_api36_x86_64` 安装并启动 `com.centralbrain.console`。
- 验证模拟器 App 通过 `10.0.2.2:8787` 连通 WSL 后端。
- 保存验证截图：
  - `logs/test/central-brain/console-launch.png`
  - `logs/test/central-brain/console-inference.png`
- 参考地平线 KaKaClaw 咖咖虾公开资料，补充产品概念映射：
  - Agentic Car OS
  - 任务即服务
  - Soul / Skill / Memory
  - 舱驾协同
  - Skill 沙箱
  - Privacy Router
- 新增 PM 级需求拆解：`docs/CENTRAL_BRAIN_REQUIREMENTS_BREAKDOWN.md`。
- 新增接口设计文档：`docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md`。
- 创建 20 小时自动进展推进任务：每 20 分钟一次，共 60 次，自动化 ID `20`。
- 用户明确要求将架构图作为真实需求基线，而非示意图。
- 新增架构图需求矩阵：`docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md`。
- 新增软件偏差登记表：`docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md`。
- 新增架构疑点登记表：`docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md`。
- 新增按图执行计划：`docs/CENTRAL_BRAIN_ARCHITECTURE_EXECUTION_PLAN.md`。
- 更新自动化 ID `20`：每 20 分钟推进时必须先检查架构需求矩阵、偏差登记表和疑点登记表。
- 用户明确虚拟化层不开发；本项目只记录虚拟化接口约束和部署假设。
- 用户明确驱动层仅在当前 Android/Linux 环境能力不足时新增开发量，但驱动接口支持必须文档化。
- 用户明确黄色小太阳组件会在多个 SoC 出现；已按跨 SoC 可移植平台组件建立 `XSC-001..006`。
- 用户明确以 Android 开发为主，交付时同时提供 Linux 版本；交付对象为 Android/Linux 座舱域软件工程师。
- 新增交付目标文档：`docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md`。
- 新增驱动接口支持矩阵：`docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md`。
- 立即手动执行一次自动化任务：
  - 完成 A1 后端 mock 初版：`/context`、`/state`、`/events/topics`、`/events/publish`、`/actions/request`、`/service/invoke`、`/tools`、`/permission/check`。
  - 新增 Linux/WSL smoke test：`tools/test_central_brain_bus.sh`。
  - 注意：Android Console 仍未切到 Uni Info Bus client，`DEV-001` 保持临时偏差。
- 推进语义网关主路径：
  - Android Console 改为调用 `GET /uib/state` 和 `POST /soa/invoke`。
  - 后端新增架构命名入口：`/uib/context`、`/uib/state`、`/soa/services`、`/soa/invoke`、`/governance/runtime`、`/bindings`。
  - 新增 Linux CLI：`central-brain/linux-cli/central_brain_cli.py`。
  - 新增 smoke test：`tools/smoke_central_brain_semantic_gateway.sh`。
  - 覆盖 Req ID：XSC-002、XSC-003、XSC-005、XSC-006、FW-U-001、FW-U-002、FW-U-005、FW-U-007、FW-S-004、FW-S-005、NV-G-001..007、NV-P-001..006。
- 推进 Runtime & Governance active prototype：
  - 新增 `central-brain/backend/runtime_governance.py`，集中承载服务注册、发现、Policy、Lifecycle、QoS 元数据和内存审计。
  - `/soa/invoke` 现在先通过 registry/discovery/policy/lifecycle precheck，再写入 audit。
  - 新增架构命名入口：`POST /policy/evaluate`、`GET /audit/recent`。
  - Linux CLI 与 smoke test 覆盖 policy deny、SOA invoke audit 和 governance 状态。
  - 覆盖 Req ID：XSC-005、FW-S-001、FW-S-002、FW-S-003、FW-S-004、FW-S-005、NV-G-001..007。
- 推进 Protocol Binding contract skeleton：
  - 新增 `central-brain/backend/protocol_bindings.py`，把 REST、Android Binder/AIDL、Linux IPC、gRPC、MQTT、SOME/IP、DDS 纳入绑定注册表。
  - 新增 Android AIDL artifact：`central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl`。
  - 新增 Linux artifact：`central-brain/bindings/linux/proto/central_brain_gateway.proto` 与 `central-brain/bindings/linux/ipc/central_brain_ipc_envelope.schema.json`。
  - 新增 `/bindings/detail` 与 Linux CLI `binding-detail`，可查看绑定 artifact、语义入口映射和分层约束。
  - 新增 `tools/check_central_brain_binding_artifacts.sh`，验证 AIDL/proto/schema artifact 存在、可解析并包含 Req ID。
  - 覆盖 Req ID：XSC-002、XSC-003、XSC-005、XSC-006、NV-P-001..006、DEL-001、DEL-002。
- 推进 A5 Native adapters mock：
  - 新增 `central-brain/backend/native_adapters.py`，建立 AIOS Kernel、SOA Service Adapter、Vehicle Signal Adapter、Model Runtime Adapter、Security/Policy Adapter 注册表。
  - 后端新增 `/native/adapters` 与 `/native/adapters/detail`，返回 Android 主开发路径、Linux 同步交付路径、Driver/HAL 依赖与虚拟化约束。
  - Linux CLI 与语义网关 smoke test 新增 `native-adapters-detail` 校验。
  - 覆盖 Req ID：XSC-004、NV-F-001、NV-F-003、NV-F-004、NV-F-005、NV-F-008、NV-F-009、NV-F-011、DEL-001、DEL-002、DEL-005。

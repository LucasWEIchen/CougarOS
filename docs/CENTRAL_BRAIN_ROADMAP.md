# Central Brain Android 13 开发路线图

版本：0.5
日期：2026-07-16
状态：Stage 2 implementation ready

## 1. 基线与范围

用户提供的架构图是需求基线，不是示意图。当前开发对象是不能修改厂商 Framework/BSP/预编译
系统组件的黑盒 Android 13 座舱控制器，主语言为 Java、AIDL、C 和 JNI。

2026-07-16 范围决策：

- Python 仿真 runtime、REST/JSON gateway、Linux Python binding/CLI/daemon、旧 Android Console
  和专用测试/部署样例全部退役。
- 当前不开发 Linux 前端，不开发 Hypervisor，不猜测 vendor property/device node/ioctl。
- 保留 Android ModelProvider/Scheduler、`vendor.npu.empty`、NPU C ABI/JNI、Driver/HAL gap、
  Safety 边界和真实硬件交付工具。
- Android deterministic provider、Digital Twin 和 Effect adapter 只允许位于 debug/test 范围，
  不得进入 production profile，也不得替代真实硬件验收。
- Driver/HAL 只有在公开/vendor SDK 确认不能满足具体接口后，才登记最小新增工作量。
- GitHub `LucasWEIchen/CougarOS` 是完整受维护源码和文档的唯一远端基线；每个完成增量必须
  commit、push、通过远端检查，并同步默认分支首页的架构、开发进度和近期记录。

主要 Req IDs：`APP-001/003/004`、`XSC-001..006`、`FW-U-001/003/004/006/007`、
`FW-S-001/003/005`、`NV-F-001/003/004/005/008/009/011/012`、
`NV-G-003/004/005/006/007`、`NV-P-002/006`、`KH-003/006`、
`DEL-001/003/004/005`、`S2-UX-001..003`、`S2-SES-001`、`S2-CTX-001`、
`S2-TWN-001`、`S2-SCN-001`、`S2-GRF-001`、`S2-SAF-001`、`S2-EFF-001`、
`S2-HMI-001..005`、
`S2-ADP-001/002`、`S2-TOL-001`、`S2-MEM-001`、`S2-EVT-001`、
`S2-MDL-001`、`S2-OBS-001`、`S2-REL-001`。

## 2. 成熟度规则

| 状态 | 含义 | 可接受证据 |
| --- | --- | --- |
| `contract_defined` | 类型、边界、错误、Req ID 已定义 | static/contract check |
| `prototype_implemented` | Android debug/test 中有确定性实现 | JVM/instrumentation test |
| `android_integrated` | Android 13 APK/AAR 链路运行 | Binder/API 33 device evidence |
| `hardware_validated` | 目标 vendor/车辆/NPU 接口通过 | target smoke/fault/rollback |
| `production_qualified` | 性能、安全、隐私、升级、运维关闭 | signed acceptance package |

禁止状态提升：

- test double 不能提升到 `hardware_validated`。
- empty provider 不能提升到 `android_integrated` 的硬件含义。
- 模拟器证据不能替代 ARM64 物理设备或车辆/NPU 证据。
- repository check 不能设置 `production_ready=true`。

## 3. 已完成 Android 基础阶段

| 阶段 | 目标 | 交付 | 状态 |
| --- | --- | --- | --- |
| R0 | 架构、Req ID、成熟度和偏差基线 | requirements/roadmap/issues/deviations | 已完成 |
| R1 | Android Gradle 多模块交付骨架 | AI SDK AAR、Runtime Service APK、Demo HMI APK | 已完成 |
| R2 | Typed/async Protocol Binding | production/diagnostic AIDL、Parcelable、callback/cancel/death | 已完成 |
| R3 | Android Runtime 核心 | Job Supervisor、可信 Binder 身份、capability/policy | 已完成 |
| R4 | Durable workflow | Room、checkpoint、approval、effect/outbox、recovery | 已完成（软件基础） |
| R5 | Model contract | ModelProvider、scheduler、test router、Vendor empty provider | 已完成（合同/测试） |
| R6 | Event/Memory/Skill | bounded runtime、durable cursor、middleware/readiness | 已完成（软件基础） |
| R7 | 应用集成 | aggregate acceptance、Client2 Binder、recovery、handoff | 已完成（应用层） |
| B0 | 黑盒实际工程基线 | Java/C/JNI/ABI/部署边界 | 已完成 |
| B1 | Native C Runtime | C ABI V1、JNI、arm64-v8a/x86_64 AAR | 已完成 |
| B2 | Runtime 集成 | Native lifecycle 接入 Binder Runtime 与 Diagnostic | 已完成 |
| B3 | 黑盒验收 | 公开 API 能力探测、安全安装、API 33 设备证据 | 已完成（模拟器 + 物理应用层） |
| B4 | 实际工程交付 | APK/AAR、hash/signer/ABI、安装和使用指南 | 已完成（软件交付） |
| B5 | 内网硬件闭环 | Private Release、结构化 Issue、脱敏复测 | 已建立，持续维护 |

上述“已完成”只描述各阶段的软件退出条件。真实车辆控制、Vendor NPU、Driver/HAL、production
signer、system/privileged deployment 和整车资格仍未完成。

## 4. Android 证据追踪键

| 增量键 | 当前证据边界 |
| --- | --- |
| R4_DURABLE_WORKFLOW | Room/outbox/recovery 软件基础完成，production effect delivery 关闭。 |
| R4C2B repository-only retry/terminal | retry/dead-letter 只证明 repository 语义。 |
| R5A1 model provider contract | Provider request/result/error/cancel 合同完成。 |
| R5A2 inference resource scheduler | deadline/priority/quota/cancel 合同完成。 |
| R5B1 deterministic stub provider | 只存在于 Android test/debug 验证，不是 NPU。 |
| R5B2 test-only model router | 只路由明确 test profile，release 不激活。 |
| R5C1 production-safe readiness | readiness 失败关闭，不提升硬件状态。 |
| R5D1 application-layer deployment | Vendor provider 仍为 empty。 |
| R6A1 bounded Event runtime | 有界 callback/overflow 软件语义完成。 |
| R6A2A durable Event schema | cursor/metadata schema 完成。 |
| R6A2B durable Event repository | owner/idempotency/reopen 完成。 |
| R6A3 Event runtime readiness | production broker/transport 仍阻塞。 |
| R6B1 bounded Memory lifecycle | metadata/digest 生命周期完成。 |
| R6B2 Memory runtime readiness | production privacy owner 仍阻塞。 |
| R6C1 signed built-in Skill runtime | 只允许内建 allowlist/test artifact。 |
| R6C2 fixed governance middleware chain | identity/policy/privacy/QoS/audit 顺序固定。 |
| R6C3 Skill/Governance readiness | production sandbox/route owner 仍阻塞。 |
| R7A1 aggregate Runtime acceptance | 聚合软件状态，不是测试证书。 |
| R7B Client2 SDK/Binder migration | HTTP/INTERNET fallback 已移除。 |
| R7C Android 13 application integration acceptance | Binder/UI/recovery 应用层矩阵完成。 |
| R7D Android 13 software handoff | Android artifacts/manifest/install/rollback 已结构化。 |

## 5. Python 原型退役

| 退役对象 | 现行替代 |
| --- | --- |
| Python Agent/REST gateway | `ICentralBrainRuntime` + `CentralBrainClient` |
| Python governance/policy | `ICentralBrainGovernance` + Binder identity/capability |
| Python readiness/audit | protected Diagnostics + Room audit + log/dumpsys snapshot |
| Python Ollama simulated NPU | Android `ModelProvider` contract；Vendor provider 当前为空 |
| Python hardware registry | NPU interface + Driver/HAL gap + Android activation gate |
| Android Console | Demo HMI + Client2 typed Binder panel |
| Linux Python CLI/daemon | 当前无替代交付；正式范围外 |

退役门禁：`tools/check_central_brain_python_prototype_retirement.sh`。决策记录：
`DEV-026`、`ISSUE-032` 和 `CENTRAL_BRAIN_PYTHON_PROTOTYPE_RETIREMENT.md`。

## 6. Stage 2 产品化计划

| 阶段 | 目标 | 主要交付 | 状态 |
| --- | --- | --- | --- |
| S2-P0 | 完整 AIOS Stage 2 设计冻结 | 调研、UX、最小工作包、详设、HMI 高保真稿件、验收指标 | 已完成 |
| S2-P1 | Runtime Contract v2 | Session、Context、Plan、Effect、Event typed contract | 下一阶段 |
| S2-P2 | Context 与 Digital Twin | Android debug/test context/twin；production 无 fallback | 未开始 |
| S2-P3 | Durable Agent Graph | plan/step/checkpoint/recovery/compensation | 未开始 |
| S2-P4 | 场景与 Effect 编排 | “我冷了”“我累了”“休息模式”等 | 未开始 |
| S2-P5 | Client2 中控 HMI 闭环 | HVAC/Seat 四视图、timeline、approval、partial、undo | 未开始 |
| S2-P6 | Memory/Event/Model 集成 | privacy lifecycle、proactive trigger、model routing | 未开始 |
| S2-P7 | 质量与发布 | fault matrix、性能、隐私、安全、升级 | 未开始 |
| S2-P8 | 真实车辆适配 | 按 capability 引入已确认的 vendor/public adapter | 外部阻塞 |

P0-P7 估算为 136-184 人日；其中 Client2 HVAC/Seat 中控闭环为 24-32 人日。该估算不含 Vendor
SDK、Driver/HAL、功能安全认证、量产 HMI 重写和
整车标定。详细工作包见 `CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md`。

下一实现工作包为 `P1-W01 Session DTO/AIDL`。执行顺序：

1. 增加 versioned Session DTO/AIDL，不修改既有 V1 checksum。
2. 增加 SDK client API、Runtime owner 和 default-deny capability。
3. 增加 JVM/AIDL/static checks，再执行 API 33 Binder instrumentation。
4. 更新 requirements/roadmap/deviation/issue/delivery/driver trace。
5. 不接入车辆/NPU/Driver/HAL，不恢复 Python gateway。

## 7. 近期进展

### 2026-07-15

- Client2 面板改为底部导航触发；默认隐藏，二次点击或面板外点击关闭。
- Android 13 ARM64 物理设备通过 UI、typed Binder、回复和 Runtime/Client2 recovery 验收。
- Stage 2 开源/行业调研、产品 UX、开发 backlog 和完整软件设计冻结。

### 2026-07-16

- 删除 Python backend、REST contract、Linux Python binding/CLI/daemon、旧 Console、Linux
  systemd 样例及相关 smoke/checker/docs。
- README、软件架构、接口、NPU、Driver/HAL、Safety、交付、偏差、问题和路线图改为 Android-only。
- 新增 Python 原型退役门禁，保留 Android 模型/NPU/Driver-HAL 真实硬件接口。
- 退役门禁、NPU 接口、交付文档、Stage 2 设计和 Android Runtime 聚合门禁全部通过。
- README 新增 GitHub source-of-truth、完整项目发布边界和已开发/未开发进度总表；pre-push 与
  Actions 扩展为覆盖全部 Central Brain 正式源码、Client2 patch、工程文档和工具。
- 冻结 Client2 中控 HVAC/Seat 演示闭环：四视图、手动/AI 统一 Effect 链、SIMULATED 标识、
  desired/reported、partial/undo/recovery，并将 P4 扩展为 12 个最小工作包。
- 基于现有 Client2 车模和右侧悬浮面板交付可点击四视图 UI/UX 原型、四张 1920x1080 PNG、
  视觉 token、Android 映射和静态门禁；仅完成 HMI-D0，HMI-D1/APK 实现仍未开始。
- GitHub 默认分支 `main` 是权威进度基线；开发分支合并后不得单独保留状态结论。

## 8. 当前门禁

必须通过：

```bash
bash tools/check_central_brain_python_prototype_retirement.sh
bash tools/check_central_brain_github_repository_completeness.sh
bash tools/check_central_brain_npu_interface.sh
bash tools/check_central_brain_virtualization_docs.sh
bash tools/check_central_brain_cockpit_hmi_design.sh
bash tools/check_central_brain_aios_stage2_design.sh
bash tools/check_central_brain_android_runtime_evolution.sh
```

当前状态：

```text
python_prototype_runtime_maintained=false
github_source_of_truth=true
github_sync_required=true
maintained_project_files_synced=true
github_homepage_architecture_current=true
design_baseline_complete=true
cockpit_hmi_design_mockups_ready=true
production_ready=false
target_hardware_validated=false
driver_development_triggered=false
virtualization_development_triggered=false
```

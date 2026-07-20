# Central Brain 最小需求说明目录

版本：1.0

日期：2026-07-20

本目录与根 `README.md` 的最细颗粒度需求跟进表一一对应。每个跟进 ID 只有一个稳定锚点；
README 的链接必须指向该锚点。本文定义开发和验收所需的最小语义，详细状态仍以所列权威
需求、backlog、路线图、偏差、问题、交付和 Driver/HAL 文档为准。

统一规则：

- `DONE`/`DEVELOPED` 只证明该行注明的软件与证据范围，不自动获得 production 或硬件含义。
- `EXTERNAL_BLOCKED` 必须失败关闭，不得用 debug/test double、UI 动画或默认值关闭。
- `SUSPENDED` 不包含仓库内执行器；`OUT_OF_SCOPE` 不创建当前交付代码。
- 所有输入、输出和证据必须保持有界、版本化、owner-scoped、可审计并避免原始敏感内容。
- 全局保持 `production_ready=false`、`target_hardware_validated=false`。

## P0 设计基线

<a id="p0-w01"></a>
### P0-W01 开源与行业证据基线

- **需求描述**：软件必须交付“开源与行业证据基线”，满足 全部，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：全部。
- **负责模块**：产品/架构文档与需求治理。
- **前置输入**：用户架构图、公开行业/开源证据、产品与工程约束。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：AIOS/座舱行业证据、采用与延后边界。
- **边界与非目标**：只冻结需求和设计，不形成 Runtime、车辆或 NPU 执行能力。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p0-w02"></a>
### P0-W02 产品与 UX 基线

- **需求描述**：软件必须交付“产品与 UX 基线”，满足 `S2-UX-001..003`, `S2-HMI-006`, `S2-SCN-001`, `S2-SAF-001`, `S2-EFF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-UX-001..003`, `S2-HMI-006`, `S2-SCN-001`, `S2-SAF-001`, `S2-EFF-001`。
- **负责模块**：产品/架构文档与需求治理。
- **前置输入**：用户架构图、公开行业/开源证据、产品与工程约束。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：意图优先、行驶限制、失败/补偿/撤销 UX。
- **边界与非目标**：只冻结需求和设计，不形成 Runtime、车辆或 NPU 执行能力。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p0-w03"></a>
### P0-W03 工程详设和 backlog

- **需求描述**：软件必须交付“工程详设和 backlog”，满足 全部，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：全部。
- **负责模块**：产品/架构文档与需求治理。
- **前置输入**：用户架构图、公开行业/开源证据、产品与工程约束。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：模块、接口、依赖、测试、DoD 与 Req ID 追踪。
- **边界与非目标**：只冻结需求和设计，不形成 Runtime、车辆或 NPU 执行能力。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

## P1 Runtime Contract v2

<a id="p1-w01"></a>
### P1-W01 Session DTO/AIDL

- **需求描述**：软件必须交付“Session DTO/AIDL”，满足 `S2-SES-001`, `S2-UX-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SES-001`, `S2-UX-001`。
- **负责模块**：central-brain-sdk、runtime-service、Room 持久化。
- **前置输入**：冻结的 AIDL/DTO 版本、Binder 身份、Session/Plan/Event/Effect 边界和 Room schema。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：五个有界 DTO；`session_contract_v1_defined=true`、`session_parcel_physical_android13_arm64_verified=true`。
- **边界与非目标**：Android 应用层合同/持久化证据不等于 VINTF stable AIDL、车辆/NPU 或量产 authority。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p1-w02"></a>
### P1-W02 Plan/Node DTO/AIDL

- **需求描述**：软件必须交付“Plan/Node DTO/AIDL”，满足 `S2-SCN-001`, `S2-GRF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SCN-001`, `S2-GRF-001`。
- **负责模块**：central-brain-sdk、runtime-service、Room 持久化。
- **前置输入**：冻结的 AIDL/DTO 版本、Binder 身份、Session/Plan/Event/Effect 边界和 Room schema。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：immutable DAG/11 node allowlist；`plan_contract_v1_defined=true`、`plan_parcel_physical_android13_arm64_verified=true`、`plan_runtime_published=false`。
- **边界与非目标**：Android 应用层合同/持久化证据不等于 VINTF stable AIDL、车辆/NPU 或量产 authority。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p1-w03"></a>
### P1-W03 Typed Event DTO/AIDL

- **需求描述**：软件必须交付“Typed Event DTO/AIDL”，满足 `S2-SES-001`, `S2-EVT-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SES-001`, `S2-EVT-001`。
- **负责模块**：central-brain-sdk、runtime-service、Room 持久化。
- **前置输入**：冻结的 AIDL/DTO 版本、Binder 身份、Session/Plan/Event/Effect 边界和 Room schema。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：cursor/replay/callback；`event_contract_v1_defined=true`、`event_parcel_physical_android13_arm64_verified=true`、`event_runtime_service_published=true`、`event_callback_service_published=true`。
- **边界与非目标**：Android 应用层合同/持久化证据不等于 VINTF stable AIDL、车辆/NPU 或量产 authority。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p1-w04"></a>
### P1-W04 Effect/Approval DTO

- **需求描述**：软件必须交付“Effect/Approval DTO”，满足 `S2-EFF-001`, `S2-SAF-001`, `S2-UX-003`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-EFF-001`, `S2-SAF-001`, `S2-UX-003`。
- **负责模块**：central-brain-sdk、runtime-service、Room 持久化。
- **前置输入**：冻结的 AIDL/DTO 版本、Binder 身份、Session/Plan/Event/Effect 边界和 Room schema。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：typed target/state/approval/undo；`effect_contract_v1_defined=true`、`effect_parcel_physical_android13_arm64_verified=true`、`effect_runtime_service_published=false`、`approval_response_service_published=false`、`undo_service_published=false`。
- **边界与非目标**：Android 应用层合同/持久化证据不等于 VINTF stable AIDL、车辆/NPU 或量产 authority。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p1-w05"></a>
### P1-W05 SDK facade v2

- **需求描述**：软件必须交付“SDK facade v2”，满足 `S2-SES-001`, `S2-UX-001..003`, `S2-EVT-001`, `APP-004`, `XSC-001/006`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SES-001`, `S2-UX-001..003`, `S2-EVT-001`, `APP-004`, `XSC-001/006`。
- **负责模块**：central-brain-sdk、runtime-service、Room 持久化。
- **前置输入**：冻结的 AIDL/DTO 版本、Binder 身份、Session/Plan/Event/Effect 边界和 Room schema。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：`ScenarioClient`/`SessionClient`、replay/resubscribe、Binder death/reconnect；`sdk_facade_v2_available=true`。
- **边界与非目标**：Android 应用层合同/持久化证据不等于 VINTF stable AIDL、车辆/NPU 或量产 authority。
- **当前状态**：`DONE / ANDROID13_ARM64_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p1-w06"></a>
### P1-W06 Room v4 schema

- **需求描述**：软件必须交付“Room v4 schema”，满足 `S2-SES-001`, `S2-GRF-001`, `S2-EFF-001`, `S2-EVT-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SES-001`, `S2-GRF-001`, `S2-EFF-001`, `S2-EVT-001`。
- **负责模块**：central-brain-sdk、runtime-service、Room 持久化。
- **前置输入**：冻结的 AIDL/DTO 版本、Binder 身份、Session/Plan/Event/Effect 边界和 Room schema。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：durable Session/Event、v3->v4 migration、process-death rehydration；`room_schema_version=4`。
- **边界与非目标**：Android 应用层合同/持久化证据不等于 VINTF stable AIDL、车辆/NPU 或量产 authority。
- **当前状态**：`DONE / ANDROID13_ARM64_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p1-w07"></a>
### P1-W07 Contract v2 aggregate

- **需求描述**：软件必须交付“Contract v2 aggregate”，满足 P1 全部，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：P1 全部。
- **负责模块**：central-brain-sdk、runtime-service、Room 持久化。
- **前置输入**：冻结的 AIDL/DTO 版本、Binder 身份、Session/Plan/Event/Effect 边界和 Room schema。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：AIDL hash、capability、bounds、Room、SDK 与 forbidden fallback 聚合门禁。
- **边界与非目标**：Android 应用层合同/持久化证据不等于 VINTF stable AIDL、车辆/NPU 或量产 authority。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p6-ev2"></a>
### P6-EV2 Durable Session Event V2

- **需求描述**：软件必须交付“Durable Session Event V2”，满足 `S2-EVT-001`, `S2-SES-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-EVT-001`, `S2-SES-001`。
- **负责模块**：runtime-service 的 Event、Trigger、Consent 与主动建议。
- **前置输入**：typed Event、Context source、Trigger rule、consent policy、QoS 和 HMI projection 输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：terminal cursor、owner/session Room ACK、SDK V2 negotiation/V1 fallback。
- **边界与非目标**：主动建议不得自动获得 Effect 权限；production middleware、可信来源和 consent owner 仍需外部输入。
- **当前状态**：`SOFTWARE_COMPLETE / ARM64_RETEST_OPEN`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

## P2 Context、Digital Twin、Scenario 与仿真 Effect

<a id="p2-w01"></a>
### P2-W01 Canonical Vehicle Signal Types

- **需求描述**：软件必须交付“Canonical Vehicle Signal Types”，满足 `S2-CTX-001`, `S2-TWN-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-CTX-001`, `S2-TWN-001`。
- **负责模块**：runtime-service 的 Context/Scenario/Digital Twin 与 debug adapter。
- **前置输入**：canonical signal/capability、受信时间与来源元数据、build-owned scenario asset；仿真项只允许 debug/test profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：12 路径、timestamp/quality/source/schema；`vehicle_signal_schema_defined=true`。
- **边界与非目标**：SIMULATED 能力不得进入 release/production registry，也不得作为真实车辆证据。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p2-w02"></a>
### P2-W02 Vehicle Capability Catalog

- **需求描述**：软件必须交付“Vehicle Capability Catalog”，满足 `S2-TWN-001`, `S2-ADP-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-TWN-001`, `S2-ADP-001`。
- **负责模块**：runtime-service 的 Context/Scenario/Digital Twin 与 debug adapter。
- **前置输入**：canonical signal/capability、受信时间与来源元数据、build-owned scenario asset；仿真项只允许 debug/test profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：8 capability、area/risk/adapter/authorization；production authorized=0。
- **边界与非目标**：SIMULATED 能力不得进入 release/production registry，也不得作为真实车辆证据。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p2-w03"></a>
### P2-W03 Vehicle Digital Twin Store

- **需求描述**：软件必须交付“Vehicle Digital Twin Store”，满足 `S2-TWN-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-TWN-001`。
- **负责模块**：runtime-service 的 Context/Scenario/Digital Twin 与 debug adapter。
- **前置输入**：canonical signal/capability、受信时间与来源元数据、build-owned scenario asset；仿真项只允许 debug/test profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：desired/reported/quality/revision；process-local、非 production trust。
- **边界与非目标**：SIMULATED 能力不得进入 release/production registry，也不得作为真实车辆证据。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p2-w04"></a>
### P2-W04 Trusted Context Snapshot

- **需求描述**：软件必须交付“Trusted Context Snapshot”，满足 `S2-CTX-001`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-CTX-001`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Context/Scenario/Digital Twin 与 debug adapter。
- **前置输入**：canonical signal/capability、受信时间与来源元数据、build-owned scenario asset；仿真项只允许 debug/test profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：freshness/trust/restricted report；production source 未接。
- **边界与非目标**：SIMULATED 能力不得进入 release/production registry，也不得作为真实车辆证据。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p2-w05"></a>
### P2-W05 Scenario Manifest

- **需求描述**：软件必须交付“Scenario Manifest”，满足 `S2-SCN-001`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SCN-001`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Context/Scenario/Digital Twin 与 debug adapter。
- **前置输入**：canonical signal/capability、受信时间与来源元数据、build-owned scenario asset；仿真项只允许 debug/test profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：schema/catalog/checksum；production-signed artifact 未接。
- **边界与非目标**：SIMULATED 能力不得进入 release/production registry，也不得作为真实车辆证据。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p2-w06"></a>
### P2-W06 Deterministic Scenario Resolver

- **需求描述**：软件必须交付“Deterministic Scenario Resolver”，满足 `S2-SCN-001`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SCN-001`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Context/Scenario/Digital Twin 与 debug adapter。
- **前置输入**：canonical signal/capability、受信时间与来源元数据、build-owned scenario asset；仿真项只允许 debug/test profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：fixed selection、availability fail-closed；model 未参与。
- **边界与非目标**：SIMULATED 能力不得进入 release/production registry，也不得作为真实车辆证据。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p2-w07"></a>
### P2-W07 Scenario Plan Compiler

- **需求描述**：软件必须交付“Scenario Plan Compiler”，满足 `S2-SCN-001`, `S2-GRF-001`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SCN-001`, `S2-GRF-001`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Context/Scenario/Digital Twin 与 debug adapter。
- **前置输入**：canonical signal/capability、受信时间与来源元数据、build-owned scenario asset；仿真项只允许 debug/test profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：immutable DAG、moving seat branch removal；Runtime publication 未接。
- **边界与非目标**：SIMULATED 能力不得进入 release/production registry，也不得作为真实车辆证据。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p2-w08"></a>
### P2-W08 Simulated Effect Adapter Base

- **需求描述**：软件必须交付“Simulated Effect Adapter Base”，满足 `S2-ADP-001`, `S2-EFF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-ADP-001`, `S2-EFF-001`。
- **负责模块**：runtime-service 的 Context/Scenario/Digital Twin 与 debug adapter。
- **前置输入**：canonical signal/capability、受信时间与来源元数据、build-owned scenario asset；仿真项只允许 debug/test profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：typed target、idempotency、desired/reported；debug-only。
- **边界与非目标**：SIMULATED 能力不得进入 release/production registry，也不得作为真实车辆证据。
- **当前状态**：`DONE / ANDROID13_ARM64_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p2-w09"></a>
### P2-W09 Simulated HVAC Adapter

- **需求描述**：软件必须交付“Simulated HVAC Adapter”，满足 `S2-ADP-001`, `S2-EFF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-ADP-001`, `S2-EFF-001`。
- **负责模块**：runtime-service 的 Context/Scenario/Digital Twin 与 debug adapter。
- **前置输入**：canonical signal/capability、受信时间与来源元数据、build-owned scenario asset；仿真项只允许 debug/test profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：power/temp/fan typed simulation/readback。
- **边界与非目标**：SIMULATED 能力不得进入 release/production registry，也不得作为真实车辆证据。
- **当前状态**：`DONE / ANDROID13_ARM64_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p2-w10"></a>
### P2-W10 Simulated Seat Adapter

- **需求描述**：软件必须交付“Simulated Seat Adapter”，满足 `S2-ADP-001`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-ADP-001`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Context/Scenario/Digital Twin 与 debug adapter。
- **前置输入**：canonical signal/capability、受信时间与来源元数据、build-owned scenario asset；仿真项只允许 debug/test profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：recline/heat typed simulation、dispatch revalidation。
- **边界与非目标**：SIMULATED 能力不得进入 release/production registry，也不得作为真实车辆证据。
- **当前状态**：`DONE / ANDROID13_ARM64_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p2-w11"></a>
### P2-W11 Simulated Media/Navigation Adapters

- **需求描述**：软件必须交付“Simulated Media/Navigation Adapters”，满足 `S2-ADP-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-ADP-001`。
- **负责模块**：runtime-service 的 Context/Scenario/Digital Twin 与 debug adapter。
- **前置输入**：canonical signal/capability、受信时间与来源元数据、build-owned scenario asset；仿真项只允许 debug/test profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：media state、synthetic navigation observation、digest-only query。
- **边界与非目标**：SIMULATED 能力不得进入 release/production registry，也不得作为真实车辆证据。
- **当前状态**：`DONE / ANDROID13_ARM64_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p2-w12"></a>
### P2-W12 Debug Simulation Controller

- **需求描述**：软件必须交付“Debug Simulation Controller”，满足 `S2-CTX-001`, `S2-ADP-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-CTX-001`, `S2-ADP-001`。
- **负责模块**：runtime-service 的 Context/Scenario/Digital Twin 与 debug adapter。
- **前置输入**：canonical signal/capability、受信时间与来源元数据、build-owned scenario asset；仿真项只允许 debug/test profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：signature/capability protected、revision ACK、release absent。
- **边界与非目标**：SIMULATED 能力不得进入 release/production registry，也不得作为真实车辆证据。
- **当前状态**：`DONE / ANDROID13_ARM64_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

## P3 Durable Agent Graph 与 Effect 闭环

<a id="p3-w01"></a>
### P3-W01 Agent Graph Runtime state machine

- **需求描述**：软件必须交付“Agent Graph Runtime state machine”，满足 `S2-GRF-001`, `NV-G-004/006/007`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-GRF-001`, `NV-G-004/006/007`。
- **负责模块**：runtime-service 的 Agent Graph、Effect 与恢复模块。
- **前置输入**：已验证的 typed Plan、Context/Policy/Safety digest、checkpoint 和 Effect/approval/undo 合同。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：10-state bounded control runtime、dependency-ready、failure closed。
- **边界与非目标**：软件 Graph/Effect 语义不得越过 production Effect、Safety、材料和 readback authority。
- **当前状态**：`DONE / DEBUG_RUNTIME`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p3-w02"></a>
### P3-W02 Typed node executors

- **需求描述**：软件必须交付“Typed node executors”，满足 `S2-GRF-001`, `S2-SAF-001`, `S2-EFF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-GRF-001`, `S2-SAF-001`, `S2-EFF-001`。
- **负责模块**：runtime-service 的 Agent Graph、Effect 与恢复模块。
- **前置输入**：已验证的 typed Plan、Context/Policy/Safety digest、checkpoint 和 Effect/approval/undo 合同。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：11 node schemas、7 deterministic executors；dispatch disabled。
- **边界与非目标**：软件 Graph/Effect 语义不得越过 production Effect、Safety、材料和 readback authority。
- **当前状态**：`DONE / CONTRACT`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p3-w03"></a>
### P3-W03 CheckpointSerializer

- **需求描述**：软件必须交付“CheckpointSerializer”，满足 `S2-GRF-001`, `NV-G-003/006/007`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-GRF-001`, `NV-G-003/006/007`。
- **负责模块**：runtime-service 的 Agent Graph、Effect 与恢复模块。
- **前置输入**：已验证的 typed Plan、Context/Policy/Safety digest、checkpoint 和 Effect/approval/undo 合同。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：registered DTO、canonical JSON、size/depth/digest guards。
- **边界与非目标**：软件 Graph/Effect 语义不得越过 production Effect、Safety、材料和 readback authority。
- **当前状态**：`DONE / CONTRACT`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p3-w04"></a>
### P3-W04 Retry/Timeout policy

- **需求描述**：软件必须交付“Retry/Timeout policy”，满足 `S2-GRF-001`, `NV-G-004`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-GRF-001`, `NV-G-004`。
- **负责模块**：runtime-service 的 Agent Graph、Effect 与恢复模块。
- **前置输入**：已验证的 typed Plan、Context/Policy/Safety digest、checkpoint 和 Effect/approval/undo 合同。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：bounded deadline/attempt/backoff；Effect retry requires reconciliation。
- **边界与非目标**：软件 Graph/Effect 语义不得越过 production Effect、Safety、材料和 readback authority。
- **当前状态**：`DONE / CONTRACT`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p3-w05"></a>
### P3-W05 P3 Durable approval interrupt

- **需求描述**：软件必须交付“P3 Durable approval interrupt”，满足 `S2-SAF-001`, `S2-UX-003`, `S2-GRF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SAF-001`, `S2-UX-003`, `S2-GRF-001`。
- **负责模块**：runtime-service 的 Agent Graph、Effect 与恢复模块。
- **前置输入**：已验证的 typed Plan、Context/Policy/Safety digest、checkpoint 和 Effect/approval/undo 合同。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：owner/plan/context/policy/Safety/expiry binding；authority 未发布。
- **边界与非目标**：软件 Graph/Effect 语义不得越过 production Effect、Safety、材料和 readback authority。
- **当前状态**：`DONE / CONTRACT`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p3-w06"></a>
### P3-W06 P3 EffectCoordinator

- **需求描述**：软件必须交付“P3 EffectCoordinator”，满足 `S2-EFF-001`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-EFF-001`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Agent Graph、Effect 与恢复模块。
- **前置输入**：已验证的 typed Plan、Context/Policy/Safety digest、checkpoint 和 Effect/approval/undo 合同。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：prepare-all、dependency waves、exact adapter registry；production dispatch=false。
- **边界与非目标**：软件 Graph/Effect 语义不得越过 production Effect、Safety、材料和 readback authority。
- **当前状态**：`DONE / CONTRACT`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p3-w07"></a>
### P3-W07 P3 Effect verification/reconciliation

- **需求描述**：软件必须交付“P3 Effect verification/reconciliation”，满足 `S2-EFF-001`, `S2-TWN-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-EFF-001`, `S2-TWN-001`。
- **负责模块**：runtime-service 的 Agent Graph、Effect 与恢复模块。
- **前置输入**：已验证的 typed Plan、Context/Policy/Safety digest、checkpoint 和 Effect/approval/undo 合同。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：delivered/applied/verified 分离、Twin reconcile、no redispatch。
- **边界与非目标**：软件 Graph/Effect 语义不得越过 production Effect、Safety、材料和 readback authority。
- **当前状态**：`DONE / CONTRACT`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p3-w08"></a>
### P3-W08 P3 Compensation/Undo

- **需求描述**：软件必须交付“P3 Compensation/Undo”，满足 `S2-EFF-001`, `S2-UX-003`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-EFF-001`, `S2-UX-003`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Agent Graph、Effect 与恢复模块。
- **前置输入**：已验证的 typed Plan、Context/Policy/Safety digest、checkpoint 和 Effect/approval/undo 合同。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：before snapshot、TTL、reverse dependency、new governed task。
- **边界与非目标**：软件 Graph/Effect 语义不得越过 production Effect、Safety、材料和 readback authority。
- **当前状态**：`DONE / CONTRACT`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p3-w09"></a>
### P3-W09 P3 Restart recovery

- **需求描述**：软件必须交付“P3 Restart recovery”，满足 `S2-SES-001`, `S2-GRF-001`, `S2-EFF-001`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SES-001`, `S2-GRF-001`, `S2-EFF-001`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Agent Graph、Effect 与恢复模块。
- **前置输入**：已验证的 typed Plan、Context/Policy/Safety digest、checkpoint 和 Effect/approval/undo 合同。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：Room recovery reducer、reconcile directives、exactly-once audit。
- **边界与非目标**：软件 Graph/Effect 语义不得越过 production Effect、Safety、材料和 readback authority。
- **当前状态**：`DONE / ANDROID13_ARM64_DEBUG_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r1"></a>
### P4-R1 Durable Runtime Orchestration V1

- **需求描述**：软件必须交付“Durable Runtime Orchestration V1”，满足 `S2-SES-001`, `S2-SCN-001`, `S2-GRF-001`, `S2-EFF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SES-001`, `S2-SCN-001`, `S2-GRF-001`, `S2-EFF-001`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：owner/session Binder、Plan/Graph/Effect/readback Room projection。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE / DEBUG_RUNTIME`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

## P4 Client2 中控闭环

<a id="p4-w01"></a>
### P4-W01 Bridge Session/Event API migration

- **需求描述**：软件必须交付“Bridge Session/Event API migration”，满足 `S2-UX-001`, `S2-HMI-005`, `XSC-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-UX-001`, `S2-HMI-005`, `XSC-001`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：Client2 typed Session/Event callbacks。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-w02"></a>
### P4-W02 Cockpit HMI state/reducer/reconnect

- **需求描述**：软件必须交付“Cockpit HMI state/reducer/reconnect”，满足 `S2-UX-001..003`, `S2-HMI-003/005/006`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-UX-001..003`, `S2-HMI-003/005/006`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：immutable state、single reducer、Binder reconnect。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-w03"></a>
### P4-W03 Intent-first overlay shell

- **需求描述**：软件必须交付“Intent-first overlay shell”，满足 `S2-HMI-001..003/006`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-HMI-001..003/006`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：Intent/Plan/Execution/Result 工程状态壳。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-w04"></a>
### P4-W04 P4 Client2 HVAC control surface

- **需求描述**：软件必须交付“P4 Client2 HVAC control surface”，满足 `S2-HMI-001/003/004/005`, `S2-ADP-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-HMI-001/003/004/005`, `S2-ADP-001`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：immutable desired、300 ms debounce、scenario ownership。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-w05"></a>
### P4-W05 P4 Client2 Seat control surface

- **需求描述**：软件必须交付“P4 Client2 Seat control surface”，满足 `S2-HMI-002..005`, `S2-SAF-001`, `S2-ADP-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-HMI-002..005`, `S2-SAF-001`, `S2-ADP-001`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：seat typed state、Safety/approval gate。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-w06"></a>
### P4-W06 P4 Client2 observable execution timeline

- **需求描述**：软件必须交付“P4 Client2 observable execution timeline”，满足 `S2-UX-001`, `S2-HMI-003/006`, `S2-EVT-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-UX-001`, `S2-HMI-003/006`, `S2-EVT-001`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：七阶段、最多八条 typed event、partial visibility。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-w07"></a>
### P4-W07 P4 Client2 approval/recovery UX

- **需求描述**：软件必须交付“P4 Client2 approval/recovery UX”，满足 `S2-UX-003`, `S2-HMI-003`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-UX-003`, `S2-HMI-003`, `S2-SAF-001`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：approval/partial/retry/undo projection。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-w08"></a>
### P4-W08 P4 Client2 driving restriction renderer

- **需求描述**：软件必须交付“P4 Client2 driving restriction renderer”，满足 `S2-UX-002`, `S2-HMI-002`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-UX-002`, `S2-HMI-002`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：PARKED/MOVING/UNKNOWN_RESTRICTED rendering。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-w09"></a>
### P4-W09 P4 Client2 engineer simulation drawer

- **需求描述**：软件必须交付“P4 Client2 engineer simulation drawer”，满足 `S2-HMI-004`, `S2-ADP-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-HMI-004`, `S2-ADP-001`, `S2-OBS-001`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：debug-only controller、fault/context controls。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-w10"></a>
### P4-W10 Scenario/manual-control synchronization

- **需求描述**：软件必须交付“Scenario/manual-control synchronization”，满足 `S2-HMI-001..006`, `S2-SCN-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-HMI-001..006`, `S2-SCN-001`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：canonical roles、lifecycle、sequence ownership。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-w11"></a>
### P4-W11 Accessibility/display matrix

- **需求描述**：软件必须交付“Accessibility/display matrix”，满足 `S2-UX-003`, `S2-HMI-001/002`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-UX-003`, `S2-HMI-001/002`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：48dp、非颜色单一表达、1920x1080 safe frame。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-w12"></a>
### P4-W12 P4 Android aggregate acceptance

- **需求描述**：软件必须交付“P4 Android aggregate acceptance”，满足 P4 全部，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：P4 全部。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：navigation/scenario/fault/restart/display application acceptance。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE / ANDROID13_ARM64_APP_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-d4a"></a>
### P4-D4a Simulated Scenario/Plan/Graph composition

- **需求描述**：软件必须交付“Simulated Scenario/Plan/Graph composition”，满足 `S2-SCN-001`, `S2-GRF-001`, `S2-EFF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SCN-001`, `S2-GRF-001`, `S2-EFF-001`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：fixed Cold/Fatigue graph、pending nodes。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE / DEBUG_ONLY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-d4b"></a>
### P4-D4b Debug Runtime Session/Event projection

- **需求描述**：软件必须交付“Debug Runtime Session/Event projection”，满足 `S2-SCN-001`, `S2-GRF-001`, `S2-EVT-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SCN-001`, `S2-GRF-001`, `S2-EVT-001`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：bounded Session/Event projection、digest-only payload。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE / DEBUG_ONLY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-d4c"></a>
### P4-D4c Signature-protected debug Binder

- **需求描述**：软件必须交付“Signature-protected debug Binder”，满足 `APP-004`, `XSC-001/004/005/006`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`APP-004`, `XSC-001/004/005/006`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：fixed 2x2 input、same-signer/capability、release absent。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE / DEBUG_ONLY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-d4d"></a>
### P4-D4d Debug simulated Effect/readback

- **需求描述**：软件必须交付“Debug simulated Effect/readback”，满足 `S2-EFF-001`, `S2-SAF-001`, `S2-HMI-003/006`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-EFF-001`, `S2-SAF-001`, `S2-HMI-003/006`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：4 adapters、7 targets、MATCHED readback、approval projection。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE / ANDROID13_ARM64_DEBUG_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-d4e"></a>
### P4-D4e Client2 simulated scenario chain

- **需求描述**：软件必须交付“Client2 simulated scenario chain”，满足 `APP-004`, `S2-SCN-001`, `S2-GRF-001`, `S2-EFF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`APP-004`, `S2-SCN-001`, `S2-GRF-001`, `S2-EFF-001`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：formal chain、seven-stage UI、Cold/Fatigue approve/reject。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE / ANDROID13_ARM64_DEBUG_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r2"></a>
### P4-R2 Client2 Orchestration V1 migration

- **需求描述**：软件必须交付“Client2 Orchestration V1 migration”，满足 `APP-004`, `S2-SES-001`, `S2-SCN-001`, `S2-EVT-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`APP-004`, `S2-SES-001`, `S2-SCN-001`, `S2-EVT-001`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：legacy scenario Binder removed；formal SDK/Room/Effect projection。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE / DEBUG_E2E`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r3"></a>
### P4-R3 Voice-first live cockpit HMI

- **需求描述**：软件必须交付“Voice-first live cockpit HMI”，满足 `S2-HMI-001..006`, `S2-MDL-001`, `S2-EFF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-HMI-001..006`, `S2-MDL-001`, `S2-EFF-001`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。
- **前置输入**：owner-scoped Session/Event/Orchestration SDK 投影、驾驶状态和显式 debug simulation profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：两个自然触发、32 行调用链、HVAC/Seat 动画；`voice_first_hmi_implemented=true`。
- **边界与非目标**：Client2 是演示 HMI；UI 动画和 debug readback 不等于真实车控或量产 HMI 资格。
- **当前状态**：`DONE / UI_SIMULATION_ONLY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[座舱 HMI 闭环计划](CENTRAL_BRAIN_COCKPIT_HMI_CONTROL_LOOP_PLAN.md)。

## P5 Tool、Skill 与 Memory

<a id="p5-w01"></a>
### P5-W01 P5 Tool Manifest/Schema

- **需求描述**：软件必须交付“P5 Tool Manifest/Schema”，满足 `S2-TOL-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-TOL-001`。
- **负责模块**：runtime-service 的 Tool、Skill、Memory 与 Context Budget。
- **前置输入**：版本化 Tool/Skill manifest、健康/签名证据、Session owner、Memory consent 与上下文预算。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：versioned manifest、bounded input/output schema。
- **边界与非目标**：process-local/contract-test 能力不得提升为 production Tool、Skill 或 Memory authority。
- **当前状态**：`DEVELOPED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p5-w02"></a>
### P5-W02 P5 Tool Registry/Resolver

- **需求描述**：软件必须交付“P5 Tool Registry/Resolver”，满足 `S2-TOL-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-TOL-001`。
- **负责模块**：runtime-service 的 Tool、Skill、Memory 与 Context Budget。
- **前置输入**：版本化 Tool/Skill manifest、健康/签名证据、Session owner、Memory consent 与上下文预算。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：version map、health freshness、registered/resolved/usable separation。
- **边界与非目标**：process-local/contract-test 能力不得提升为 production Tool、Skill 或 Memory authority。
- **当前状态**：`DEVELOPED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p5-w03"></a>
### P5-W03 P5 Tool RuleSolver

- **需求描述**：软件必须交付“P5 Tool RuleSolver”，满足 `S2-TOL-001`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-TOL-001`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Tool、Skill、Memory 与 Context Budget。
- **前置输入**：版本化 Tool/Skill manifest、健康/签名证据、Session owner、Memory consent 与上下文预算。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：rule x model x usable intersection、fail-closed。
- **边界与非目标**：process-local/contract-test 能力不得提升为 production Tool、Skill 或 Memory authority。
- **当前状态**：`DEVELOPED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p5-w04"></a>
### P5-W04 P5 Tool Executor boundary

- **需求描述**：软件必须交付“P5 Tool Executor boundary”，满足 `S2-TOL-001`, `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-TOL-001`, `S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：runtime-service 的 Tool、Skill、Memory 与 Context Budget。
- **前置输入**：版本化 Tool/Skill manifest、健康/签名证据、Session owner、Memory consent 与上下文预算。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：signed built-in executor、deadline/output bound、digest-only invocation。
- **边界与非目标**：process-local/contract-test 能力不得提升为 production Tool、Skill 或 Memory authority。
- **当前状态**：`DEVELOPED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p5-w05"></a>
### P5-W05 P5 Skill package verifier

- **需求描述**：软件必须交付“P5 Skill package verifier”，满足 `S2-TOL-001`, `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-TOL-001`, `S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：runtime-service 的 Tool、Skill、Memory 与 Context Budget。
- **前置输入**：版本化 Tool/Skill manifest、健康/签名证据、Session owner、Memory consent 与上下文预算。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：signer/version/digest/anti-downgrade；no dynamic load。
- **边界与非目标**：process-local/contract-test 能力不得提升为 production Tool、Skill 或 Memory authority。
- **当前状态**：`DEVELOPED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p5-w06"></a>
### P5-W06 P5 WorkingMemoryStore

- **需求描述**：软件必须交付“P5 WorkingMemoryStore”，满足 `S2-MEM-001`, `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MEM-001`, `S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：runtime-service 的 Tool、Skill、Memory 与 Context Budget。
- **前置输入**：版本化 Tool/Skill manifest、健康/签名证据、Session owner、Memory consent 与上下文预算。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：owner/session bounded process-local store。
- **边界与非目标**：process-local/contract-test 能力不得提升为 production Tool、Skill 或 Memory authority。
- **当前状态**：`DEVELOPED / PROCESS_LOCAL`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p5-w07"></a>
### P5-W07 P5 ProfileMemoryStore

- **需求描述**：软件必须交付“P5 ProfileMemoryStore”，满足 `S2-MEM-001`, `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MEM-001`, `S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：runtime-service 的 Tool、Skill、Memory 与 Context Budget。
- **前置输入**：版本化 Tool/Skill manifest、健康/签名证据、Session owner、Memory consent 与上下文预算。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：consent/field/user-seat binding；contract cipher only。
- **边界与非目标**：process-local/contract-test 能力不得提升为 production Tool、Skill 或 Memory authority。
- **当前状态**：`DEVELOPED / CONTRACT_TEST`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p5-w08"></a>
### P5-W08 P5 EpisodicMemoryStore

- **需求描述**：软件必须交付“P5 EpisodicMemoryStore”，满足 `S2-MEM-001`, `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MEM-001`, `S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：runtime-service 的 Tool、Skill、Memory 与 Context Budget。
- **前置输入**：版本化 Tool/Skill manifest、健康/签名证据、Session owner、Memory consent 与上下文预算。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：typed bounded scenario summaries；process-local。
- **边界与非目标**：process-local/contract-test 能力不得提升为 production Tool、Skill 或 Memory authority。
- **当前状态**：`DEVELOPED / PROCESS_LOCAL`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p5-w09"></a>
### P5-W09 P5 ContextBudgetManager

- **需求描述**：软件必须交付“P5 ContextBudgetManager”，满足 `S2-MEM-001`, `S2-MDL-001`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MEM-001`, `S2-MDL-001`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Tool、Skill、Memory 与 Context Budget。
- **前置输入**：版本化 Tool/Skill manifest、健康/签名证据、Session owner、Memory consent 与上下文预算。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：metadata-only budget decision；tokenizer/summary execution 未接。
- **边界与非目标**：process-local/contract-test 能力不得提升为 production Tool、Skill 或 Memory authority。
- **当前状态**：`DEVELOPED / DECISION_ONLY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p5-w10"></a>
### P5-W10 P5 Memory consent HMI/API

- **需求描述**：软件必须交付“P5 Memory consent HMI/API”，满足 `S2-MEM-001`, `S2-UX-003`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MEM-001`, `S2-UX-003`。
- **负责模块**：runtime-service 的 Tool、Skill、Memory 与 Context Budget。
- **前置输入**：版本化 Tool/Skill manifest、健康/签名证据、Session owner、Memory consent 与上下文预算。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：source visibility、disable、clear preference、moving restriction。
- **边界与非目标**：process-local/contract-test 能力不得提升为 production Tool、Skill 或 Memory authority。
- **当前状态**：`DEVELOPED / PROJECTION_ONLY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p5-r1"></a>
### P5-R1 Debug Runtime composition

- **需求描述**：软件必须交付“Debug Runtime composition”，满足 P5 + `S2-GRF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：P5 + `S2-GRF-001`。
- **负责模块**：runtime-service 的 Tool、Skill、Memory 与 Context Budget。
- **前置输入**：版本化 Tool/Skill manifest、健康/签名证据、Session owner、Memory consent 与上下文预算。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：Tool/Skill/ContextBudget/WorkingMemory combined evidence。
- **边界与非目标**：process-local/contract-test 能力不得提升为 production Tool、Skill 或 Memory authority。
- **当前状态**：`DONE / DEBUG_ONLY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

## P6 Event 与主动智能

<a id="p6-w01"></a>
### P6-W01 P6 EventBroker interface/in-process

- **需求描述**：软件必须交付“P6 EventBroker interface/in-process”，满足 `S2-EVT-001`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-EVT-001`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Event、Trigger、Consent 与主动建议。
- **前置输入**：typed Event、Context source、Trigger rule、consent policy、QoS 和 HMI projection 输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：typed topic、append-before-notify、bounded replay、identity policy。
- **边界与非目标**：主动建议不得自动获得 Effect 权限；production middleware、可信来源和 consent owner 仍需外部输入。
- **当前状态**：`DEVELOPED / PROCESS_LOCAL`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p6-w02"></a>
### P6-W02 P6 Event Backpressure/QoS

- **需求描述**：软件必须交付“P6 Event Backpressure/QoS”，满足 `S2-EVT-001`, `NV-G-004`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-EVT-001`, `NV-G-004`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Event、Trigger、Consent 与主动建议。
- **前置输入**：typed Event、Context source、Trigger rule、consent policy、QoS 和 HMI projection 输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：4 policies、critical no-silent-drop、consumer isolation。
- **边界与非目标**：主动建议不得自动获得 Effect 权限；production middleware、可信来源和 consent owner 仍需外部输入。
- **当前状态**：`DEVELOPED / PROCESS_LOCAL`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p6-w03"></a>
### P6-W03 P6 TriggerRule/TriggerEngine

- **需求描述**：软件必须交付“P6 TriggerRule/TriggerEngine”，满足 `S2-EVT-001`, `S2-SCN-001`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-EVT-001`, `S2-SCN-001`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Event、Trigger、Consent 与主动建议。
- **前置输入**：typed Event、Context source、Trigger rule、consent policy、QoS 和 HMI projection 输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：threshold/window/debounce/cooldown、suggestion-only。
- **边界与非目标**：主动建议不得自动获得 Effect 权限；production middleware、可信来源和 consent owner 仍需外部输入。
- **当前状态**：`DEVELOPED / PROCESS_LOCAL`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p6-w04"></a>
### P6-W04 P6 Proactive consent/policy

- **需求描述**：软件必须交付“P6 Proactive consent/policy”，满足 `S2-SAF-001`, `S2-MEM-001`, `S2-EVT-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SAF-001`, `S2-MEM-001`, `S2-EVT-001`。
- **负责模块**：runtime-service 的 Event、Trigger、Consent 与主动建议。
- **前置输入**：typed Event、Context source、Trigger rule、consent policy、QoS 和 HMI projection 输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：exact grant binding、HIGH/CRITICAL hard block、TTL/revoke。
- **边界与非目标**：主动建议不得自动获得 Effect 权限；production middleware、可信来源和 consent owner 仍需外部输入。
- **当前状态**：`DEVELOPED / POLICY_ONLY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p6-w05"></a>
### P6-W05 P6 Context source adapters

- **需求描述**：软件必须交付“P6 Context source adapters”，满足 `S2-CTX-001`, `S2-EVT-001`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-CTX-001`, `S2-EVT-001`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Event、Trigger、Consent 与主动建议。
- **前置输入**：typed Event、Context source、Trigger rule、consent policy、QoS 和 HMI projection 输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：Runtime health/time/simulated vehicle normalization。
- **边界与非目标**：主动建议不得自动获得 Effect 权限；production middleware、可信来源和 consent owner 仍需外部输入。
- **当前状态**：`DEVELOPED / NORMALIZATION_ONLY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p6-w06"></a>
### P6-W06 P6 Active suggestion UX

- **需求描述**：软件必须交付“P6 Active suggestion UX”，满足 `S2-UX-002`, `S2-TRG-002`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-UX-002`, `S2-TRG-002`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Event、Trigger、Consent 与主动建议。
- **前置输入**：typed Event、Context source、Trigger rule、consent policy、QoS 和 HMI projection 输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：full/minimal card、merge/replay、never-ask。
- **边界与非目标**：主动建议不得自动获得 Effect 权限；production middleware、可信来源和 consent owner 仍需外部输入。
- **当前状态**：`DEVELOPED / PROJECTION_ONLY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p6-p7-r1"></a>
### P6-P7-R1 Debug decision composition

- **需求描述**：软件必须交付“Debug decision composition”，满足 P6 + P7 + `S2-GRF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：P6 + P7 + `S2-GRF-001`。
- **负责模块**：runtime-service 的 Event、Trigger、Consent 与主动建议。
- **前置输入**：typed Event、Context source、Trigger rule、consent policy、QoS 和 HMI projection 输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：Context/Trigger/Consent/Model/Event chain、no action authority。
- **边界与非目标**：主动建议不得自动获得 Effect 权限；production middleware、可信来源和 consent owner 仍需外部输入。
- **当前状态**：`DONE / DEBUG_ONLY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

## P7 Model Runtime 与模型网关

<a id="p7-w01"></a>
### P7-W01 P7 ModelRequest/Result v2

- **需求描述**：软件必须交付“P7 ModelRequest/Result v2”，满足 `S2-MDL-001`, `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MDL-001`, `S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：runtime-service 的 Model Provider/Registry/Router 与模型网关。
- **前置输入**：ModelRequest/Result、Provider health、routing policy、资源快照、结构化输出 schema 与座舱 prompt。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：privacy/latency/token/capability/fallback/digest contract。
- **边界与非目标**：模型输出只提供候选计划，不能授权 Effect；debug/Ollama/OpenClaw 证据不等于 Vendor NPU 或量产资格。
- **当前状态**：`DEVELOPED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p7-w02"></a>
### P7-W02 P7 ModelProviderRegistry/health

- **需求描述**：软件必须交付“P7 ModelProviderRegistry/health”，满足 `S2-MDL-001`, `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MDL-001`, `S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：runtime-service 的 Model Provider/Registry/Router 与模型网关。
- **前置输入**：ModelRequest/Result、Provider health、routing policy、资源快照、结构化输出 schema 与座舱 prompt。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：5-provider catalog、health source/freshness/replay。
- **边界与非目标**：模型输出只提供候选计划，不能授权 Effect；debug/Ollama/OpenClaw 证据不等于 Vendor NPU 或量产资格。
- **当前状态**：`DEVELOPED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p7-w03"></a>
### P7-W03 P7 PolicyAwareModelRouter

- **需求描述**：软件必须交付“P7 PolicyAwareModelRouter”，满足 `S2-MDL-001`, `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MDL-001`, `S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：runtime-service 的 Model Provider/Registry/Router 与模型网关。
- **前置输入**：ModelRequest/Result、Provider health、routing policy、资源快照、结构化输出 schema 与座舱 prompt。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：privacy/network/thermal/latency/quota/fallback decision。
- **边界与非目标**：模型输出只提供候选计划，不能授权 Effect；debug/Ollama/OpenClaw 证据不等于 Vendor NPU 或量产资格。
- **当前状态**：`DEVELOPED / NO_ACTION_AUTHORITY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p7-w04"></a>
### P7-W04 P7 LocalModelProvider

- **需求描述**：软件必须交付“P7 LocalModelProvider”，满足 `S2-MDL-001`, `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MDL-001`, `S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：runtime-service 的 Model Provider/Registry/Router 与模型网关。
- **前置输入**：ModelRequest/Result、Provider health、routing policy、资源快照、结构化输出 schema 与座舱 prompt。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：deadline/cancel/stream limits；debug source only。
- **边界与非目标**：模型输出只提供候选计划，不能授权 Effect；debug/Ollama/OpenClaw 证据不等于 Vendor NPU 或量产资格。
- **当前状态**：`DEVELOPED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p7-w05"></a>
### P7-W05 P7 Structured Model Output

- **需求描述**：软件必须交付“P7 Structured Model Output”，满足 `S2-MDL-001`, `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MDL-001`, `S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：runtime-service 的 Model Provider/Registry/Router 与模型网关。
- **前置输入**：ModelRequest/Result、Provider health、routing policy、资源快照、结构化输出 schema 与座舱 prompt。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：exact JSON、scenario binding、action allowlist、raw log=false。
- **边界与非目标**：模型输出只提供候选计划，不能授权 Effect；debug/Ollama/OpenClaw 证据不等于 Vendor NPU 或量产资格。
- **当前状态**：`DEVELOPED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p7-w06"></a>
### P7-W06 P7 Scenario Evaluation

- **需求描述**：软件必须交付“P7 Scenario Evaluation”，满足 `S2-MDL-001`, `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MDL-001`, `S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：runtime-service 的 Model Provider/Registry/Router 与模型网关。
- **前置输入**：ModelRequest/Result、Provider health、routing policy、资源快照、结构化输出 schema 与座舱 prompt。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：deterministic evaluation harness、bounded metrics。
- **边界与非目标**：模型输出只提供候选计划，不能授权 Effect；debug/Ollama/OpenClaw 证据不等于 Vendor NPU 或量产资格。
- **当前状态**：`DEVELOPED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p7-w07"></a>
### P7-W07 P7 Resource Admission

- **需求描述**：软件必须交付“P7 Resource Admission”，满足 `S2-MDL-001`, `NV-G-004`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MDL-001`, `NV-G-004`。
- **负责模块**：runtime-service 的 Model Provider/Registry/Router 与模型网关。
- **前置输入**：ModelRequest/Result、Provider health、routing policy、资源快照、结构化输出 schema 与座舱 prompt。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：foreground priority、thermal degradation、fail-closed。
- **边界与非目标**：模型输出只提供候选计划，不能授权 Effect；debug/Ollama/OpenClaw 证据不等于 Vendor NPU 或量产资格。
- **当前状态**：`DEVELOPED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p7-r2"></a>
### P7-R2 WSL Ollama development gateway

- **需求描述**：软件必须交付“WSL Ollama development gateway”，满足 `S2-MDL-001`, `S2-SAF-001`, `S2-OBS-001`, `XSC-001/005/006`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MDL-001`, `S2-SAF-001`, `S2-OBS-001`, `XSC-001/005/006`。
- **负责模块**：runtime-service 的 Model Provider/Registry/Router 与模型网关。
- **前置输入**：ModelRequest/Result、Provider health、routing policy、资源快照、结构化输出 schema 与座舱 prompt。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：real debug model via ADB reverse、strict output、Client2 projection。
- **边界与非目标**：模型输出只提供候选计划，不能授权 Effect；debug/Ollama/OpenClaw 证据不等于 Vendor NPU 或量产资格。
- **当前状态**：`DONE / ANDROID13_ARM64_DEBUG_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[Ollama 网关设计](CENTRAL_BRAIN_OLLAMA_MODEL_GATEWAY.md)。

<a id="p7-r3-oc2"></a>
### P7-R3-OC2 OpenClaw target transitional gateway

- **需求描述**：软件必须交付“OpenClaw target transitional gateway”，满足 `S2-MDL-001/002`, `S2-SAF-001`, `S2-OBS-001/002`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MDL-001/002`, `S2-SAF-001`, `S2-OBS-001/002`。
- **负责模块**：runtime-service 的 Model Provider/Registry/Router 与模型网关。
- **前置输入**：ModelRequest/Result、Provider health、routing policy、资源快照、结构化输出 schema 与座舱 prompt。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：fixed WebSocket v3、challenge/auth/send/history/abort、Client2 projection；`openclaw_target_integration_implemented=true`。
- **边界与非目标**：模型输出只提供候选计划，不能授权 Effect；debug/Ollama/OpenClaw 证据不等于 Vendor NPU 或量产资格。
- **当前状态**：`TRANSITIONAL / CURRENT_CONNECTIVITY_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[OpenClaw 网关设计](CENTRAL_BRAIN_OPENCLAW_TARGET_GATEWAY.md)。

## P9 软件接口与调试证据

<a id="p9-w01"></a>
### P9-W01 P9 Performance Budget Contract

- **需求描述**：软件必须交付“P9 Performance Budget Contract”，满足 `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-OBS-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：7 categories/10 metrics、strict report、synthetic probe。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`SOFTWARE_COMPLETE / TARGET_MEASUREMENT_OPEN`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w02"></a>
### P9-W02 P9 Stability Fault Matrix Contract

- **需求描述**：软件必须交付“P9 Stability Fault Matrix Contract”，满足 `S2-REL-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-REL-001`, `S2-OBS-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：3 workloads x 6 faults = 18 cases、strict report。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`SOFTWARE_COMPLETE / 72H_TARGET_OPEN`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w03a"></a>
### P9-W03a P9 Parser Security Corpus

- **需求描述**：软件必须交付“P9 Parser Security Corpus”，满足 `S2-SAF-001`, `S2-TOL-001`, `S2-SES-001`, `S2-MDL-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SAF-001`, `S2-TOL-001`, `S2-SES-001`, `S2-MDL-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：3 parser surfaces/18 hostile cases、deterministic regression。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`DONE / HOST_REGRESSION`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w03b"></a>
### P9-W03b P9 Identity/Replay Security Corpus

- **需求描述**：软件必须交付“P9 Identity/Replay Security Corpus”，满足 `S2-SAF-001`, `S2-SES-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SAF-001`, `S2-SES-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：caller/session/signer 3 surfaces/18 cases。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`DONE / HOST_POLICY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w03c"></a>
### P9-W03c P9 Security Boundary Inventory

- **需求描述**：软件必须交付“P9 Security Boundary Inventory”，满足 `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：37 public AIDL items、8 validation families、debug probe。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`DONE / ANDROID13_ARM64_DEBUG_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w03d"></a>
### P9-W03d Binder identity device evidence

- **需求描述**：软件必须交付“Binder identity device evidence”，满足 `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：UID/package/current signer、spoof negative case。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`DONE / ANDROID13_ARM64_DEBUG_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w03e"></a>
### P9-W03e Callback replay device evidence

- **需求描述**：软件必须交付“Callback replay device evidence”，满足 `S2-SAF-001`, `S2-TOL-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SAF-001`, `S2-TOL-001`, `S2-OBS-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：task/sequence/single-terminal/cross-owner isolation。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`DONE / ANDROID13_ARM64_DEBUG_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w03f"></a>
### P9-W03f Executable parser robustness campaign

- **需求描述**：“Executable parser robustness campaign”的历史可执行实现必须保持退役，仓库不得重新引入该能力；相关需求只保留审计记录。
- **需求追踪**：`S2-SAF-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：验收要求仓库中无现行执行路径，并保留“历史实现已按用户决定完整撤回”的历史结论。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`RETIRED / NOT_CURRENT_CAPABILITY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w03g"></a>
### P9-W03g External security evidence interface

- **需求描述**：“External security evidence interface”只允许保留非执行接口和状态记录，不得在仓库内启用执行器或自动传输。
- **需求追踪**：`S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：验收只检查接口字段、禁止项和执行器缺失；当前交付为“8-field metadata/digest/reference interface；无 executor/transport”。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`SUSPENDED / EXTERNAL_INTERFACE_ONLY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w04a"></a>
### P9-W04a P9 Privacy Data Inventory

- **需求描述**：软件必须交付“P9 Privacy Data Inventory”，满足 `S2-MEM-001`, `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MEM-001`, `S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：12 surfaces、retention/delete/export/log/enforcement classification。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w04b"></a>
### P9-W04b P9 Privacy Policy Admission

- **需求描述**：软件必须交付“P9 Privacy Policy Admission”，满足 `S2-MEM-001`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MEM-001`, `S2-SAF-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：owner evidence、delete/hold/export guards；draft denied。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`DONE / OWNER_POLICY_OPEN`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w04c"></a>
### P9-W04c P9 Privacy Redaction/Audit Probe

- **需求描述**：软件必须交付“P9 Privacy Redaction/Audit Probe”，满足 `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：21 keys、10 forbidden field classes、debug Activity。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`DONE / TARGET_PROBE_OPEN`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w05a"></a>
### P9-W05a P9 Production Release Admission

- **需求描述**：软件必须交付“P9 Production Release Admission”，满足 `S2-REL-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-REL-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：exact 3 APK set、same-signer/cohort/version/schema/rollback gate。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`DONE / OWNER_OPEN`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w05b"></a>
### P9-W05b P9 Production Release Metadata Probe

- **需求描述**：软件必须交付“P9 Production Release Metadata Probe”，满足 `S2-REL-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-REL-001`, `S2-OBS-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：27-key projection、read-only no-install adapter。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`DONE / TARGET_REHEARSAL_OPEN`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w06a"></a>
### P9-W06a Driver Safety Admission

- **需求描述**：软件必须交付“Driver Safety Admission”，满足 `S2-UX-002`, `S2-SAF-001`, `S2-EFF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-UX-002`, `S2-SAF-001`, `S2-EFF-001`, `S2-OBS-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：12 actions、4 UX profiles、500 ms state、moving hard interlock。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`DONE / OEM_OWNER_OPEN`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w06b"></a>
### P9-W06b Driver Safety Redacted Probe

- **需求描述**：软件必须交付“Driver Safety Redacted Probe”，满足 `S2-SAF-001`, `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：27-key projection、DUMP Activity、no-install adapter。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`DONE / TARGET_MATRIX_OPEN`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w07a"></a>
### P9-W07a Release Evidence Envelope

- **需求描述**：软件必须交付“Release Evidence Envelope”，满足 `S2-OBS-001`, `S2-REL-001`, `DEL-001/004/005`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-OBS-001`, `S2-REL-001`, `DEL-001/004/005`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：release identity、8 diagnostic categories、stable report digest。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`DONE / TARGET_OWNER_OPEN`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w07b"></a>
### P9-W07b Field Diagnostics Probe

- **需求描述**：软件必须交付“Field Diagnostics Probe”，满足 `S2-OBS-001`, `S2-REL-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-OBS-001`, `S2-REL-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：31-key projection、5 executed + 3 NOT_RUN adapter。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`DONE / TARGET_REPORT_OPEN`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p9-w07c"></a>
### P9-W07c Release Retest Workflow

- **需求描述**：软件必须交付“Release Retest Workflow”，满足 `S2-REL-001`, `DEL-004/005`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-REL-001`, `DEL-004/005`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：5 states/5 transitions、replacement identity、four-party digest。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **当前状态**：`DONE / RETEST_OPEN`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p10-r1"></a>
### P10-R1 P10-R1 Android repository software completion

- **需求描述**：软件必须交付“P10-R1 Android repository software completion”，满足 全部已分类 Req IDs，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：全部已分类 Req IDs。
- **负责模块**：仓库级需求治理与软件完成度门禁。
- **前置输入**：全部分类后的 Req ID、工作包状态、专项合同和聚合门禁结果。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：`repository_software_requirements_complete=true`、`unclassified_repository_requirement_count=0`。
- **边界与非目标**：仓库软件完成只说明需求已分类和软件门禁通过，不表示外部激活完成。
- **当前状态**：`DONE / EXTERNAL_ACTIVATION_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

## P3-P7 量产激活剩余项

<a id="p3-act-01"></a>
### P3-ACT-01 Agent Graph production wiring

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“Agent Graph production wiring”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-GRF-001`, `S2-EFF-001`。
- **负责模块**：对应 Runtime 模块与目标平台集成 owner。
- **前置输入**：Effect material/authority、retention/encryption、target fault evidence；`ISSUE-022`。
- **输出与验收**：完成条件：取得并审查“Effect material/authority、retention/encryption、target fault evidence；`ISSUE-022`”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：禁止在外部 authority、目标接口和证据缺失时激活 production 路径。
- **当前状态**：`SOFTWARE_COMPLETE / EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

<a id="p4-act-01"></a>
### P4-ACT-01 中控 AIOS 真实车辆闭环

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“中控 AIOS 真实车辆闭环”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-HMI-001..006`, `S2-EFF-001`, `S2-ADP-002`。
- **负责模块**：对应 Runtime 模块与目标平台集成 owner。
- **前置输入**：vehicle service、approval/undo authority、target validation；`ISSUE-019/023/030/033`。
- **输出与验收**：完成条件：取得并审查“vehicle service、approval/undo authority、target validation；`ISSUE-019/023/030/033`”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：禁止在外部 authority、目标接口和证据缺失时激活 production 路径。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

<a id="p5-act-01"></a>
### P5-ACT-01 Tool/Skill/Memory production publication

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“Tool/Skill/Memory production publication”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-TOL-001`, `S2-MEM-001`。
- **负责模块**：对应 Runtime 模块与目标平台集成 owner。
- **前置输入**：signer、storage、identity、privacy owner；`ISSUE-040..045`。
- **输出与验收**：完成条件：取得并审查“signer、storage、identity、privacy owner；`ISSUE-040..045`”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：禁止在外部 authority、目标接口和证据缺失时激活 production 路径。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

<a id="p6-act-01"></a>
### P6-ACT-01 Durable production Event/Trigger/Consent

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“Durable production Event/Trigger/Consent”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-EVT-001`, `S2-SAF-001`。
- **负责模块**：对应 Runtime 模块与目标平台集成 owner。
- **前置输入**：publisher/middleware/source/identity/receipt owner；`ISSUE-031/046`。
- **输出与验收**：完成条件：取得并审查“publisher/middleware/source/identity/receipt owner；`ISSUE-031/046`”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：禁止在外部 authority、目标接口和证据缺失时激活 production 路径。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

<a id="p7-act-01"></a>
### P7-ACT-01 Production Model Provider

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“Production Model Provider”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-MDL-001/002`, `S2-OBS-001/002`。
- **负责模块**：对应 Runtime 模块与目标平台集成 owner。
- **前置输入**：TLS、credential owner、health/version、artifact、resource producer、NPU evidence；`ISSUE-024/044/054`。
- **输出与验收**：完成条件：取得并审查“TLS、credential owner、health/version、artifact、resource producer、NPU evidence；`ISSUE-024/044/054`”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：禁止在外部 authority、目标接口和证据缺失时激活 production 路径。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

## P8 真实目标 Adapter

<a id="p8-w01"></a>
### P8-W01 P8 Target Capability Discovery

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“P8 Target Capability Discovery”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-ADP-002`。
- **负责模块**：目标平台集成 owner、Vehicle/Vendor/NPU adapter。
- **前置输入**：14-column software contract/collector 已完成；缺 OEM property/service/permission/owner/version evidence；`ISSUE-047`。
- **输出与验收**：完成条件：取得并审查“14-column software contract/collector 已完成；缺 OEM property/service/permission/owner/version evidence；`ISSUE-047`”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：不得猜测 OEM/Vendor property、service、ABI、权限、buffer 或 Driver/HAL；缺失时返回 unavailable。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

<a id="p8-w02"></a>
### P8-W02 VSS to AAOS mapping

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“VSS to AAOS mapping”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-ADP-002`。
- **负责模块**：目标平台集成 owner、Vehicle/Vendor/NPU adapter。
- **前置输入**：缺公开 CarProperty/service schema、area/type/read-write/permission。
- **输出与验收**：完成条件：取得并审查“缺公开 CarProperty/service schema、area/type/read-write/permission”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：不得猜测 OEM/Vendor property、service、ABI、权限、buffer 或 Driver/HAL；缺失时返回 unavailable。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

<a id="p8-w03"></a>
### P8-W03 AaosCarPropertyEffectAdapter

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“AaosCarPropertyEffectAdapter”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-ADP-002`。
- **负责模块**：目标平台集成 owner、Vehicle/Vendor/NPU adapter。
- **前置输入**：缺 AAOS property contract、权限、readback、fault/rollback evidence。
- **输出与验收**：完成条件：取得并审查“缺 AAOS property contract、权限、readback、fault/rollback evidence”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：不得猜测 OEM/Vendor property、service、ABI、权限、buffer 或 Driver/HAL；缺失时返回 unavailable。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

<a id="p8-w04"></a>
### P8-W04 Vendor service adapter

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“Vendor service adapter”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-ADP-002`。
- **负责模块**：目标平台集成 owner、Vehicle/Vendor/NPU adapter。
- **前置输入**：缺 Vendor service ABI/AIDL、owner、version、permission。
- **输出与验收**：完成条件：取得并审查“缺 Vendor service ABI/AIDL、owner、version、permission”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：不得猜测 OEM/Vendor property、service、ABI、权限、buffer 或 Driver/HAL；缺失时返回 unavailable。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

<a id="p8-w05"></a>
### P8-W05 Vendor NPU provider

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“Vendor NPU provider”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-MDL-001`, `S2-ADP-002`。
- **负责模块**：目标平台集成 owner、Vehicle/Vendor/NPU adapter。
- **前置输入**：缺 Vendor SDK、PCIe runtime、model artifact、memory/cancel/performance evidence。
- **输出与验收**：完成条件：取得并审查“缺 Vendor SDK、PCIe runtime、model artifact、memory/cancel/performance evidence”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：不得猜测 OEM/Vendor property、service、ABI、权限、buffer 或 Driver/HAL；缺失时返回 unavailable。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

<a id="p8-w06"></a>
### P8-W06 Production activation evidence

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“Production activation evidence”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-ADP-002`, `DEL-005`。
- **负责模块**：目标平台集成 owner、Vehicle/Vendor/NPU adapter。
- **前置输入**：每项 capability 的 owner/ABI/permission/safety/smoke/rollback 证据未提供。
- **输出与验收**：完成条件：取得并审查“每项 capability 的 owner/ABI/permission/safety/smoke/rollback 证据未提供”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：不得猜测 OEM/Vendor property、service、ABI、权限、buffer 或 Driver/HAL；缺失时返回 unavailable。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

## P9 目标资格剩余项

<a id="p9-ext-01"></a>
### P9-EXT-01 Target performance measurement

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“Target performance measurement”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-OBS-001`。
- **负责模块**：目标测试、OEM/Vendor owner 与发布责任人。
- **前置输入**：owner-approved 30-sample target evidence；`ISSUE-048`。
- **输出与验收**：完成条件：取得并审查“owner-approved 30-sample target evidence；`ISSUE-048`”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：只能由命名 owner/tester 提交的受控目标证据关闭，仓库自身不能自动判定通过。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

<a id="p9-ext-02"></a>
### P9-EXT-02 72h stability/fault run

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“72h stability/fault run”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-REL-001`, `S2-OBS-001`。
- **负责模块**：目标测试、OEM/Vendor owner 与发布责任人。
- **前置输入**：real workload/fault injection/72h evidence；`ISSUE-049`。
- **输出与验收**：完成条件：取得并审查“real workload/fault injection/72h evidence；`ISSUE-049`”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：只能由命名 owner/tester 提交的受控目标证据关闭，仓库自身不能自动判定通过。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

<a id="p9-ext-03"></a>
### P9-EXT-03 Security external evidence

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“Security external evidence”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-SAF-001`。
- **负责模块**：目标测试、OEM/Vendor owner 与发布责任人。
- **前置输入**：executable campaign suspended；只接受批准的非秘密 evidence interface；`ISSUE-050`。
- **输出与验收**：完成条件：取得并审查“executable campaign suspended；只接受批准的非秘密 evidence interface；`ISSUE-050`”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：只能由命名 owner/tester 提交的受控目标证据关闭，仓库自身不能自动判定通过。
- **当前状态**：`SUSPENDED / EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

<a id="p9-ext-04"></a>
### P9-EXT-04 Privacy owner policy and enforcement

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“Privacy owner policy and enforcement”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-MEM-001`, `S2-SAF-001`。
- **负责模块**：目标测试、OEM/Vendor owner 与发布责任人。
- **前置输入**：owner ceiling/evidence、repository enforcement、target probe；`ISSUE-051`。
- **输出与验收**：完成条件：取得并审查“owner ceiling/evidence、repository enforcement、target probe；`ISSUE-051`”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：只能由命名 owner/tester 提交的受控目标证据关闭，仓库自身不能自动判定通过。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

<a id="p9-ext-05"></a>
### P9-EXT-05 Production signer/OTA/rollback

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“Production signer/OTA/rollback”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-REL-001`。
- **负责模块**：目标测试、OEM/Vendor owner 与发布责任人。
- **前置输入**：signer owner、candidate、MDM/OTA、rollback rehearsal；`ISSUE-052`。
- **输出与验收**：完成条件：取得并审查“signer owner、candidate、MDM/OTA、rollback rehearsal；`ISSUE-052`”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：只能由命名 owner/tester 提交的受控目标证据关闭，仓库自身不能自动判定通过。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

<a id="p9-ext-06"></a>
### P9-EXT-06 OEM driver-safety acceptance

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“OEM driver-safety acceptance”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-UX-002`, `S2-SAF-001`, `S2-EFF-001`。
- **负责模块**：目标测试、OEM/Vendor owner 与发布责任人。
- **前置输入**：trusted state、DMS/identity、seat/HVAC policy、hard interlock、owner sign-off；`ISSUE-029/030`。
- **输出与验收**：完成条件：取得并审查“trusted state、DMS/identity、seat/HVAC policy、hard interlock、owner sign-off；`ISSUE-029/030`”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：只能由命名 owner/tester 提交的受控目标证据关闭，仓库自身不能自动判定通过。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

<a id="p9-ext-07"></a>
### P9-EXT-07 Complete release/field retest

- **需求描述**：系统必须在外部输入完整、可审查并通过准入后实现或激活“Complete release/field retest”；输入不完整时必须失败关闭，不得用仿真或默认值替代。
- **需求追踪**：`S2-OBS-001`, `S2-REL-001`, `DEL-004/005`。
- **负责模块**：目标测试、OEM/Vendor owner 与发布责任人。
- **前置输入**：named replacement release、target report、owner/tester evidence；`ISSUE-052/053`。
- **输出与验收**：完成条件：取得并审查“named replacement release、target report、owner/tester evidence；`ISSUE-052/053`”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。
- **边界与非目标**：只能由命名 owner/tester 提交的受控目标证据关闭，仓库自身不能自动判定通过。
- **当前状态**：`EXTERNAL_BLOCKED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[交付目标](CENTRAL_BRAIN_DELIVERY_TARGETS.md)；[Driver/HAL 边界](CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md)。

## 明确挂起或范围外

<a id="scope-01"></a>
### SCOPE-01 Python simulation runtime

- **需求描述**：项目不得把“Python simulation runtime”作为当前 Android 13 交付的一部分；只保留必要的接口、迁移或范围记录。
- **需求追踪**：用户批准的范围约束。
- **负责模块**：项目治理；不分配实现模块。
- **前置输入**：已退役；`python_prototype_runtime_maintained=false`。
- **输出与验收**：通过仓库静态门禁证明当前交付中不存在被禁止的实现；范围结论保持 OUT_OF_SCOPE。
- **边界与非目标**：该条目不创建可执行工作包；需要恢复时必须重新立项并分配 Req ID。
- **当前状态**：`OUT_OF_SCOPE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。
<a id="scope-02"></a>
### SCOPE-02 Linux frontend

- **需求描述**：项目不得把“Linux frontend”作为当前 Android 13 交付的一部分；只保留必要的接口、迁移或范围记录。
- **需求追踪**：用户批准的范围约束。
- **负责模块**：项目治理；不分配实现模块。
- **前置输入**：本阶段只交付 Android 13 座舱应用。
- **输出与验收**：通过仓库静态门禁证明当前交付中不存在被禁止的实现；范围结论保持 OUT_OF_SCOPE。
- **边界与非目标**：该条目不创建可执行工作包；需要恢复时必须重新立项并分配 Req ID。
- **当前状态**：`OUT_OF_SCOPE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="scope-03"></a>
### SCOPE-03 Hypervisor / ASIL-QM partition implementation

- **需求描述**：项目不得把“Hypervisor / ASIL-QM partition implementation”作为当前 Android 13 交付的一部分；只保留必要的接口、迁移或范围记录。
- **需求追踪**：用户批准的范围约束。
- **负责模块**：项目治理；不分配实现模块。
- **前置输入**：用户明确不开发虚拟化；只保留外部 Safety 接口。
- **输出与验收**：通过仓库静态门禁证明当前交付中不存在被禁止的实现；范围结论保持 OUT_OF_SCOPE。
- **边界与非目标**：该条目不创建可执行工作包；需要恢复时必须重新立项并分配 Req ID。
- **当前状态**：`OUT_OF_SCOPE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="scope-04"></a>
### SCOPE-04 Unapproved kernel/Driver/HAL extension

- **需求描述**：在未取得独立批准和明确接口基线前，项目不得实现或推断“Unapproved kernel/Driver/HAL extension”。
- **需求追踪**：用户批准的范围约束。
- **负责模块**：项目治理；不分配实现模块。
- **前置输入**：仅在公开/Vendor API 经证明确有缺口时创建最小工作包。
- **输出与验收**：通过仓库静态门禁证明当前交付中不存在被禁止的实现；范围结论保持 SUSPENDED。
- **边界与非目标**：该条目不创建可执行工作包；需要恢复时必须重新立项并分配 Req ID。
- **当前状态**：`SUSPENDED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="scope-05"></a>
### SCOPE-05 Unconfirmed protocol binding

- **需求描述**：在未取得独立批准和明确接口基线前，项目不得实现或推断“Unconfirmed protocol binding”。
- **需求追踪**：用户批准的范围约束。
- **负责模块**：项目治理；不分配实现模块。
- **前置输入**：架构图未确认的协议不得推断实现。
- **输出与验收**：通过仓库静态门禁证明当前交付中不存在被禁止的实现；范围结论保持 SUSPENDED。
- **边界与非目标**：该条目不创建可执行工作包；需要恢复时必须重新立项并分配 Req ID。
- **当前状态**：`SUSPENDED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

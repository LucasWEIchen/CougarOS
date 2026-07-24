# Central Brain 最小需求说明目录

版本：1.1

日期：2026-07-20

本目录与根 `README.md` 的最细颗粒度需求跟进表一一对应。每个跟进 ID 只有一个稳定锚点；
README 的链接必须指向该锚点。本文定义开发和验收所需的最小语义，详细状态仍以所列权威
需求、backlog、路线图、偏差、问题、交付和 Driver/HAL 文档为准。
每个章节同时记录与 README 完全一致的 GitHub 代码段链接；代码缺失时只能指向空接口、准入、退役或
范围门禁，并必须保留相应的非实现分类。

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
- **代码对应**：设计门禁：[check_central_brain_aios_stage2_design.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_aios_stage2_design.sh#L4-L22)。
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
- **代码对应**：设计门禁：[check_central_brain_cockpit_hmi_design.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_cockpit_hmi_design.sh#L4-L22)。
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
- **代码对应**：设计门禁：[check_central_brain_aios_stage2_design.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_aios_stage2_design.sh#L4-L22)。
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
- **代码对应**：合同实现：[SessionContract.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/session/SessionContract.java#L8-L26)；[check_central_brain_android_session_contract.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_session_contract.sh#L1-L19)。
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
- **代码对应**：合同实现：[PlanContract.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/plan/PlanContract.java#L16-L34)；[check_central_brain_android_plan_contract.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_plan_contract.sh#L1-L19)。
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
- **代码对应**：合同实现：[EventContract.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/event/EventContract.java#L13-L31)；[check_central_brain_android_event_contract.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_event_contract.sh#L1-L19)。
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
- **代码对应**：合同实现：[EffectContract.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/effect/EffectContract.java#L9-L27)；[check_central_brain_android_effect_contract.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_effect_contract.sh#L1-L19)。
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
- **代码对应**：合同实现：[ScenarioClient.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/ScenarioClient.java#L10-L28)；[check_central_brain_android_sdk_facade.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_sdk_facade.sh#L4-L22)。
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
- **代码对应**：合同实现：[CentralBrainDatabase.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/CentralBrainDatabase.java#L30-L48)；[check_central_brain_android_room_v4.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_room_v4.sh#L4-L22)。
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
- **代码对应**：合同实现：[CentralBrainSdk.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainSdk.java#L8-L25)；[check_central_brain_runtime_contract_v2.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_runtime_contract_v2.sh#L4-L22)。
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
- **代码对应**：合同实现：[EventV2Contract.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/event/EventV2Contract.java#L7-L25)；[check_central_brain_android_event_v2.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_event_v2.sh#L4-L22)。
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
- **代码对应**：软件实现：[VehicleSignalPath.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/vehicle/schema/VehicleSignalPath.java#L9-L27)；[check_central_brain_android_vehicle_signal_schema.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_vehicle_signal_schema.sh#L4-L22)。
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
- **代码对应**：软件实现：[CapabilityCatalog.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/vehicle/capability/CapabilityCatalog.java#L14-L32)；[check_central_brain_android_vehicle_capability_catalog.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_vehicle_capability_catalog.sh#L4-L22)。
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
- **代码对应**：软件实现：[VehicleDigitalTwinStore.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/vehicle/twin/VehicleDigitalTwinStore.java#L13-L31)；[check_central_brain_android_vehicle_digital_twin.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_vehicle_digital_twin.sh#L4-L22)。
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
- **代码对应**：软件实现：[ContextSnapshotBuilder.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/context/ContextSnapshotBuilder.java#L30-L48)；[check_central_brain_android_context_snapshot.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_context_snapshot.sh#L4-L22)。
- **当前状态**：`DONE`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)。

<a id="p2-w05"></a>
### P2-W05 Scenario Manifest

- **需求描述**：软件必须交付“Scenario Manifest”，满足 `S2-SCN-001`, `S2-SAF-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-SCN-001`, `S2-SAF-001`。
- **负责模块**：runtime-service 的 Context/Scenario/Digital Twin 与 debug adapter。
- **前置输入**：canonical signal/capability、受信时间与来源元数据、build-owned scenario asset；仿真项只允许 debug/test profile。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：4 个版本化 build-owned 场景、strict schema、catalog digest 和 checksum；production-signed artifact 未接。
- **边界与非目标**：SIMULATED 能力不得进入 release/production registry，也不得作为真实车辆证据。
- **代码对应**：软件实现：[ScenarioManifestParser.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/scenario/ScenarioManifestParser.java#L38-L56)；[check_central_brain_android_scenario_manifest.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_scenario_manifest.sh#L4-L22)。
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
- **代码对应**：软件实现：[DeterministicScenarioResolver.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/scenario/DeterministicScenarioResolver.java#L31-L49)；[check_central_brain_android_scenario_resolver.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_scenario_resolver.sh#L4-L22)。
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
- **代码对应**：软件实现：[ScenarioPlanCompiler.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/scenario/ScenarioPlanCompiler.java#L30-L48)；[check_central_brain_android_scenario_plan_compiler.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_scenario_plan_compiler.sh#L4-L22)。
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
- **代码对应**：Debug 实现：[SimulatedEffectAdapter.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/simulation/SimulatedEffectAdapter.java#L15-L33)；[check_central_brain_android_simulated_effect_adapter.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_simulated_effect_adapter.sh#L4-L22)。
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
- **代码对应**：Debug 实现：[SimulatedHvacEffectAdapter.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/simulation/SimulatedHvacEffectAdapter.java#L23-L41)；[check_central_brain_android_simulated_hvac_adapter.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_simulated_hvac_adapter.sh#L4-L22)。
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
- **代码对应**：Debug 实现：[SimulatedSeatEffectAdapter.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/simulation/SimulatedSeatEffectAdapter.java#L27-L45)；[check_central_brain_android_simulated_seat_adapter.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_simulated_seat_adapter.sh#L4-L22)。
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
- **代码对应**：Debug 实现：[SimulatedMediaEffectAdapter.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/simulation/SimulatedMediaEffectAdapter.java#L15-L33)；[check_central_brain_android_simulated_media_navigation_adapters.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_simulated_media_navigation_adapters.sh#L4-L22)。
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
- **代码对应**：Debug 实现：[DebugSimulationController.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/simulation/DebugSimulationController.java#L26-L44)；[check_central_brain_android_debug_simulation_controller.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_debug_simulation_controller.sh#L4-L22)。
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
- **代码对应**：软件实现：[AgentGraphRuntime.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java#L28-L46)；[check_central_brain_android_agent_graph_runtime.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_agent_graph_runtime.sh#L4-L22)。
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
- **代码对应**：软件实现：[NodeExecutorRegistry.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/NodeExecutorRegistry.java#L20-L38)；[check_central_brain_android_typed_node_executors.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_typed_node_executors.sh#L4-L22)。
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
- **代码对应**：软件实现：[CheckpointSerializer.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/CheckpointSerializer.java#L7-L25)；[check_central_brain_android_checkpoint_serializer.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_checkpoint_serializer.sh#L4-L22)。
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
- **代码对应**：软件实现：[NodeRetryPolicy.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/NodeRetryPolicy.java#L9-L27)；[check_central_brain_android_retry_timeout_policy.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_retry_timeout_policy.sh#L4-L22)。
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
- **代码对应**：软件实现：[ApprovalInterruptExecutor.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/ApprovalInterruptExecutor.java#L12-L30)；[check_central_brain_android_approval_interrupt.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_approval_interrupt.sh#L4-L22)。
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
- **代码对应**：软件实现：[EffectCoordinator.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectCoordinator.java#L19-L37)；[check_central_brain_android_effect_coordinator.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_effect_coordinator.sh#L4-L22)。
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
- **代码对应**：软件实现：[EffectVerifier.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectVerifier.java#L25-L43)；[check_central_brain_android_effect_verification.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_effect_verification.sh#L4-L22)。
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
- **代码对应**：软件实现：[CompensationPlanner.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/CompensationPlanner.java#L26-L44)；[check_central_brain_android_compensation_undo.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_compensation_undo.sh#L4-L22)。
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
- **代码对应**：软件实现：[GraphRestartReconciler.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/GraphRestartReconciler.java#L25-L43)；[check_central_brain_android_graph_restart_recovery.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_graph_restart_recovery.sh#L4-L22)。
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
- **代码对应**：Client2/Runtime 实现：[OrchestrationEndpoint.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/orchestration/OrchestrationEndpoint.java#L25-L43)；[check_central_brain_android_orchestration_v1.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_orchestration_v1.sh#L4-L22)。
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
- **代码对应**：Client2/Runtime 实现：[Client2ScenarioBridge.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/Client2ScenarioBridge.java#L25-L43)；[check_central_brain_android_client2_binder.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_client2_binder.sh#L4-L22)。
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
- **代码对应**：Client2/Runtime 实现：[CockpitHmiReducer.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitHmiReducer.java#L12-L30)；[check_central_brain_android_client2_hmi_reducer.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_client2_hmi_reducer.sh#L4-L22)。
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
- **代码对应**：Client2/Runtime 实现：[main_layout.central_brain_panel.xml](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/patches/main_layout.central_brain_panel.xml#L2-L20)；[check_central_brain_android_client2_intent_shell.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_client2_intent_shell.sh#L4-L22)。
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
- **代码对应**：Client2/Runtime 实现：[HvacControlIntent.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/HvacControlIntent.java#L9-L27)；[check_central_brain_android_client2_hvac_surface.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_client2_hvac_surface.sh#L4-L22)。
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
- **代码对应**：Client2/Runtime 实现：[SeatControlIntent.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/SeatControlIntent.java#L8-L26)；[check_central_brain_android_client2_seat_surface.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_client2_seat_surface.sh#L4-L22)。
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
- **代码对应**：Client2/Runtime 实现：[CockpitExecutionTimeline.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitExecutionTimeline.java#L16-L34)；[check_central_brain_android_client2_execution_timeline.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_client2_execution_timeline.sh#L4-L22)。
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
- **代码对应**：Client2/Runtime 实现：[CockpitRecoveryState.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitRecoveryState.java#L8-L26)；[check_central_brain_android_client2_recovery_ux.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_client2_recovery_ux.sh#L4-L22)。
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
- **代码对应**：Client2/Runtime 实现：[DrivingUxPolicy.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/DrivingUxPolicy.java#L4-L22)；[check_central_brain_android_client2_driving_restriction.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_client2_driving_restriction.sh#L4-L22)。
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
- **代码对应**：Client2/Runtime 实现：[DebugSimulationControllerClient.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/DebugSimulationControllerClient.java#L20-L38)；[check_central_brain_android_client2_engineer_simulation.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_client2_engineer_simulation.sh#L4-L22)。
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
- **代码对应**：Client2/Runtime 实现：[CockpitScenarioControlState.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitScenarioControlState.java#L11-L29)；[check_central_brain_android_client2_scenario_sync.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_client2_scenario_sync.sh#L4-L22)。
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
- **代码对应**：Client2/Runtime 实现：[CockpitDisplayPolicy.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitDisplayPolicy.java#L4-L22)；[check_central_brain_android_client2_accessibility_display.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_client2_accessibility_display.sh#L4-L22)。
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
- **代码对应**：Client2/Runtime 实现：[CockpitControlCoordinator.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java#L31-L49)；[check_central_brain_android_client2_p4_acceptance.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_client2_p4_acceptance.sh#L4-L22)。
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
- **代码对应**：Debug 实现：[SimulatedScenarioGraph.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedScenarioGraph.java#L30-L48)；[check_central_brain_android_simulated_scenario_graph.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_simulated_scenario_graph.sh#L4-L22)。
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
- **代码对应**：Debug 实现：[SimulatedScenarioRuntime.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedScenarioRuntime.java#L20-L38)；[check_central_brain_android_simulated_scenario_runtime.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_simulated_scenario_runtime.sh#L4-L22)。
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
- **代码对应**：Debug 实现：[SimulatedScenarioRuntimeService.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedScenarioRuntimeService.java#L30-L48)；[check_central_brain_android_simulated_scenario_binder.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_simulated_scenario_binder.sh#L4-L22)。
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
- **代码对应**：Debug 实现：[SimulatedScenarioEffectComposition.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedScenarioEffectComposition.java#L29-L47)；[check_central_brain_android_simulated_effect_composition.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_simulated_effect_composition.sh#L4-L22)。
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
- **代码对应**：Debug 实现：[Client2ScenarioBridge.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/Client2ScenarioBridge.java#L25-L43)；[check_central_brain_android_client2_simulated_scenario_chain.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_client2_simulated_scenario_chain.sh#L1-L19)。
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
- **代码对应**：Client2/Runtime 实现：[OrchestrationRuntimeClient.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/OrchestrationRuntimeClient.java#L29-L47)；[check_central_brain_android_client2_orchestration_migration.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_client2_orchestration_migration.sh#L4-L22)。
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
- **代码对应**：Client2/Runtime 实现：[CockpitControlCoordinator.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java#L31-L49)；[check_central_brain_android_voice_first_hmi.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_voice_first_hmi.sh#L4-L22)。
- **当前状态**：`DONE / UI_SIMULATION_ONLY`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[座舱 HMI 闭环计划](CENTRAL_BRAIN_COCKPIT_HMI_CONTROL_LOOP_PLAN.md)。

<a id="p4-r4"></a>
### P4-R4 Multimodal model I/O live HMI

- **需求描述**：软件必须交付“Multimodal model I/O live HMI”，满足 `S2-HMI-003/007/008`, `S2-MDL-002`, `S2-OBS-002`, `S2-SAF-001`, `XSC-001/005/006`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-HMI-003/007/008`, `S2-MDL-002`, `S2-OBS-002`, `S2-SAF-001`, `XSC-001/005/006`。
- **负责模块**：Client2 HMI、Central Brain Java SDK 与 Runtime 模型 I/O 投影。
- **前置输入**：实际模型 transcript、单张受控 PNG/JPEG、实际模型回复、run-bound aggregate digest 和可信 driving state。
- **输出与验收**：`处理一下` 将 2,244,206-byte 座舱帧经 FD Binder 与文字绑定到同一 OpenClaw 请求；Client2 显示缩略图、居中预览、实时输入/输出、白名单动作和模拟 Effect/Readback；Android 13 ARM64 1920x1080 通过。
- **边界与非目标**：当前是受控帧调试入口，不是实时摄像头；原始内容不持久化，模型不授权 Effect，车辆总线未访问。
- **代码对应**：合同实现：[CockpitMultimodalInput.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitMultimodalInput.java#L19-L45)；[DevelopmentModelInput.aidl](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/central-brain-sdk/src/debug/aidl/com/centralbrain/sdk/model/DevelopmentModelInput.aidl#L5-L16)；[DevelopmentModelInputStore.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/DevelopmentModelInputStore.java#L13-L43)；[central_brain_android_multimodal_model_io_hmi_requirement_v1.json](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/contracts/central_brain_android_multimodal_model_io_hmi_requirement_v1.json#L2-L20)。
- **当前状态**：`DONE / ANDROID13_ARM64_CONTROLLED_FRAME_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[产品 UX](CENTRAL_BRAIN_AIOS_STAGE2_PRODUCT_UX_PLAN.md)；[接口详设](CENTRAL_BRAIN_INTERFACE_DESIGN.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r5a"></a>
### P4-R5a Controlled multimodal input binding

- **需求描述**：软件必须交付“Controlled multimodal input binding”，满足 `APP-004`, `S2-HMI-008`, `S2-PER-001`, `S2-OBS-001`，把文字、单图 FD、摘要、Session 和场景绑定为一次消费输入。
- **需求追踪**：`APP-004`, `S2-HMI-008`, `S2-PER-001`, `S2-OBS-001`。
- **负责模块**：Client2 多模态输入、SDK debug AIDL 与 Runtime input store。
- **前置输入**：当前 Session/scenario、文字“处理一下”、单张受控 PNG、byte count 和 SHA-256。
- **输出与验收**：receipt 与 aggregate digest 一致；图片只消费一次；2,244,206-byte 夹具在 ARM64 真机进入同一模型请求。
- **边界与非目标**：只完成受控帧 debug 入口；不接 Camera HAL；原图不得持久化或写入普通日志。
- **代码对应**：Debug 实现：[CockpitMultimodalInput.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitMultimodalInput.java#L19-L45)；[DevelopmentModelInputStore.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/DevelopmentModelInputStore.java#L13-L43)。
- **当前状态**：`DONE / CONTROLLED_FRAME_ARM64_VERIFIED`。
- **权威依据**：[购物与路径规划详设](CENTRAL_BRAIN_CABIN_SHOPPING_ROUTE_PLANNING_DESIGN.md)；[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r5b"></a>
### P4-R5b Cabin observation projection

- **需求描述**：软件必须交付“Cabin observation projection”，满足 `S2-PER-001`, `S2-MDL-002`, `S2-SAF-001`, `S2-OBS-001`，只投影座位区域占用和可见饮水容器事实。
- **需求追踪**：`S2-PER-001`, `S2-MDL-002`, `S2-SAF-001`, `S2-OBS-001`。
- **负责模块**：Runtime model boundary 与 Client2 event projection。
- **前置输入**：已验证 frame digest、模型图片消费证明和场景节点状态。
- **输出与验收**：调用链显示三个已占用区域和后排右侧饮水容器，并与当前 Run 绑定。
- **边界与非目标**：不得输出年龄、身份、家庭关系、容器为空或口渴；观察不能授权 Tool 或 Effect。
- **代码对应**：Debug 实现：[OrchestrationRuntimeClient.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/OrchestrationRuntimeClient.java#L620-L660)；[central_brain_android_cabin_shopping_route_planning_v1.json](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/contracts/central_brain_android_cabin_shopping_route_planning_v1.json#L1-L24)。
- **当前状态**：`DONE / DEBUG_PROJECTION`。
- **权威依据**：[购物与路径规划详设](CENTRAL_BRAIN_CABIN_SHOPPING_ROUTE_PLANNING_DESIGN.md)；[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r5c"></a>
### P4-R5c Controlled multi-seat Context

- **需求描述**：软件必须交付“Controlled multi-seat Context”，满足 `S2-CTX-001/002`, `S2-TWN-001`, `S2-SAF-001`，使场景支持 CABIN 与四座位区域并投影当前夹具占用。
- **需求追踪**：`S2-CTX-001/002`, `S2-TWN-001`, `S2-SAF-001`。
- **负责模块**：Scenario manifest、debug decision composition 和 Client2 feedback。
- **前置输入**：受控图片、车辆坐标区域和 debug simulation profile。
- **输出与验收**：场景 zones 包含四座位；UI 显示三个已占用区域并保持 synthetic 边界。
- **边界与非目标**：不声称已融合真实座椅传感器；生产可信占用源缺失时保持 unavailable。
- **代码对应**：Debug 实现：[scene.cabin.multimodal.assist.v1.json](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/assets/scenarios/scene.cabin.multimodal.assist.v1.json#L1-L30)；[CockpitControlCoordinator.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java#L1140-L1180)。
- **当前状态**：`DONE / CONTROLLED_CONTEXT`。
- **权威依据**：[购物与路径规划详设](CENTRAL_BRAIN_CABIN_SHOPPING_ROUTE_PLANNING_DESIGN.md)；[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r5d"></a>
### P4-R5d Shopping semantic model allowlist

- **需求描述**：软件必须交付“Shopping semantic model allowlist”，满足 `S2-MDL-001/002`, `S2-PER-001`, `S2-INT-001`, `S2-SAF-001`，将多模态输出限制为购物与购买路线候选。
- **需求追踪**：`S2-MDL-001/002`, `S2-PER-001`, `S2-INT-001`, `S2-SAF-001`。
- **负责模块**：Cockpit prompt、OpenClaw/Ollama provider 和 structured output validator。
- **前置输入**：文字、图片、汽车座舱上下文、scenario digest 和动作 allowlist。
- **输出与验收**：必须包含 `shopping.search_products` 与 `navigation.plan_purchase_route`；不得要求 HVAC/Media。
- **边界与非目标**：模型不能生成订单提交、导航启动、支付授权、Tool receipt 或 Effect grant。
- **代码对应**：Debug 实现：[CockpitModelPrompt.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/CockpitModelPrompt.java#L75-L115)；[CockpitModelPromptTest.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/model/CockpitModelPromptTest.java#L1-L35)。
- **当前状态**：`DONE / REAL_MODEL_VERIFIED`。
- **权威依据**：[购物与路径规划详设](CENTRAL_BRAIN_CABIN_SHOPPING_ROUTE_PLANNING_DESIGN.md)；[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r5e"></a>
### P4-R5e Evidence-bound shopping intent

- **需求描述**：软件必须交付“Evidence-bound shopping intent”，满足 `S2-INT-001`, `S2-PER-001`, `S2-CTX-002`, `S2-SAF-001`，把购物需求表达为必须确认的候选意图。
- **需求追踪**：`S2-INT-001`, `S2-PER-001`, `S2-CTX-002`, `S2-SAF-001`。
- **负责模块**：Scenario policy node、model action validator 和 Orchestration projection。
- **前置输入**：图片消费证明、座舱观察投影和模型白名单动作。
- **输出与验收**：`resolve_shopping_intent` 只能进入购物同意中断；未确认不得运行 Tool。
- **边界与非目标**：不存在 `AUTO_EXECUTE`；图片不能证明乘员年龄、确定口渴或购买授权。
- **代码对应**：Debug 实现：[scene.cabin.multimodal.assist.v1.json](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/assets/scenarios/scene.cabin.multimodal.assist.v1.json#L30-L70)；[SimulatedScenarioGraph.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedScenarioGraph.java#L1-L40)。
- **当前状态**：`DONE / CONFIRM_REQUIRED`。
- **权威依据**：[购物与路径规划详设](CENTRAL_BRAIN_CABIN_SHOPPING_ROUTE_PLANNING_DESIGN.md)；[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r5f"></a>
### P4-R5f Shopping and route-planning scenario DAG

- **需求描述**：软件必须交付“Shopping and route-planning scenario DAG”，满足 `S2-SCN-001`, `S2-GRF-001`, `S2-INT-001`, `S2-TOL-001`，编排观察、购物意图、确认、搜索、预览、提交和总结。
- **需求追踪**：`S2-SCN-001`, `S2-GRF-001`, `S2-INT-001`, `S2-TOL-001`。
- **负责模块**：Scenario manifest/schema/catalog、compiler、Graph runtime 和 checksum。
- **前置输入**：购物候选、导航能力、Tool allowlist 和确认 policy。
- **输出与验收**：交付 `scene.cabin.multimodal.assist.v1` v2 的 13 节点 DAG、六 Tool、三确认和冻结摘要；目录门禁固定该资产为 v2，并保持其他三个内置资产为 v1。
- **边界与非目标**：兼容场景 ID 不变；无 `effect.execute` 节点；订单和导航确认相互独立。
- **代码对应**：Debug 实现：[scene.cabin.multimodal.assist.v1.json](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/assets/scenarios/scene.cabin.multimodal.assist.v1.json#L1-L40)；[ScenarioManifestParserTest.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/scenario/ScenarioManifestParserTest.java#L1-L40)；[check_central_brain_android_scenario_manifest.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_scenario_manifest.sh#L105-L140)。
- **当前状态**：`DONE / MANIFEST_V2`。
- **权威依据**：[购物与路径规划详设](CENTRAL_BRAIN_CABIN_SHOPPING_ROUTE_PLANNING_DESIGN.md)；[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r5g"></a>
### P4-R5g Independent shopping purchase navigation confirmations

- **需求描述**：软件必须交付“Independent shopping purchase navigation confirmations”，满足 `S2-SAF-001`, `S2-HMI-003/009`, `S2-NAV-001`, `S2-COM-001`，定义购物同意、订单提交和导航启动三个互不兼容的确认。
- **需求追踪**：`S2-SAF-001`, `S2-HMI-003/009`, `S2-NAV-001`, `S2-COM-001`。
- **负责模块**：Simulated scenario composition、Orchestration approval handling 与 Client2 controls。
- **前置输入**：当前 run、pending node、projection digest 和用户确认。
- **输出与验收**：三个不同 `pending_node_id` 依次中断；每个 approval digest 只允许对应 Tool 使用。
- **边界与非目标**：确认不能覆盖 hard interlock；购买确认不能启动导航，导航确认不能提交订单。
- **代码对应**：Debug 实现：[SimulatedScenarioEffectComposition.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedScenarioEffectComposition.java#L1-L40)；[OrchestrationRuntimeClient.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/OrchestrationRuntimeClient.java#L390-L430)。
- **当前状态**：`DONE / THREE_NODE_BOUND_CONFIRMATIONS`。
- **权威依据**：[购物与路径规划详设](CENTRAL_BRAIN_CABIN_SHOPPING_ROUTE_PLANNING_DESIGN.md)；[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r5h"></a>
### P4-R5h Shopping route Tool orchestration

- **需求描述**：软件必须交付“Shopping route Tool orchestration”，满足 `S2-TOL-001`, `S2-NAV-001`, `S2-COM-001`, `S2-SAF-001`，按场景节点执行六个 allowlisted 购物与路线 Tool。
- **需求追踪**：`S2-TOL-001`, `S2-NAV-001`, `S2-COM-001`, `S2-SAF-001`。
- **负责模块**：Scenario Graph、pending Tool state 和 `SimulatedShoppingPlanningService`。
- **前置输入**：Tool node ID、input digest 和节点绑定 approval digest。
- **输出与验收**：六个 Tool 都生成有界 result digest；未知 Tool 或缺确认时失败关闭。
- **边界与非目标**：Tool 不接收原图；debug service 不得进入 production registry 或产生外部副作用。
- **代码对应**：Debug 实现：[SimulatedShoppingPlanningService.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedShoppingPlanningService.java#L1-L40)；[SimulatedScenarioRuntime.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedScenarioRuntime.java#L1-L40)。
- **当前状态**：`DONE / DEBUG_TOOL_SERVICE`。
- **权威依据**：[购物与路径规划详设](CENTRAL_BRAIN_CABIN_SHOPPING_ROUTE_PLANNING_DESIGN.md)；[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r5i"></a>
### P4-R5i Route-planning search preview start

- **需求描述**：软件必须交付“Route-planning search preview start”，满足 `S2-NAV-001`, `S2-SAF-001`, `S2-TOL-001`, `S2-OBS-002`，拆分商户 POI、路线预览和导航启动。
- **需求追踪**：`S2-NAV-001`, `S2-SAF-001`, `S2-TOL-001`, `S2-OBS-002`。
- **负责模块**：POI Tool、route preview Tool、navigation start Tool 和 HMI route projection。
- **前置输入**：商品类别、商户候选、路线约束、route digest 和导航确认。
- **输出与验收**：三个 synthetic 商户候选、2.4 km/4 min 预览及 `NAVIGATION_SIMULATED`。
- **边界与非目标**：debug 结果必须标记 synthetic；production 不得声称真实地图或导航已启动。
- **代码对应**：Debug 实现：[SimulatedShoppingPlanningService.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedShoppingPlanningService.java#L15-L45)；[CockpitControlCoordinator.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java#L1160-L1200)。
- **当前状态**：`DONE / NAVIGATION_SIMULATED`。
- **权威依据**：[购物与路径规划详设](CENTRAL_BRAIN_CABIN_SHOPPING_ROUTE_PLANNING_DESIGN.md)；[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r5j"></a>
### P4-R5j Shopping search prepare commit

- **需求描述**：软件必须交付“Shopping search prepare commit”，满足 `S2-COM-001`, `S2-SAF-001`, `S2-TOL-001`, `S2-OBS-001`，将商品搜索、商户搜索、订单预览和提交建模为独立 Tool。
- **需求追踪**：`S2-COM-001`, `S2-SAF-001`, `S2-TOL-001`, `S2-OBS-001`。
- **负责模块**：debug product/merchant/order service、purchase approval 和 Client2 projection。
- **前置输入**：商品类别、候选商户、订单预览 digest 和购买确认。
- **输出与验收**：三项商品、三项商户和订单预览；commit 返回 `ORDER_NOT_DISPATCHED`。
- **边界与非目标**：Commerce 不进入 Vehicle Capability；本阶段不实现真实支付、凭据存储或自动下单。
- **代码对应**：Debug 实现：[SimulatedShoppingPlanningService.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedShoppingPlanningService.java#L15-L45)；[SimulatedShoppingPlanningServiceTest.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/testDebug/java/com/centralbrain/runtime/scenario/SimulatedShoppingPlanningServiceTest.java#L1-L40)。
- **当前状态**：`DONE / ORDER_NOT_DISPATCHED`。
- **权威依据**：[购物与路径规划详设](CENTRAL_BRAIN_CABIN_SHOPPING_ROUTE_PLANNING_DESIGN.md)；[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r5k"></a>
### P4-R5k External dispatch fail-closed boundary

- **需求描述**：软件必须交付“External dispatch fail-closed boundary”，满足 `S2-CTX-002`, `S2-EFF-001`, `S2-ADP-001/002`, `S2-SAF-001`，确保购物、支付、地图和车辆接口缺失时不外发。
- **需求追踪**：`S2-CTX-002`, `S2-EFF-001`, `S2-ADP-001/002`, `S2-SAF-001`。
- **负责模块**：Shopping Tool result authority flags、scenario composition 和 HMI boundary labels。
- **前置输入**：Tool result、confirmation digest 和 build variant。
- **输出与验收**：`externalDispatchPerformed=false`、`paymentMaterialAccessed=false`、`vehicleHardwareAccessed=false`。
- **边界与非目标**：UI 仿真不得冒充商户订单、真实导航或车辆 readback；production 不回退 debug service。
- **代码对应**：Debug 实现：[SimulatedShoppingPlanningService.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedShoppingPlanningService.java#L45-L85)；[SimulatedScenarioEffectComposition.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedScenarioEffectComposition.java#L300-L340)。
- **当前状态**：`DONE / PRODUCTION_ADAPTERS_EMPTY`。
- **权威依据**：[购物与路径规划详设](CENTRAL_BRAIN_CABIN_SHOPPING_ROUTE_PLANNING_DESIGN.md)；[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r5l"></a>
### P4-R5l Client2 event-driven shopping route HMI

- **需求描述**：软件必须交付“Client2 event-driven shopping route HMI”，满足 `S2-HMI-003/007/008/009`, `S2-OBS-002`, `S2-UX-002/003`，实时显示模型输入/输出、购物意图、确认、商品、订单和路线。
- **需求追踪**：`S2-HMI-003/007/008/009`, `S2-OBS-002`, `S2-UX-002/003`。
- **负责模块**：Client2 immutable state/reducer/coordinator/XML、Orchestration callback 和 live trace projection。
- **前置输入**：Runtime snapshot、模型 projection、三个 pending node、Tool 状态和最终 lifecycle。
- **输出与验收**：交付极简按钮、动态确认条、逐条滚动链、商品/商户/订单/路线反馈；在 testboard 的 WSL 路径和生产板目标以太路径分别通过 1920x1080 验收，并由互斥环境声明区分两类证据。
- **边界与非目标**：不得使用本地 UI 定时器伪造模型/Tool 完成或一次跳到最终结果；HMI 不持有 authority。
- **代码对应**：Debug 实现：[CockpitControlCoordinator.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java#L1120-L1160)；[main_layout.central_brain_panel.xml](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/patches/main_layout.central_brain_panel.xml#L55-L85)；[run_client2_central_brain_openclaw_development_test.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/run_client2_central_brain_openclaw_development_test.sh#L237-L266)。
- **当前状态**：`DONE / DUAL_ROUTE_ARM64_VERIFIED`。
- **权威依据**：[购物与路径规划详设](CENTRAL_BRAIN_CABIN_SHOPPING_ROUTE_PLANNING_DESIGN.md)；[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r6"></a>
### P4-R6 Unity 原生 HVAC 与座椅展开修复

- **需求描述**：Cold 场景必须更新 RenderService/Unity 原生驾驶席和乘员席温度，禁止使用 Android TextView 覆盖 Unity；Fatigue 座椅靠背必须从 15 度向 30 度展开。
- **需求追踪**：`S2-HMI-001/002/003/004`, `S2-UX-002`, `S2-ADP-001/002`, `S2-SAF-001`, `DEL-004`。
- **负责模块**：Client2 `CockpitControlCoordinator`、TuanjieView 跨进程触控、RenderService Unity Addressables patch。
- **前置输入**：已准入的 debug HVAC/Seat Effect、1920x1080 Tuanjie render surface、成对安装的 Client2 和 RenderService debug APK。
- **输出与验收**：Cold 真实模型终态后 Unity 原生双区由 26.5°C 切换为 28.0°C；Fatigue 靠背顶端远离坐垫并显示 30 度；Android 温度 overlay 不存在；testboard 与生产板应用层复测通过。
- **边界与非目标**：只形成 HMI 仿真状态；不访问 Vehicle/VHAL/CAN/Driver-HAL，不修改 `libtuanjie.so`、厂商 Android 系统镜像或真实 HVAC/Seat target/readback。
- **代码对应**：Client2/Unity 实现：[CockpitControlCoordinator.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java#L1263-L1277)；[CockpitControlCoordinator.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java#L1279-L1307)；[patch_unity_hvac_bundle.py](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/renderservice-central-brain/scripts/patch_unity_hvac_bundle.py#L173-L205)；[patch_unity_hvac_bundle.py](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/renderservice-central-brain/scripts/patch_unity_hvac_bundle.py#L263-L281)；[check_central_brain_unity_native_hvac_seat.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_unity_native_hvac_seat.sh#L20-L50)。
- **当前状态**：`DONE / TESTBOARD_ARM64_VERIFIED / PRODUCTION_BOARD_ARM64_VERIFIED`。
- **权威依据**：[专项详设](CENTRAL_BRAIN_CLIENT2_UNITY_NATIVE_HVAC_SEAT_PATCH.md)；[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p4-r7"></a>
### P4-R7 渲染清晰度、动态温区与车模旋转

- **需求描述**：1920x1080 黑盒 Android 13 座舱必须请求更高 Unity 内部渲染尺度；双区温度必须使用 Unity 原生动态文本并支持 18.0-30.0°C、0.5°C 步进和可见逐级动画；车模必须恢复滑动旋转且不破坏车门点击。
- **需求追踪**：`APP-004`, `S2-HMI-001..004`, `S2-UX-002/003`, `DEL-004`。
- **负责模块**：Client2 `CockpitControlCoordinator`、`TuanjieView` RenderService Binder、RenderService Unity Addressables patch、配对 APK 构建/门禁。
- **前置输入**：1920x1080 `TuanjieView`、RenderService `DisplayIndex=1`、成对安装且同签的 Client2/RenderService debug APK。
- **输出与验收**：请求 1.5 render scale；双区默认 26.5°C、18.0/30.0°C 边界、0.5°C 手动/场景共享状态机；Cold 逐级显示 26.5→27.0→27.5→28.0；滑动改变车模朝向且车门点击有效；无 crash/ANR。
- **边界与非目标**：render scale 是应用层请求，不等于 GPU/Unity 图形质量标定；动态温区是 HMI 仿真，不访问 Vehicle/VHAL/CAN/Driver-HAL，不修改系统镜像或 `libtuanjie.so`。
- **代码对应**：[CockpitControlCoordinator.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java#L76-L84)；[CockpitControlCoordinator.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java#L1256-L1532)；[patch_unity_hvac_bundle.py](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/renderservice-central-brain/scripts/patch_unity_hvac_bundle.py#L33-L50)；[check_central_brain_unity_native_hvac_seat.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_unity_native_hvac_seat.sh#L1-L65)。
- **当前状态**：`REPOSITORY_BUILT / TESTBOARD_RETEST_BLOCKED_ADB_OFFLINE`。
- **权威依据**：[P4-R7 详设](CENTRAL_BRAIN_CLIENT2_RENDER_FIDELITY_HVAC_ORBIT.md)；[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

## P5 Tool、Skill 与 Memory

<a id="p5-w01"></a>
### P5-W01 P5 Tool Manifest/Schema

- **需求描述**：软件必须交付“P5 Tool Manifest/Schema”，满足 `S2-TOL-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-TOL-001`。
- **负责模块**：runtime-service 的 Tool、Skill、Memory 与 Context Budget。
- **前置输入**：版本化 Tool/Skill manifest、健康/签名证据、Session owner、Memory consent 与上下文预算。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：versioned manifest、bounded input/output schema。
- **边界与非目标**：process-local/contract-test 能力不得提升为 production Tool、Skill 或 Memory authority。
- **代码对应**：软件实现：[ToolManifest.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/tools/ToolManifest.java#L17-L35)；[check_central_brain_android_tool_manifest.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_tool_manifest.sh#L4-L22)。
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
- **代码对应**：软件实现：[ToolRegistry.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/tools/ToolRegistry.java#L16-L34)；[check_central_brain_android_tool_registry.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_tool_registry.sh#L4-L22)。
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
- **代码对应**：软件实现：[ToolRuleSolver.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/tools/ToolRuleSolver.java#L13-L31)；[check_central_brain_android_tool_rule_solver.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_tool_rule_solver.sh#L4-L22)。
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
- **代码对应**：软件实现：[InProcessBuiltInToolExecutor.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/tools/InProcessBuiltInToolExecutor.java#L15-L33)；[check_central_brain_android_tool_executor.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_tool_executor.sh#L4-L22)。
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
- **代码对应**：软件实现：[SkillArtifactVerifier.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/skills/SkillArtifactVerifier.java#L14-L32)；[check_central_brain_android_skill_package_verifier.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_skill_package_verifier.sh#L4-L22)。
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
- **代码对应**：软件实现：[WorkingMemoryStore.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/memory/WorkingMemoryStore.java#L17-L35)；[check_central_brain_android_working_memory_store.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_working_memory_store.sh#L4-L22)。
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
- **代码对应**：软件实现：[ProfileMemoryStore.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/memory/ProfileMemoryStore.java#L22-L40)；[check_central_brain_android_profile_memory_store.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_profile_memory_store.sh#L4-L22)。
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
- **代码对应**：软件实现：[EpisodicMemoryStore.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/memory/EpisodicMemoryStore.java#L16-L34)；[check_central_brain_android_episodic_memory_store.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_episodic_memory_store.sh#L4-L22)。
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
- **代码对应**：软件实现：[ContextBudgetManager.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/memory/ContextBudgetManager.java#L15-L33)；[check_central_brain_android_context_budget_manager.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_context_budget_manager.sh#L4-L22)。
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
- **代码对应**：软件实现：[MemoryConsentController.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/memory/MemoryConsentController.java#L17-L35)；[check_central_brain_android_memory_consent_hmi.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_memory_consent_hmi.sh#L4-L22)。
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
- **代码对应**：Debug 实现：[DebugRuntimeCompositionBoundary.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/orchestration/DebugRuntimeCompositionBoundary.java#L30-L48)；[check_central_brain_android_runtime_composition_v1.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_runtime_composition_v1.sh#L4-L22)。
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
- **代码对应**：软件实现：[InProcessDurableEventBroker.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/InProcessDurableEventBroker.java#L21-L39)；[check_central_brain_android_event_broker.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_event_broker.sh#L4-L22)。
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
- **代码对应**：软件实现：[InProcessEventBackpressureQueue.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/InProcessEventBackpressureQueue.java#L12-L30)；[check_central_brain_android_event_backpressure_qos.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_event_backpressure_qos.sh#L4-L22)。
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
- **代码对应**：软件实现：[TriggerEngine.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/TriggerEngine.java#L16-L34)；[check_central_brain_android_trigger_engine.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_trigger_engine.sh#L4-L22)。
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
- **代码对应**：软件实现：[ProactiveConsentPolicy.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/ProactiveConsentPolicy.java#L17-L35)；[check_central_brain_android_proactive_consent_policy.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_proactive_consent_policy.sh#L4-L22)。
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
- **代码对应**：软件实现：[RuntimeHealthContextSourceAdapter.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/RuntimeHealthContextSourceAdapter.java#L8-L26)；[check_central_brain_android_context_source_adapters.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_context_source_adapters.sh#L4-L22)。
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
- **代码对应**：软件实现：[ActiveSuggestionController.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/suggestion/ActiveSuggestionController.java#L21-L39)；[check_central_brain_android_active_suggestion_ux.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_active_suggestion_ux.sh#L4-L22)。
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
- **代码对应**：Debug 实现：[DebugDecisionCompositionBoundary.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/orchestration/DebugDecisionCompositionBoundary.java#L58-L76)；[check_central_brain_android_decision_composition_v1.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_decision_composition_v1.sh#L4-L22)。
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
- **代码对应**：软件实现：[ModelContractV2.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelContractV2.java#L13-L31)；[check_central_brain_android_model_contract_v2.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_model_contract_v2.sh#L4-L22)。
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
- **代码对应**：软件实现：[ModelProviderRegistry.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProviderRegistry.java#L21-L39)；[check_central_brain_android_model_provider_registry.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_model_provider_registry.sh#L4-L22)。
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
- **代码对应**：软件实现：[PolicyAwareModelRouter.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/PolicyAwareModelRouter.java#L21-L39)；[check_central_brain_android_policy_aware_model_router.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_policy_aware_model_router.sh#L4-L22)。
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
- **代码对应**：软件实现：[LocalModelProvider.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/LocalModelProvider.java#L15-L33)；[check_central_brain_android_local_model_provider.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_local_model_provider.sh#L4-L22)。
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
- **代码对应**：软件实现：[StructuredModelOutput.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/StructuredModelOutput.java#L31-L49)；[check_central_brain_android_structured_model_output.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_structured_model_output.sh#L4-L22)。
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
- **代码对应**：软件实现：[ScenarioEvaluationHarness.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ScenarioEvaluationHarness.java#L26-L44)；[check_central_brain_android_scenario_evaluation.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_scenario_evaluation.sh#L4-L22)。
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
- **代码对应**：软件实现：[ModelResourceAdmission.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/scheduler/ModelResourceAdmission.java#L16-L34)；[check_central_brain_android_model_resource_admission.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_model_resource_admission.sh#L4-L22)。
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
- **代码对应**：Debug 实现：[OllamaInferenceEngine.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/OllamaInferenceEngine.java#L29-L47)；[check_central_brain_android_ollama_gateway.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_ollama_gateway.sh#L4-L22)。
- **当前状态**：`DONE / ANDROID13_ARM64_DEBUG_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[Ollama 网关设计](CENTRAL_BRAIN_OLLAMA_MODEL_GATEWAY.md)。

<a id="p7-r3-oc2"></a>
### P7-R3-OC2 OpenClaw target transitional gateway

- **需求描述**：软件必须交付“OpenClaw target transitional gateway”，满足 `S2-MDL-001/002`, `S2-SAF-001`, `S2-OBS-001/002`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MDL-001/002`, `S2-SAF-001`, `S2-OBS-001/002`。
- **负责模块**：runtime-service 的 Model Provider/Registry/Router 与模型网关。
- **前置输入**：Android 13 车机经目标以太网提交座舱文字及可选的单张有界 PNG/JPEG，文字与图片必须进入同一个已鉴权 `chat.send`。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：fixed WebSocket v3、challenge/auth/send/history/abort、文字+图片附件、Client2 projection；目标板以太多模态、三个确认及 Graph revision 77 已通过。
- **边界与非目标**：受控帧前端和目标以太多模态已完成；实时相机、量产媒体治理、持久 IPv4 和 Provider 量产资格仍未完成，模型输出不能授权 Effect。
- **代码对应**：过渡实现：[OpenClawInferenceEngine.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/OpenClawInferenceEngine.java#L38-L56)；[central_brain_android_openclaw_target_gateway_v1.json](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/contracts/central_brain_android_openclaw_target_gateway_v1.json#L42-L58)；[check_central_brain_android_openclaw_target_gateway.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_openclaw_target_gateway.sh#L4-L22)。
- **当前状态**：`TRANSITIONAL / TARGET_MULTIMODAL_VERIFIED / PRODUCTION_QUALIFICATION_OPEN`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[OpenClaw 网关设计](CENTRAL_BRAIN_OPENCLAW_TARGET_GATEWAY.md)；[车机直连接口详解](CENTRAL_BRAIN_OPENCLAW_INTERFACE_CODE_GUIDE.md)。

<a id="p7-r4-ocdev"></a>
### P7-R4-OCDEV WSL OpenClaw via real Android ADB

- **需求描述**：软件必须交付“WSL OpenClaw via real Android ADB”，满足 `S2-MDL-001/002`, `S2-SAF-001`, `S2-OBS-001/002`, `XSC-001/005/006`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-MDL-001/002`, `S2-SAF-001`, `S2-OBS-001/002`, `XSC-001/005/006`。
- **负责模块**：runtime-service 的 Model Provider/Registry/Router 与模型网关。
- **前置输入**：真实 Android 13 ARM64、ADB reverse、WSL OpenClaw v4、Ollama `qwen3.6:27b`、结构化座舱 prompt 与 action allowlist。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：Android13 ARM64 -> ADB reverse -> OpenClaw v4 -> Ollama 真实终态；Client2/SDK/Runtime 同源构建，模型终态、编排投影、实时调用链、UI 仿真 Effect 和脱敏证据完整。
- **边界与非目标**：ADB reverse 只证明开发模型链路，不是以太网、目标 NPU、车辆 Effect、Driver/HAL 或量产资格证据。
- **代码对应**：Debug 实现：[OpenClawInferenceEngine.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/OpenClawInferenceEngine.java#L549-L583)；[run_client2_central_brain_openclaw_development_test.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/run_client2_central_brain_openclaw_development_test.sh#L65-L105)；[check_central_brain_android_openclaw_development_gateway.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_openclaw_development_gateway.sh#L4-L22)。
- **当前状态**：`DONE / ANDROID13_ARM64_CLIENT2_OPENCLAW_VERIFIED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[开发 OpenClaw 网关](CENTRAL_BRAIN_OPENCLAW_DEVELOPMENT_GATEWAY.md)。

<a id="p7-r5-mmdev"></a>
### P7-R5-MMDEV OpenClaw multimodal development channel

- **需求描述**：Client2“处理一下”必须把文字与当前受控座舱帧作为同一多模态输入，经 SDK/Binder、Runtime、OpenClaw/Ollama 到白名单执行闭环。
- **需求追踪**：`S2-MDL-001/002`, `S2-OBS-001/002`, `S2-SAF-001`, `XSC-001/005/006`, `DEL-001/003/004/005`。
- **负责模块**：Client2 HMI、debug SDK/Binder、runtime-service 编排、OpenClaw 附件协议。
- **前置输入**：文字“处理一下”、digest-bound 单张 PNG、OpenClaw v4 与 `qwen3.6:27b` vision。
- **输出与验收**：Android 13 ARM64 必须显示输入缩略图、实际回复、获准动作与 UI 仿真 Effect；独立探针继续验证图片可见事实。
- **边界与非目标**：固定受控帧不是实时相机；目标以太网、NPU、车辆 Effect 和量产媒体治理仍开放。
- **代码对应**：Debug 实现：[CockpitMultimodalInput.java](https://github.com/LucasWEIchen/CougarOS/blob/main/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitMultimodalInput.java#L19-L45)；[DevelopmentModelProjectionService.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/DevelopmentModelProjectionService.java#L56-L80)；[OpenClawInferenceEngine.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/OpenClawInferenceEngine.java#L208-L229)；[run_client2_central_brain_openclaw_development_test.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/run_client2_central_brain_openclaw_development_test.sh#L155-L179)。
- **当前状态**：`CONTROLLED_FRAME_BOUND / ARM64_VERIFIED / LIVE_CAMERA_OPEN`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)；[多模态开发通道](CENTRAL_BRAIN_OPENCLAW_MULTIMODAL_DEVELOPMENT.md)。

## P9 软件接口与调试证据

<a id="p9-w01"></a>
### P9-W01 P9 Performance Budget Contract

- **需求描述**：软件必须交付“P9 Performance Budget Contract”，满足 `S2-OBS-001`，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：`S2-OBS-001`。
- **负责模块**：质量、隐私、安全、发布与诊断合同/探针。
- **前置输入**：版本化测试/发布合同、脱敏元数据、目标证据模式和责任人批准输入。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：7 categories/10 metrics、strict report、synthetic probe。
- **边界与非目标**：软件合同和 debug probe 不等于目标性能、72h、安全、隐私、签名、驾驶安全或发布资格。
- **代码对应**：合同实现：[PerformanceBudgetContract.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/performance/PerformanceBudgetContract.java#L21-L39)；[check_central_brain_android_performance_budget.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_performance_budget.sh#L4-L22)。
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
- **代码对应**：合同实现：[StabilityFaultMatrixContract.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/reliability/StabilityFaultMatrixContract.java#L21-L39)；[check_central_brain_android_stability_fault_matrix.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_stability_fault_matrix.sh#L4-L22)。
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
- **代码对应**：合同实现：[ParserSecurityCorpusContract.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/security/ParserSecurityCorpusContract.java#L13-L31)；[check_central_brain_android_parser_security_corpus.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_parser_security_corpus.sh#L4-L22)。
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
- **代码对应**：合同实现：[IdentityReplaySecurityCorpusContract.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/security/IdentityReplaySecurityCorpusContract.java#L13-L31)；[check_central_brain_android_identity_replay_security_corpus.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_identity_replay_security_corpus.sh#L4-L22)。
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
- **代码对应**：合同实现：[SecurityBoundaryInventoryContract.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/security/SecurityBoundaryInventoryContract.java#L12-L30)；[check_central_brain_android_security_boundary_inventory.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_security_boundary_inventory.sh#L4-L22)。
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
- **代码对应**：合同实现：[SecurityIdentityProbeService.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/security/SecurityIdentityProbeService.java#L12-L30)；[check_central_brain_android_security_identity.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_security_identity.sh#L4-L22)。
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
- **代码对应**：合同实现：[TaskCallbackReplayGuard.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/TaskCallbackReplayGuard.java#L9-L27)；[check_central_brain_android_callback_replay_security.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_callback_replay_security.sh#L4-L22)。
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
- **代码对应**：无执行代码；撤回门禁：[check_central_brain_android_security_evidence_interface.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_security_evidence_interface.sh#L4-L22)。
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
- **代码对应**：非执行接口：[central_brain_android_p9_security_evidence_interface.json](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/contracts/central_brain_android_p9_security_evidence_interface.json#L2-L20)；[check_central_brain_android_security_evidence_interface.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_security_evidence_interface.sh#L4-L22)。
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
- **代码对应**：合同实现：[PrivacyDataInventoryContract.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/privacy/PrivacyDataInventoryContract.java#L13-L31)；[check_central_brain_android_privacy_data_inventory.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_privacy_data_inventory.sh#L4-L22)。
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
- **代码对应**：合同实现：[PrivacyLifecyclePolicyAdmission.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/privacy/PrivacyLifecyclePolicyAdmission.java#L13-L31)；[check_central_brain_android_privacy_policy_admission.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_privacy_policy_admission.sh#L4-L22)。
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
- **代码对应**：合同实现：[PrivacyRedactionAuditProjection.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/privacy/PrivacyRedactionAuditProjection.java#L8-L26)；[check_central_brain_android_privacy_redaction_audit.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_privacy_redaction_audit.sh#L4-L22)。
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
- **代码对应**：合同实现：[ProductionReleaseAdmission.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/release/ProductionReleaseAdmission.java#L14-L32)；[check_central_brain_android_production_release_admission.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_production_release_admission.sh#L4-L22)。
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
- **代码对应**：合同实现：[ProductionReleaseMetadataProjection.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/release/ProductionReleaseMetadataProjection.java#L8-L26)；[check_central_brain_android_production_release_metadata_probe.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_production_release_metadata_probe.sh#L4-L22)。
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
- **代码对应**：合同实现：[DriverSafetyAdmissionContract.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/governance/DriverSafetyAdmissionContract.java#L20-L38)；[check_central_brain_android_driver_safety_admission.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_driver_safety_admission.sh#L4-L22)。
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
- **代码对应**：合同实现：[DriverSafetyAuditProjection.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/governance/DriverSafetyAuditProjection.java#L10-L28)；[check_central_brain_android_driver_safety_probe.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_driver_safety_probe.sh#L4-L22)。
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
- **代码对应**：合同实现：[ReleaseEvidenceEnvelope.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/release/ReleaseEvidenceEnvelope.java#L12-L30)；[check_central_brain_android_release_evidence_envelope.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_release_evidence_envelope.sh#L4-L22)。
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
- **代码对应**：合同实现：[FieldDiagnosticsProjection.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/release/FieldDiagnosticsProjection.java#L8-L26)；[check_central_brain_android_field_diagnostics_probe.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_field_diagnostics_probe.sh#L4-L22)。
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
- **代码对应**：合同实现：[ReleaseRetestWorkflow.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/release/ReleaseRetestWorkflow.java#L13-L31)；[check_central_brain_android_release_retest_workflow.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_release_retest_workflow.sh#L4-L22)。
- **当前状态**：`DONE / RETEST_OPEN`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[Stage 2 backlog](CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

<a id="p10-r1"></a>
### P10-R1 P10-R1 Android repository software completion

- **需求描述**：软件必须交付“P10-R1 Android repository software completion”，满足 全部已分类 Req IDs，并以有界、版本化、可审计且失败关闭的方式提供所列能力。
- **需求追踪**：全部已分类 Req IDs。
- **负责模块**：仓库级需求治理与软件完成度门禁。
- **前置输入**：全部分类后的 Req ID、工作包状态、专项合同和聚合门禁结果。
- **输出与验收**：必须能够由专项合同、测试或设备证据复现：P4-R5 debug 软件完成后 `repository_software_requirements_complete=true`、`open_repository_software_requirement_count=0`、`unclassified_repository_requirement_count=0`。
- **边界与非目标**：仓库软件完成不等于量产激活；OEM Vehicle、Vendor NPU、目标签名/SELinux、目标以太网与资格证据仍为外部阻塞。
- **代码对应**：聚合门禁：[central_brain_android_software_completion_v1.json](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/contracts/central_brain_android_software_completion_v1.json#L2-L20)；[check_central_brain_android_software_completion.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_software_completion.sh#L4-L22)。
- **当前状态**：`DONE / EXTERNAL_PRODUCTION_BLOCKED`。
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
- **代码对应**：空接口/准入：[EmptyEffectMaterialSource.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EmptyEffectMaterialSource.java#L6-L24)；[check_central_brain_android_effect_activation_gate.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_effect_activation_gate.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[AdapterRegistry.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/AdapterRegistry.java#L16-L34)；[check_central_brain_android_effect_gate_wiring.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_effect_gate_wiring.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[SkillGovernanceReadinessSnapshot.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/governance/SkillGovernanceReadinessSnapshot.java#L12-L30)；[check_central_brain_android_skill_governance_readiness.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_skill_governance_readiness.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[EventRuntimeReadinessSnapshot.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/EventRuntimeReadinessSnapshot.java#L10-L28)；[check_central_brain_android_event_runtime_readiness.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_event_runtime_readiness.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[ModelRuntimeReadinessSnapshot.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelRuntimeReadinessSnapshot.java#L9-L27)；[check_central_brain_android_model_runtime_readiness.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_model_runtime_readiness.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[central_brain_android_p8_target_capability_discovery.json](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/contracts/central_brain_android_p8_target_capability_discovery.json#L2-L20)；[check_central_brain_android_target_capability_discovery.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_target_capability_discovery.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[AdapterRegistry.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/AdapterRegistry.java#L16-L34)；[check_central_brain_android_target_capability_discovery.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_target_capability_discovery.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[AdapterRegistry.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/AdapterRegistry.java#L16-L34)；[check_central_brain_android_effect_gate_wiring.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_effect_gate_wiring.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[EffectAdapter.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectAdapter.java#L9-L27)；[check_central_brain_android_effect_gate_wiring.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_effect_gate_wiring.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[ModelProviderProfiles.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProviderProfiles.java#L6-L24)；[central_brain_native.h](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/native-runtime/src/main/cpp/include/central_brain_native.h#L1-L19)；[check_central_brain_npu_interface.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_npu_interface.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[RuntimeAcceptanceSnapshot.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/acceptance/RuntimeAcceptanceSnapshot.java#L18-L36)；[check_central_brain_android_target_deployment.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_target_deployment.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[PerformanceBudgetContract.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/performance/PerformanceBudgetContract.java#L21-L39)；[check_central_brain_android_performance_budget.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_performance_budget.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[StabilityFaultMatrixContract.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/reliability/StabilityFaultMatrixContract.java#L21-L39)；[check_central_brain_android_stability_fault_matrix.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_stability_fault_matrix.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[central_brain_android_p9_security_evidence_interface.json](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/contracts/central_brain_android_p9_security_evidence_interface.json#L2-L20)；[check_central_brain_android_security_evidence_interface.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_security_evidence_interface.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[PrivacyLifecyclePolicyAdmission.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/privacy/PrivacyLifecyclePolicyAdmission.java#L13-L31)；[check_central_brain_android_privacy_policy_admission.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_privacy_policy_admission.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[ProductionReleaseAdmission.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/release/ProductionReleaseAdmission.java#L14-L32)；[check_central_brain_android_production_release_admission.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_production_release_admission.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[DriverSafetyAdmissionContract.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/governance/DriverSafetyAdmissionContract.java#L20-L38)；[check_central_brain_android_driver_safety_admission.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_driver_safety_admission.sh#L4-L22)。
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
- **代码对应**：空接口/准入：[ReleaseRetestWorkflow.java](https://github.com/LucasWEIchen/CougarOS/blob/main/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/release/ReleaseRetestWorkflow.java#L13-L31)；[check_central_brain_android_release_retest_workflow.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_release_retest_workflow.sh#L4-L22)。
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
- **代码对应**：无实现；范围门禁：[check_central_brain_python_prototype_retirement.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_python_prototype_retirement.sh#L4-L22)。
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
- **代码对应**：无实现；范围门禁：[check_central_brain_python_prototype_retirement.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_python_prototype_retirement.sh#L4-L22)。
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
- **代码对应**：无实现；范围门禁：[check_central_brain_virtualization_docs.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_virtualization_docs.sh#L1-L19)。
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
- **代码对应**：无实现；范围门禁：[check_central_brain_npu_interface.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_npu_interface.sh#L4-L22)。
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
- **代码对应**：无实现；范围门禁：[check_central_brain_android_aidl_contract.sh](https://github.com/LucasWEIchen/CougarOS/blob/main/tools/check_central_brain_android_aidl_contract.sh#L1-L19)。
- **当前状态**：`SUSPENDED`。
- **权威依据**：[架构需求基线](CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md)；[路线图](CENTRAL_BRAIN_ROADMAP.md)；[偏差登记](CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md)；[风险台账](CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md)。

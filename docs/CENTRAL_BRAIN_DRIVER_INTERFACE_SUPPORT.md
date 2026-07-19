# 驱动层接口支持矩阵

版本：2.7

日期：2026-07-17

## P4-R1 Orchestration Driver/HAL Boundary

`P4-R1` 只使用 Java/AIDL Binder、Room/SQLite、PackageManager-derived caller identity 和现有 debug simulator，
不访问 Android Car、VHAL、CAN、Vendor SOA、device node、sysfs、ioctl、PCIe/NPU 或新 JNI。故本增量
`driver_development_triggered=false`、`virtualization_development_triggered=false`。release backend 明确留空并
fail closed；未来生产 backend 必须由 P8 target mapping 提供 property/service ID、area、type、unit、freshness、
write permission、readback、timeout、owner 和 version 后才可接入。

当前 `orchestration_runtime_service_published=true`、`vehicle_signal_provider_wired=false`、
`vehicle_capability_adapter_registry_wired=false`、`production_effect_authority_available=false`、
`driver_hal_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。Req IDs：`S2-EFF-001`、`S2-SAF-001`、`XSC-004/006`、
`NV-F-001`、`NV-G-005..007`、`DEL-001/003/004`；里程碑 `P4-R1`。

## P6-EV2 Session Event Driver/HAL Boundary

`P6-EV2` 仅使用 Android Binder、Room/SQLite、PackageManager-derived owner 和 Java SHA-256，不读取
Android Car、VehicleProperty、Vendor Binder/SOA、CAN、device node、sysfs、ioctl、PCIe/NPU、JNI 或网络。
因此本增量不新增 C/C++、Driver/HAL 或虚拟化代码：`driver_development_triggered=false`、
`virtualization_development_triggered=false`。跨 SOC Event transport 仍需 P8 提供 endpoint/protocol/
ownership/version/QoS evidence 后另立最小接口包，不能从 Session Event V2 推断厂商驱动。

当前 `event_v2_interface_published=true`、`event_v2_room_ack_wired=true`、
`event_v2_android13_arm64_verified=false`、`driver_hal_accessed=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`。Req IDs：`S2-EVT-001`、`FW-U-003`、
`NV-G-004/006/007`、`XSC-001/005/006`、`DEL-001/003/004`；里程碑 `P6-EV2`。

## 范围声明

当前项目只开发黑盒 Android 13 用户态中央大脑。驱动层不是默认开发范围；只有公开 Android/NDK、
OEM/Vendor service 或已发布 SDK 无法满足接口，且 owner、ABI、最小缺口和验收条件明确时，才新增
独立 Driver/HAL 工作包。

Req ID：`XSC-004`、`XSC-006`、`NV-F-001`、`NV-F-003..005`、`NV-F-011`、
`NV-P-002`、`HW-002`、`KH-001..009`、`HV-001..003`、`DEL-001`、`DEL-003..005`。

Python hardware registry 和 no-store REST checklist 已退役。当前权威边界由 Android empty provider、
activation gate、C ABI、本文和 `CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md` 共同定义。

## 2026-07-15 AIOS Stage 2 Driver/HAL 边界

Stage 2 P1-P7 只开发 Android Java/AIDL/C 用户态软件和 debug/test-only Digital Twin。Session、
Context、Plan、Effect、Event、Memory、Skill、HMI 与 fault/recovery 工作包均不得触发 Driver/HAL。
P8 只有取得目标车辆/NPU owner、API/ABI、权限、Safety、smoke 和 rollback 证据后，才可按
capability 评审 `DRV-GAP-001..005`；production adapter 缺失时返回 unavailable，不回退仿真。

`driver_development_triggered=false`、`virtualization_development_triggered=false`。

### P1-W01 Session Contract Driver/HAL Boundary

P1-W01 只在 Android SDK AAR 中新增 app-layer AIDL DTO/interface、Java 边界校验和 Parcel/checksum
测试。合同不包含 device node、fd/shared memory、vehicle property、CAN/DBC、Vendor NPU handle、
DMA/IOMMU 或 ioctl；Android instrumentation 仅做 Parcel round-trip，报告 `hardware_accessed=false`。

Android 13/API 33 ARM64 物理控制器已通过该 Parcel instrumentation，临时 test APK 随后卸载；该证据
只设置 `session_parcel_physical_android13_arm64_verified=true`，不触发任何 Driver/HAL 结论。

状态：`session_contract_v1_defined=true`、`session_runtime_service_published=false`、
`driver_development_triggered=false`。没有发现需要新增 Driver/HAL 的明确 gap，`DRV-GAP-001..005`
均不因本工作包改变。

### P1-W02 Plan Contract Driver/HAL Boundary

P1-W02 只在 Android SDK AAR 中新增 app-layer Plan/Node structured parcelable、纯 Java DAG/容量/
重试/补偿校验和 Parcel/checksum 测试。合同只传递 canonical ID、digest 和有界执行 metadata，不包含
device node、fd/shared memory、vehicle property、CAN/DBC、Vendor NPU handle、DMA/IOMMU 或 ioctl。

Android 13/API 33 ARM64 物理控制器仅执行四个 DTO Parcel round-trip、cycle/unknown-type reject，
报告 `hardware_accessed=false` 并卸载临时 test APK。状态：`plan_contract_v1_defined=true`、
`plan_parcel_physical_android13_arm64_verified=true`、`plan_runtime_published=false`、
`driver_development_triggered=false`。

本工作包没有发现新的公开 Android/Vendor SDK 能力缺口，不触发 Driver/HAL 开发；
`DRV-GAP-001..005` 保持原状态。真实 effect dispatch、车辆 readback、NPU 和 Safety authority 仍受 P8
及既有 gap gate 约束。

## 2026-07-16 Android 实机边界

- Runtime、Demo、Client2 的物理 Android 13 应用层验收不等于 NPU/VHAL/车辆总线验收。
- 普通 APK 不打开未知 device node、不猜 ioctl/sysfs、不修改 kernel/HAL/VHAL/SELinux。
- `vendor.npu.empty`、Vehicle/Effect empty gate 在缺少公开合同和证据时保持 unavailable。
- Python/Ollama 仿真路径已删除，不再作为 Driver/HAL 缺失时的 fallback。
- Android deterministic provider 只用于 unit/debug 合同测试，不关闭任何硬件 gap。
- `driver_development_triggered=false`、`hardware_accessed=false`、
  `target_hardware_validated=false`、`production_ready=false`。

## 2026-07-16 Client2 中控 HVAC/Seat 规划边界

`S2-HMI-001..006` 的 Client2 意图/计划/执行/结果界面、HVAC/Seat Effect 详情、Java reducer、typed Binder、Room session、
debug/test Digital Twin 和 Simulated Effect adapter 都是 Android 用户态工作，不新增 Driver/HAL。
HMI 只能经 SDK/Governance/Effect 调用 adapter；不得打开 device node、猜测 VHAL property 或将本地
View 状态当作车身回读。

演示闭环阶段保持 `source=SIMULATED`、`hardware_accessed=false`、
`driver_development_triggered=false`。真实 target profile 无可用 Vehicle adapter 时返回 unavailable；
只有 OEM/Vendor 提供 HVAC/Seat API/ABI、area/capability、权限、Safety、readback、fault 和 rollback
证据后，才在 P8 评审 `DRV-GAP-002`。因此 HMI-D0..D4 不改变任何 Driver/HAL gap，新增驱动开发量为 0。

1920x1080 Panel 安全框、预览缩放和半透明材质均属于 Android HMI 用户态设计，不新增 Surface、
Display、GPU、Kernel、Driver 或 HAL 接口要求；HMI-D1 只能使用厂商已提供的公开渲染能力。

## 驱动接口矩阵

| 能力 | Android 优先接口 | 需要新增 Driver/HAL 的触发条件 | 当前状态 | Gap |
| --- | --- | --- | --- | --- |
| Vendor NPU | vendor AIDL/SDK/NDK -> ModelProvider | 无可用 service/SDK，且 PCIe ABI/owner 已发布 | empty | DRV-GAP-001 |
| Vehicle property | CarPropertyManager 或 OEM service | 目标无公开车辆 API，owner 要求新 HAL/driver | empty | DRV-GAP-002 |
| Camera/Audio/Sensors | Camera2/Audio/Car/OEM API | 公开 API 无法满足带宽/时间戳/安全需求 | unavailable | DRV-GAP-003 |
| Ethernet/SOME-IP/DDS/TSN | OEM middleware/service | 无受控用户态 transport 且接口 owner 明确 | unavailable | DRV-GAP-004 |
| Shared memory/Safety IPC | AHardwareBuffer/SharedMemory/vendor IPC | 目标跨域 transport 要求新 kernel/HAL 支持 | unavailable | DRV-GAP-005 |
| App Binder/Room/HMI | Android framework API | 不触发驱动开发 | implemented | none |

## NPU 接口最低抽象

真实 NPU adapter 至少提供 device discovery/status、model load/unload、session create、infer/stream、
cancel、metrics、fault isolation 和受控 reset，并映射以下边界：

| 维度 | 必须明确 |
| --- | --- |
| ABI | service/AIDL hash 或 header/library version、supported ABI |
| Buffer | type、size、alignment、shape/stride、ownership、IOMMU/cache、revoke |
| Lifecycle | init/warmup/load/infer/cancel/close、death/restart |
| Security | package/signer/SELinux/vendor ACL、model signature |
| QoS | queue、slot、priority、deadline、timeout、backpressure |
| Fault | PCIe/runtime/firmware/thermal/IOMMU、unknown result、reset/rollback |
| Evidence | target smoke、performance、long-run、privacy、upgrade/rollback |

详细合同见 `docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md`。

## 当前 Driver/HAL gap backlog

| Gap ID | 关联 Req ID | 缺失输入 | 允许的当前实现 | 关闭条件 |
| --- | --- | --- | --- | --- |
| DRV-GAP-001 | HW-002, NV-F-011, KH-003/006/007 | Vendor NPU SDK/service、PCIe/firmware/buffer/fault ABI | ModelProvider + vendor empty + C ABI boundary | 目标 owner、API/ABI、权限、buffer、fault、smoke、rollback |
| DRV-GAP-002 | NV-F-003..005, KH-001/002/004 | VHAL/OEM vehicle API 与 signal mapping | Effect contract + empty vehicle adapter | property/action allowlist、permission、Safety/readback、target test |
| DRV-GAP-003 | NV-F-006, KH-004/005 | Camera/Audio/Sensor source 和时间同步 | unavailable interface | API、privacy、bandwidth、timestamp、fault evidence |
| DRV-GAP-004 | NV-P-003..007, KH-008/009 | SOME-IP/DDS/TSN/PTP owner/runtime | typed Binder/Event contract only | transport owner、QoS、schema、security、target load evidence |
| DRV-GAP-005 | HV-003, KH-006/007 | shared-memory/Safety cross-domain ABI | interface constraint only | Hypervisor/Safety owner、memory ownership、fault/recovery evidence |

## 新增开发量触发流程

1. 目标 owner 提交公开 API/ABI、权限、版本和缺口证据。
2. 先确认 Java/AIDL/NDK/vendor user-space adapter 是否足够。
3. 只有确实需要 kernel/HAL 支持时，建立独立 gap work package。
4. work package 必须列出 source owner、supported kernel/ABI、buffer ownership、security、fault、
   upgrade/rollback 和 target acceptance。
5. 未通过 code review、目标 smoke、fault injection 和 rollback 前，production registry 不得加载。
6. Driver/HAL 变更不能由普通 APK 或 debug test 自动触发。

## 软件交付边界

所有 R1-R7/B0-B5 应用层增量均必须说明是否访问硬件、是否新增 Driver/HAL、是否改变虚拟化状态。
下列历史 Android 交付 trace 保留用于静态门禁；它们的应用层 PASS 不关闭 DRV-GAP-001..005。

## Android Runtime Evolution Driver/HAL Boundary

`docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md` R0..R7 只建设 Android 13 用户态 AI SDK、Runtime Service、Binder、Room/SQLite、治理和 deterministic provider。它不修改厂商 Android Framework、BSP、预编译系统组件、kernel driver、HAL、SELinux policy 或虚拟化层。

Model Router 当前只保留 test/debug deterministic provider；Vendor NPU provider 保持 empty adapter，并继续报告 `hardware_accessed=false`、`driver_development_triggered=false` 和 `production_ready=false`。仓库不存在 Python/Ollama provider。只有目标硬件、vendor SDK、ABI、buffer/fault contract 和 smoke evidence 明确后，才通过 `DRV-GAP-001` 触发最小 Java/JNI/C ABI 或 HAL bridge 开发。

Event callback、Room persistence、Binder identity 和 Android Job Supervisor 不需要新增 Driver/HAL。DDS/shared-memory 高频数据面、Vehicle bus、Camera/Audio/Sensors 和 Safety Runtime 仍分别受 `DRV-GAP-002..005` 约束。本阶段不交付 Linux 前端。

Req IDs：`XSC-004`、`XSC-006`、`NV-F-001`、`NV-F-011`、`NV-P-002`、`HW-002`、`KH-003`、`KH-006`、`KH-007`、`DEL-001`、`DEL-005`。

### R1A Gradle Foundation Driver/HAL Evidence

R1A only adds source-built Android application/library boundaries. `central-brain-sdk` is a Java AAR, `runtime-service` is non-exported and returns no Binder, and `demo-hmi` only renders SDK version/maturity text. None requests network, vehicle, camera, audio, location, device-node or privileged permissions.

The successful AAR/APK build does not open a device node, call a vendor SDK/HAL, access PCIe NPU/vehicle bus/shared memory, or modify the Android system image. No new Driver/HAL gap was found and added driver development remains zero. Req IDs: `XSC-004`, `XSC-006`, `NV-F-001`, `NV-P-002`, `DEL-001`, `DEL-005`.

### R1B Device Lifecycle Driver/HAL Evidence

The debug-only DUMP-protected probe, adb installer, service `dumpsys` check and Demo UI dump use Android application/framework diagnostics only. They do not inspect a device node, ioctl/sysfs, vendor SDK/HAL, PCIe NPU, vehicle bus, shared memory, Camera/Audio/Sensors or Safety Runtime. The API 36 result explicitly reports `hardware_accessed=false`, `driver_development_triggered=false`, and `virtualization_development_triggered=false`; new Driver/HAL development remains zero. Req IDs: `XSC-004`, `XSC-006`, `NV-F-001`, `NV-P-002`, `DEL-001`, `DEL-005`.

### R1C API 33 Exit Driver/HAL Evidence

The Android 13/API 33 x86_64 strict test repeated the same application/framework-only checks and returned `r1_api33_exit_criteria_met=true`, `hardware_accessed=false`, `driver_development_triggered=false`, and `virtualization_development_triggered=false`. The AVD does not represent target SoC/NPU hardware and does not close `DRV-GAP-001..005`; R1 completion adds no Driver/HAL development. Req IDs: `XSC-004`, `XSC-006`, `NV-F-001`, `NV-P-002`, `DEL-001`, `DEL-005`.

### R2A AIDL Contract Driver/HAL Boundary

R2A adds Java Binder metadata and structured parcelables only. Production AIDL explicitly rejects file descriptors, shared memory, vendor handles and JSON escape payloads; diagnostic AIDL is bounded read-only paging. No interface opens a device node, calls HAL/vendor SDK, accesses PCIe NPU/vehicle bus or modifies the system image. `hardware_accessed=false`, `driver_development_triggered=false`, and `virtualization_development_triggered=false`; no new Driver/HAL development is required. Req IDs: `XSC-004`, `XSC-006`, `NV-F-001`, `NV-P-002`, `DEL-001`, `DEL-005`.

### R2B Binder Runtime Driver/HAL Boundary

R2B adds Android application-layer Binder Service/client code, signature permissions, an in-memory deterministic task runner and read-only diagnostics. The production and diagnostic paths contain no native library, JNI, device node, ioctl, sysfs, VHAL/vendor AIDL, PCIe/NPU runtime, vehicle bus, shared memory, DMA-BUF or Safety Runtime access. API 33 device validation reports `hardware_accessed=false`, `driver_development_triggered=false`, and `virtualization_development_triggered=false`; no Driver/HAL gap is activated and no new driver development is required. Req IDs: `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-001`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-005`.

### R2C Binder Lifecycle Driver/HAL Boundary

R2C uses public Android Binder death recipients, `bindService`/`unbindService`, app instrumentation, `am force-stop`, logcat and DUMP-protected debug Activities. The client-death scenario keeps the app-layer Runtime started only so callback Binder death can be observed. No test opens a device node, invokes JNI/HAL/vendor SDK, probes PCIe/NPU/vehicle interfaces, uses shared memory or changes the Android image. API 33 evidence reports `hardware_accessed=false`, `driver_development_triggered=false`, and `virtualization_development_triggered=false`; R2 completion adds zero Driver/HAL development and closes no DRV-GAP. Req IDs: `XSC-004`, `XSC-006`, `NV-F-001`, `NV-G-006`, `NV-P-002`, `DEL-001`, `DEL-004`, `DEL-005`.

### R3A Job Supervisor Driver/HAL Boundary

R3A adds application-process Java only: a bounded task state machine, terminal retention, Binder UID/package/current-signer identity capture and owner isolation. Identity uses public `Binder`, `PackageManager`, `SigningInfo` and `UserManager` APIs. It does not need NDK/JNI, VHAL, vendor AIDL, device nodes, ioctl/sysfs, PCIe/NPU, vehicle bus, shared memory, Camera/Audio/Sensors, Safety Runtime or an Android system-image change.

JVM and API 33 tests report `job_supervisor_active=true`, `trusted_caller_identity_resolved=true`, `request_identity_fields_used=false`, `hardware_accessed=false`, `driver_development_triggered=false`, and `virtualization_development_triggered=false`. No DRV-GAP is activated and added Driver/HAL development remains zero. Capability configuration and approval are Runtime & Governance R3B/R3C work, not Driver/HAL work. Req IDs: `XSC-004`, `XSC-005`, `XSC-006`, `FW-U-007`, `NV-F-001`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-004`, `DEL-005`.

### R3B Capability Policy Driver/HAL Boundary

R3B adds an APK resource parser, Java package/current-signer policy evaluator and a separately installed test-only denial probe. These are Android application and Binder authorization mechanisms only. The probe deliberately shares the debug signer so it can pass the outer signature permission; denial occurs inside Runtime & Governance because its package has no capability rule.

API 33 evidence reports `outer_signature_permission_passed=true`, `outer_diagnostic_signature_permission_passed=true`, `unknown_client_default_deny_verified=true`, `diagnostic_capability_default_deny_verified=true`, `package_and_current_signer_mapping_verified=true`, `hardware_accessed=false`, `driver_development_triggered=false`, and `virtualization_development_triggered=false`. No native code, device node, vendor SDK/HAL, NPU, vehicle interface, shared memory, Safety Runtime or system-image change is involved; no DRV-GAP is activated and added Driver/HAL development remains zero. Req IDs: `XSC-004`, `XSC-005`, `XSC-006`, `FW-U-007`, `NV-F-001`, `NV-G-005`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-004`, `DEL-005`.

### R3C1 Action Governance Core Driver/HAL Boundary

R3C1 adds pure Java risk policy, a Safety/Vehicle State provider interface and a process-local pending-approval registry. The current provider is a Runtime-owned deterministic fixture, not VHAL/Safety Runtime integration; it explicitly returns `hardwareBacked=false` and `productionTrusted=false`. Policy decisions never dispatch a service or vehicle action and always return `dispatchAllowed=false`.

No code opens device nodes, invokes ioctl/sysfs, JNI, VHAL/vendor AIDL, Safety Runtime, Vehicle bus, shared memory, PCIe NPU or vendor SDK. `DRV-GAP-002` and `DRV-GAP-005` remain open for target Vehicle/Safety sources and are not changed by this fixture. Added Driver/HAL development is zero; `hardware_accessed=false`, `driver_development_triggered=false`, and `virtualization_development_triggered=false` remain mandatory. Req IDs: `XSC-004`, `XSC-005`, `XSC-006`, `FW-U-004`, `FW-U-007`, `FW-S-005`, `NV-F-001`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-004`, `DEL-005`.

### R3C2 Governance Binder Driver/HAL Boundary

R3C2 adds app-layer AIDL, Binder Service/client, signature permission and capability checks only. `ActionRequest` carries no device handle or vehicle state; the Service reads the same Runtime-owned hardware-free provider and returns `sourceHardwareBacked=false`, `sourceProductionTrusted=false`, and `dispatchAllowed=false`. Approval status remains process-local with no grant path.

API 33 testing uses APK install, Binder, PackageManager, logcat and UIAutomator only. It does not invoke VHAL/vendor AIDL, Safety Runtime, Vehicle bus, shared memory, JNI, ioctl/sysfs, device nodes, PCIe NPU or vendor SDK. `DRV-GAP-002`/`DRV-GAP-005` remain open and unchanged; added Driver/HAL development is zero. `hardware_accessed=false`, `driver_development_triggered=false`, `virtualization_development_triggered=false`, and `service_dispatch_triggered=false` are exit evidence. Req IDs: `XSC-004`, `XSC-005`, `XSC-006`, `FW-U-004`, `FW-U-007`, `FW-S-005`, `NV-F-001`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-004`, `DEL-005`.

### R4A Room Schema Driver/HAL Boundary

R4A adds Java annotation processing, AndroidX Room runtime, app-private SQLite files, schema JSON and a debug migration Activity. SQLite storage uses Android application APIs and does not require kernel, HAL, vendor SDK, VHAL, Safety Runtime or shared-memory support.

The migration probe creates/deletes only an isolated app-private test database and reports `durable_dispatch_enabled=false`. No pending effect or outbox row is dispatched; no NPU, Vehicle bus, device node, ioctl/sysfs, camera/audio/sensor or PCIe resource is accessed. No DRV-GAP changes state and added Driver/HAL development remains zero. Req IDs: `XSC-005`, `XSC-006`, `FW-U-004`, `NV-F-001`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-004`, `DEL-005`.

### R4B1 Durable Repository Driver/HAL Boundary

R4B1 adds pure Java SHA-256 ownership, Room transactions and an app-private debug probe. It consumes only trusted identity snapshots already resolved through Binder/PackageManager; it does not query VHAL, Safety Runtime, NPU, sensors, device nodes, vendor SDK or shared memory.

The repository stores task metadata/digests and acceptance audit rows only. It does not create pending effects/outbox rows, invoke SOA/Skill/Action dispatch or require native code. `runtime_repository_wired=false`, `durable_dispatch_enabled=false`, all hardware/Driver/HAL/virtualization flags remain false, and added Driver/HAL development is zero. Req IDs: `FW-U-004`, `NV-F-001`, `NV-G-006`, `NV-G-007`, `XSC-005`, `DEL-001`, `DEL-004`.

### R4B2 Durable Runtime Driver/HAL Boundary

R4B2 connects only the source-built Java Runtime Service to its app-private Room database. Binder identity, SHA-256, SQLite WAL and callback delivery use Android application APIs; no JNI/C/C++, VHAL, Safety Runtime, NPU, vendor SDK, shared memory, device node, ioctl/sysfs or vehicle bus is required.

Task transitions persist metadata/digests/checkpoints/audits but never create or consume pending-effect/outbox work. Runtime reports `task_recovery_enabled=false` and `durable_dispatch_enabled=false`; restart recovery and any future adapter activation remain separately gated. No DRV-GAP changes state and added Driver/HAL development is zero. Req IDs: `FW-U-004`, `NV-F-001`, `NV-G-003`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `XSC-005`, `XSC-006`, `DEL-001`, `DEL-004`.

### R4B3 Durable Approval Driver/HAL Boundary

R4B3 uses Java Binder identity, SHA-256 owner fingerprint and app-private Room only. Approval rows/audits contain Action ID, derived risk/reason, status, idempotency metadata and timestamps; they contain no VHAL frame, signer certificate, device handle or hardware payload.

The Governance AIDL has no approve/grant call and every response keeps `dispatchAllowed=false`. No pending effect/outbox is created, no NPU/vehicle/vendor adapter is selected, and no C/C++/JNI/Driver/HAL work is added. All hardware/Driver/HAL/virtualization flags remain false. Req IDs: `FW-U-004`, `FW-U-007`, `NV-F-001`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `XSC-005`, `XSC-006`, `DEL-001`, `DEL-004`.

### R4C1 Restart Reconciliation Driver/HAL Boundary

R4C1 uses Java Executor/Future, Binder callback delivery and app-private Room transactions only. It reads task/checkpoint/audit metadata from SQLite and does not inspect a VHAL frame, Safety Runtime state, NPU resource, sensor, device node, vendor SDK or shared memory.

Restart candidates are failed closed and never resumed or dispatched. No pending effect/outbox row is created or consumed, no C/C++/JNI/Driver/HAL code is added, and no existing DRV-GAP changes state. `task_execution_resume_enabled=false`, `durable_dispatch_enabled=false`, `hardware_accessed=false`, `driver_development_triggered=false`, and `virtualization_development_triggered=false` are exit evidence. Req IDs: `FW-U-004`, `NV-F-001`, `NV-G-003`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `XSC-005`, `XSC-006`, `DEL-001`, `DEL-004`.

### R4C2A Effect Outbox Driver/HAL Boundary

R4C2A uses Java SHA-256, Room transactions and an app-private debug probe only. Destination values are inert routing labels; no UIB/SOA/Skill adapter, VHAL, Safety Runtime, NPU, vendor SDK, shared memory, device node or vehicle bus is opened.

An IN_FLIGHT row may be requeued because no dispatcher exists in this increment. Before any real adapter activation, the Driver/HAL/vendor interface contract must consume the persisted idempotency token or provide a trusted delivery-status query; otherwise ambiguous crash retries remain unsafe. No DRV-GAP changes state and added C/C++/JNI/Driver/HAL work is zero. `effect_repository_wired=false`, `outbox_dispatch_enabled=false`, `service_dispatch_triggered=false` and all hardware/virtualization flags remain false. Req IDs: `APP-004`, `XSC-001`, `XSC-004`, `FW-U-004`, `FW-U-005`, `NV-F-001`, `NV-G-006`, `NV-G-007`, `DEL-001`, `DEL-004`.

### R4C2B Effect Terminal State Driver/HAL Boundary

R4C2B adds only Java/Room state transitions, SHA-256 replay evidence and API 33 app-private database probes. Retry delay, attempt limits, terminal states and exhausted-claim reconciliation do not open a device node, call JNI/C/C++, VHAL, Safety Runtime, vendor SDK, PCIe NPU, vehicle bus or shared memory.

`EFFECT_CLAIM_EXHAUSTED` records an unknown final-attempt result as local FAILED/DEAD_LETTER; it is not destination status evidence. Before R4C3 or a target-platform integration may wire an adapter, the existing Driver/HAL/vendor contract must accept the persisted idempotency token or expose a trusted query by that token. If the vendor ABI supports neither, the gap must be recorded before any minimal bridge is developed. This increment activates no DRV-GAP and adds zero Driver/HAL development. `effect_repository_wired=false`, `outbox_dispatch_enabled=false`, `service_dispatch_triggered=false` and all hardware/virtualization flags remain false. Req IDs: `APP-004`, `XSC-001`, `XSC-004`, `FW-U-004`, `FW-U-005`, `NV-F-001`, `NV-G-006`, `NV-G-007`, `DEL-001`, `DEL-004`.

### R4C3A Effect Adapter Driver/HAL Boundary

R4C3A defines a Java adapter contract and status reconciler plus a debug-only deterministic fixture. The contract requires persisted-token deduplication, original-result replay and linearizable token status, but it does not implement a vendor adapter, JNI/C/C++, VHAL, Safety Runtime, NPU runtime, vehicle bus, shared memory, device-node or HAL call.

The fault probe uses app-memory canonical bytes and simulated remote status. `transient_effect_material_durable=false` means it cannot qualify target-process restart or real hardware. Target integration must first map the vendor ABI to token apply/status semantics and a trusted material source; an ABI without those primitives must be recorded as a DRV-GAP before minimal bridge work. No existing DRV-GAP changes state and added Driver/HAL development remains zero. `effect_adapter_production_wired=false`, `real_adapter_dispatch_enabled=false`, `service_dispatch_triggered=false` and all hardware/virtualization flags remain false. Req IDs: `APP-004`, `XSC-001`, `XSC-004`, `FW-U-004`, `FW-U-005`, `NV-F-001`, `NV-G-006`, `NV-G-007`, `DEL-001`, `DEL-004`.

### R4C3B Effect Material Activation Driver/HAL Boundary

R4C3B adds Java descriptor/blocker logic, an explicit empty material provider and debug-only process-memory material fixture. It does not add a Room blob, file store, Android Keystore key, vendor secure storage, JNI/C/C++, HAL, device-node or hardware call.

The gate requires process-restart durability, encryption, effect-bound integrity, bounded retention and deletion before any future adapter activation. Current source is empty and activation remains false. Target key/storage ownership is a platform security/product decision; only a vendor requirement that cannot be satisfied through application-private encrypted storage or published SDK should create a DRV-GAP. This increment changes no DRV-GAP and adds zero Driver/HAL development. `production_effect_material_durable=false`, `raw_effect_material_persisted=false`, `real_adapter_dispatch_enabled=false`, `service_dispatch_triggered=false` and all hardware/virtualization flags remain false. Req IDs: `APP-004`, `XSC-001`, `XSC-004`, `FW-U-004`, `FW-U-005`, `NV-F-001`, `NV-G-006`, `NV-G-007`, `DEL-001`, `DEL-004`.

### R4C3C Production Gate Visibility Driver/HAL Boundary

R4C3C wires only immutable blocked-state metadata into Runtime log/dumpsys and the existing read-only Diagnostic Binder. Snapshot construction evaluates a null adapter plus the empty Java provider and performs no I/O. No effect repository, adapter, material store, JNI/C/C++, HAL, device node, VHAL, NPU, vehicle bus or shared memory is opened.

R4 stage completion therefore means Android durable-workflow and fail-closed activation boundaries are integrated, not that any Driver/HAL or hardware path is active. Target adapter/material evidence remains a prerequisite and may later reference existing DRV-GAP records if published platform APIs are insufficient. No DRV-GAP changes state and added Driver/HAL development remains zero. Activation/apply/status/dispatch/hardware/virtualization flags remain false. Req IDs: `APP-004`, `XSC-001`, `XSC-004`, `FW-U-004`, `FW-U-005`, `NV-F-001`, `NV-G-006`, `NV-G-007`, `DEL-001`, `DEL-004`.

### R5A1 Model Provider Driver/HAL Boundary

R5A1 adds Java types, immutable profiles, JVM tests and one DUMP-protected debug Activity. The deterministic profile is TEST_ONLY and the Vendor NPU profile is explicitly EMPTY; neither profile is instantiated or referenced by production Runtime/Governance. No Java network client, JNI/C/C++, vendor library, HAL/AIDL service, device node, ioctl/sysfs, PCIe enumeration, DMA-BUF/IOMMU, VHAL or Safety Runtime call is added.

The Vendor-empty profile does not probe the target and cannot close `DRV-GAP-001`. It has zero inference slots, UNAVAILABLE health, no metrics/fallback and no hardware evidence. A later vendor implementation may be developed only after the published Android SDK/ABI, ownership, permissions and target smoke evidence identify a concrete gap. Added Driver/HAL development remains zero; `model_provider_runtime_wired=false`, `model_router_dispatch_enabled=false`, `vendor_npu_provider_available=false`, `hardware_accessed=false`, `driver_development_triggered=false`, and `virtualization_development_triggered=false` are mandatory. Req IDs: `APP-004`, `XSC-004`, `NV-F-011`, `NV-G-004`, `NV-G-006`, `DEL-001`, `DEL-004`, `DEL-005`.

### R5A2 Scheduler Driver/HAL Boundary

R5A2 is Java collection/state-machine code plus JVM/API 33 probes. It consumes only trusted owner fingerprints, model/provider IDs, elapsed-realtime deadlines, quota values and synthetic lease IDs. It does not instantiate a provider, execute inference, call provider cancellation, open a network connection or inspect hardware state.

Provider slots are contract counters, not detected NPU resources. Current Stub/Vendor profiles map to disabled routes; enabled `test.*` routes exist only inside unit/debug evidence. No JNI/C/C++, vendor library, HAL/AIDL service, device node, ioctl/sysfs, PCIe, DMA-BUF/IOMMU, VHAL or Safety Runtime code is added. No DRV-GAP changes state and added Driver/HAL development remains zero. `provider_cancel_invoked=false`, `scheduler_production_wired=false`, `model_router_dispatch_enabled=false`, `hardware_accessed=false`, `driver_development_triggered=false`, and `virtualization_development_triggered=false` are mandatory. Req IDs: `APP-004`, `XSC-004`, `NV-F-001`, `NV-F-011`, `NV-G-004`, `NV-G-006`, `DEL-001`, `DEL-004`, `DEL-005`.

### R5B1 Deterministic Provider Driver/HAL Boundary

The executable Stub is Java-only and consumes model/input SHA-256 metadata. It generates bounded synthetic bytes, schedules phases on an injected executor and uses an injected elapsed clock. Fault modes mutate only in-process lifecycle state and do not emulate a vendor ABI, PCIe/IOMMU fault or Safety Runtime signal.

No network, JNI/C/C++, vendor library, HAL/AIDL service, device node, ioctl/sysfs, PCIe enumeration, DMA-BUF, VHAL or hardware metric access is present. The implementation remains TEST_ONLY, profile configuration/routing and production inference remain false, and `DRV-GAP-001` stays open. Added Driver/HAL development is zero; all no-hardware/virtualization flags remain mandatory. Req IDs: `APP-004`, `XSC-004`, `NV-F-011`, `NV-G-004`, `NV-G-006`, `DEL-001`, `DEL-004`, `DEL-005`.

### R5B2 Test-Only Router Driver/HAL Boundary

R5B2 composes Java Scheduler and deterministic-provider objects only in JVM/debug evidence. Route, lease and cancellation identifiers are synthetic application-process values; model/input material is SHA-256 metadata and output is synthetic bytes. Provider slot accounting does not discover or reserve an NPU resource.

No network, JNI/C/C++, vendor library, HAL/AIDL service, device node, ioctl/sysfs, PCIe, DMA-BUF/IOMMU, VHAL, Safety Runtime or hardware metric path is added. Production Services do not construct the Router, Vendor NPU remains EMPTY and `DRV-GAP-001` stays open. Added Driver/HAL development is zero; `production_model_router_wired=false`, `production_model_router_dispatch_enabled=false`, `hardware_accessed=false`, `driver_development_triggered=false`, and `virtualization_development_triggered=false` are mandatory. Req IDs: `APP-004`, `XSC-004`, `NV-F-001`, `NV-F-011`, `NV-G-004`, `NV-G-006`, `DEL-001`, `DEL-004`, `DEL-005`.

### R5C1 Model Readiness Visibility Driver/HAL Boundary

R5C1 reads immutable Java profile descriptors/snapshots and formats them for Runtime log, protected dumpsys and Diagnostic Binder. Vendor `UNAVAILABLE` and `VENDOR_RUNTIME_UNAVAILABLE` are empty-interface constants; no driver/HAL/vendor process was queried. Stub `HEALTHY` is contract metadata paired with COLD/NOT_WIRED, not a live process health signal.

No network, JNI/C/C++, vendor library, HAL/AIDL service, device node, ioctl/sysfs, PCIe, DMA-BUF/IOMMU, VHAL, Safety Runtime or hardware metric polling is added. `DRV-GAP-001` remains open and added Driver/HAL development is zero. Production inference/router/scheduler/Ollama/Vendor access plus all hardware/virtualization flags remain false. Req IDs: `APP-004`, `XSC-004`, `XSC-005`, `NV-F-011`, `NV-F-012`, `NV-G-006`, `NV-G-007`, `DEL-001`, `DEL-004`, `DEL-005`.

### R5D1 Application-Layer Deployment Driver/HAL Boundary

R5D1 uses adb install plus public package/property/manifest/dumpsys queries. Accepted APKs live under `/data/app` with ordinary application UIDs and do not require system/privileged flags. The tool has no system/vendor partition write capability and does not require platform source, vendor SDK, JNI/C/C++, HAL/AIDL hardware service, device node, PCIe, DMA-BUF/IOMMU, VHAL or Safety Runtime.

The API 33 emulator result does not close any DRV-GAP. A physical device application-layer pass still leaves `target_hardware_validated=false`; `DRV-GAP-001` requires separate vendor NPU ABI, permission, memory, lifecycle, cancellation, fault and target smoke evidence. Added Driver/HAL development remains zero and all no-hardware/virtualization flags are mandatory. Req IDs: `APP-004`, `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-011`, `NV-F-012`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

### R6A1 Event Runtime Driver/HAL Boundary

R6A1 is Java collection/state-machine code using injected elapsed time and synthetic IDs. Trusted topics describe Runtime/Governance/Model metadata only; events carry schema and SHA-256 digest metadata, not vehicle frames, sensor buffers or shared-memory handles. API 33 evidence runs in one app process.

No Binder callback, DDS, SOME/IP, MQTT, SSE/WebSocket, SocketCAN, VHAL, shared memory, JNI/C/C++, vendor library, device node, vehicle bus or Safety Runtime is accessed. Existing `DRV-GAP-002/004/005` remain unchanged and added Driver/HAL work is zero. `dds_runtime_active=false`, `network_transport_active=false`, `vehicle_bus_accessed=false`, `hardware_accessed=false`, and virtualization false are mandatory. Req IDs: `XSC-002`, `XSC-004`, `XSC-005`, `FW-U-003`, `NV-G-004`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `NV-P-006`, `DEL-001`, `DEL-004`, `DEL-005`.

### R6A2A Event Schema Driver/HAL Boundary

R6A2A changes only the app-private Room/SQLite metadata schema and debug migration evidence. It stores subscription identity, topic names, sequence numbers, queue/state/overflow metadata and timestamps; no event body, vehicle frame, sensor buffer, shared-memory handle or hardware address is stored.

No Driver/HAL ABI, JNI/C/C++, VHAL, DDS, network transport, device node, vendor service, NPU or Safety Runtime is required. `DRV-GAP-002/004/005` remain open and unchanged; added Driver/HAL development is zero. Req IDs: `XSC-002`, `XSC-004`, `XSC-005`, `FW-U-003`, `FW-U-004`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `NV-P-006`, `DEL-001`, `DEL-004`, `DEL-005`.

### R6A2B Event Repository Driver/HAL Boundary

R6A2B executes Room transactions over subscription/cursor metadata and SHA-256 audit digests. Trusted latest sequence is an injected application-layer value; the repository does not read a timer device, vehicle bus, shared-memory counter or vendor event source. Source regression is rejected in software.

No JNI/C/C++, Driver/HAL ABI, VHAL, DDS, network, device node, NPU, vendor service or Safety Runtime is accessed. The missing durable monotonic event source is a Runtime/broker activation blocker under ISSUE-025, not a reason to create a driver in the current environment. Existing `DRV-GAP-002/004/005` and added Driver/HAL work remain unchanged/zero.

### R6A3 Event Readiness Driver/HAL Boundary

R6A3 formats immutable Java booleans and blocker IDs for log, dumpsys and Diagnostic Binder. It does not query a publisher clock, database row, callback registry, broker process, network stack or vehicle source. `durable_event_source_available=false` is product/runtime integration evidence, not a device-driver probe result.

No JNI/C/C++, Driver/HAL ABI, VHAL, DDS, network, shared memory, device node, NPU, vendor service or Safety Runtime is accessed. `DRV-GAP-002/004/005` remain unchanged, added Driver/HAL work remains zero, and no vendor/AOSP/BSP or virtualization change is introduced.

### R6B1 Memory Lifecycle Driver/HAL Boundary

R6B1 is Java collection/state-machine code over owner IDs, purpose enums, SHA-256 references and an injected elapsed clock. It stores no raw utterance/model output, vehicle frame, sensor buffer, shared-memory handle or hardware address. PROFILE consent and export authorization are synthetic trusted contract inputs, not calls into a vendor security service.

No JNI/C/C++, keystore vendor extension, Driver/HAL ABI, VHAL, network, shared memory, device node, NPU, vendor service or Safety Runtime is accessed. Durable encrypted PROFILE storage may later use Android application-private storage and Keystore; only an unmet requirement after inspecting published target APIs may create a new DRV-GAP. Existing gaps remain unchanged, added Driver/HAL work is zero, and no vendor/AOSP/BSP or virtualization change is introduced. Req IDs: `XSC-001`, `XSC-004`, `XSC-005`, `FW-U-006`, `FW-U-007`, `NV-F-001`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-004`, `DEL-005`.

### R6B2 Memory Readiness Driver/HAL Boundary

R6B2 formats immutable Java prerequisite flags for Runtime log, protected dumpsys and Diagnostic Binder. `DURABLE_ENCRYPTED_STORAGE_MISSING` and `KEY_LIFECYCLE_NOT_CONFIGURED` are product/security integration blockers; the snapshot does not inspect filesystem encryption, Android Keystore, StrongBox, a vendor security service or device state.

No JNI/C/C++, Driver/HAL ABI, VHAL, network, shared memory, device node, NPU, vendor service or Safety Runtime is accessed. No DRV-GAP is activated until published Android/target security APIs are shown insufficient for an approved storage/key design. Existing gaps remain unchanged, added Driver/HAL work is zero, and no vendor/AOSP/BSP or virtualization change is introduced. Req IDs: `XSC-001`, `XSC-004`, `XSC-005`, `XSC-006`, `FW-U-006`, `FW-U-007`, `NV-F-001`, `NV-F-012`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-004`, `DEL-005`.

### R6C1 Built-In Skill Driver/HAL Boundary

R6C1 uses Java immutable manifests, SHA-256 strings, enum policy and bounded in-process admission records. Route targets are metadata only; no SOA/UIB/Agent route is called. Signer allowlist matching compares compile-time digest evidence and does not invoke PackageManager, APK signature APIs, a vendor trust service or hardware-backed key verification.

No APK/JAR/dex/native loader, JNI/C/C++, Driver/HAL ABI, VHAL, network, shared memory, device node, NPU, vendor service or Safety Runtime is accessed. A later target requirement for hardware-backed attestation creates a DRV-GAP only if published Android/target APIs cannot satisfy an approved verifier design. Existing gaps remain unchanged, added Driver/HAL work is zero, and no vendor/AOSP/BSP or virtualization change is introduced. Req IDs: `APP-004`, `XSC-001`, `XSC-004`, `XSC-005`, `FW-U-006`, `FW-U-007`, `FW-U-008`, `NV-F-001`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-004`, `DEL-005`.

### R6C2 Governance Middleware Driver/HAL Boundary

R6C2 evaluates Java enums, immutable Skill manifest metadata, trusted identity/policy booleans, elapsed-time QoS values and SHA-256 digests. The dispatch stage is a software admission gate and never calls an adapter, service or device. Output guard receives only schema/digest/size/redaction metadata, not a vehicle frame, model buffer or shared-memory handle.

No JNI/C/C++, Driver/HAL ABI, VHAL, DDS, network, shared memory, device node, NPU, vendor service or Safety Runtime is accessed. Existing DRV-GAP items remain unchanged, added Driver/HAL work is zero, and no vendor/AOSP/BSP or virtualization change is introduced. Req IDs: `APP-004`, `XSC-001`, `XSC-002`, `XSC-004`, `XSC-005`, `FW-U-003`, `FW-U-006`, `FW-U-007`, `FW-U-008`, `NV-F-001`, `NV-G-003`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-004`, `DEL-005`.

### R6C3 Skill/Governance Readiness Driver/HAL Boundary

R6C3 formats immutable Java constants and blocker IDs for Runtime log, protected dumpsys and Diagnostic Binder. Artifact verifier missing, sandbox missing and route-owner registry missing are product/security/runtime integration blockers; the snapshot does not query PackageManager signing data, hardware-backed keys, a vendor trust service, filesystem, device node or adapter.

No JNI/C/C++, Driver/HAL ABI, VHAL, DDS, network, shared memory, device node, NPU, vendor service or Safety Runtime is accessed. A future hardware-backed attestation requirement creates a DRV-GAP only if published Android/target APIs cannot satisfy an approved design. Existing gaps remain unchanged, added Driver/HAL work is zero, and no vendor/AOSP/BSP or virtualization change is introduced. Req IDs: `APP-004`, `XSC-001`, `XSC-002`, `XSC-004`, `XSC-005`, `XSC-006`, `FW-U-003`, `FW-U-006`, `FW-U-007`, `FW-U-008`, `NV-F-001`, `NV-F-012`, `NV-G-003`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-004`, `DEL-005`.

### R7A1 Runtime Acceptance Driver/HAL Boundary

R7A1 reads immutable Java readiness snapshots and compile-time baseline constants. `TARGET_HARDWARE_NOT_VALIDATED` is deliberately preserved as an aggregate blocker; the snapshot does not probe PCIe, NPU, VHAL, vehicle bus, shared memory, vendor services or device state.

No JNI/C/C++, Driver/HAL ABI, system/vendor partition access, network, device node or Safety Runtime is added. The aggregate software-ready flag cannot close any DRV-GAP. Existing gaps remain unchanged, added Driver/HAL work is zero, and no vendor/AOSP/BSP or virtualization change is introduced. Req IDs: `APP-004`, `XSC-001`, `XSC-002`, `XSC-004`, `XSC-005`, `XSC-006`, `FW-U-003`, `FW-U-004`, `FW-U-005`, `FW-U-006`, `FW-U-007`, `FW-U-008`, `NV-F-001`, `NV-F-011`, `NV-F-012`, `NV-G-003`, `NV-G-004`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

### R7B Client2 Binder Driver/HAL Boundary

R7B uses Android application APIs only: explicit Binder service binding through the public SDK, AIDL parcelables/callbacks, PackageManager-enforced signature permission, APK signing tools and UI automation. The secondary dex contains Java SDK/bridge bytecode and does not load JNI or vendor libraries. Runtime returns the existing deterministic software reply and does not route to Python/Ollama, NPU or vehicle control.

No C/C++, JNI, VHAL, vendor AIDL/HIDL, device node, PCIe/NPU, shared memory, network, vehicle bus, Safety Runtime, system/vendor partition or virtualization API is added. Existing DRV-GAP items remain unchanged and added Driver/HAL work is zero. Target RenderService signer trust is an application/vendor integration issue until published APIs prove a lower-layer gap. Req IDs: `APP-004`, `XSC-001`, `XSC-005`, `XSC-006`, `NV-G-006`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`.

### R7C Application Acceptance Driver/HAL Boundary

R7C uses adb package enable/disable, Activity force-stop/relaunch, UIAutomator, typed Binder instrumentation and one debug-only `Process.killProcess` receiver. These actions fault Android application processes only; they do not reset a SoC, NPU, ECU, VHAL, vendor service or kernel driver. Interrupted task reconciliation remains app-private Room behavior with execution resume disabled.

No C/C++, JNI, Driver/HAL ABI, device node, PCIe/NPU, shared memory, network, vehicle bus, Safety Runtime, system/vendor partition or virtualization API is added. API 33 emulator recovery evidence cannot close any DRV-GAP or target hardware claim. Existing gaps remain unchanged and added Driver/HAL work is zero. Req IDs: `APP-004`, `XSC-001`, `XSC-005`, `XSC-006`, `NV-F-001`, `NV-F-012`, `NV-G-003`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

### R7D Handoff Driver/HAL Boundary

R7D reads APK/ZIP metadata, hashes files, invokes Android package/signing host tools and uses public adb/package/dumpsys interfaces. It packages application-layer AAR/APKs only; all delivered artifacts are checked for absence of native library payloads. The installer writes only through `adb install -r` to ordinary `/data/app` packages and has no root, remount, fastboot, system/vendor partition or automatic-uninstall path.

The target deployment and Client2 recovery scripts are source-checkout test tooling only. They use the existing Android application/test interfaces and do not turn the delivery archive into a Driver/HAL or hardware qualification artifact.

The seven delivery slots keep target owner, Effect/VHAL, vendor NPU, Event broker, encrypted Memory, Skill/Governance composition and hardware evidence inactive. They are interface declarations, not Driver/HAL implementations. `DRV-GAP-001` and all other existing gaps remain open/unchanged; added Driver/HAL development is zero. A future vendor Model Provider may use C/C++ only when a published NPU SDK/ABI proves Java/public Android APIs insufficient and the owner supplies lifecycle, memory, cancel, fault and target evidence.

R7D does not access device nodes, ioctl/sysfs, PCIe, DMA-BUF/IOMMU, VHAL, DDS, vehicle bus, Safety Runtime or hardware metrics. It does not modify vendor Android/AOSP/BSP and does not develop Linux frontend or virtualization. Req IDs: `APP-004`, `XSC-001`, `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-001`, `NV-F-011`, `NV-F-012`, `NV-G-003`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

### B0 Black-Box Native Driver/HAL Boundary

B0 authorizes a userspace C runtime inside the application package, not a kernel driver or HAL. The planned `libcentral_brain_native.so` may implement ABI validation, lifecycle and provider bookkeeping only. It may not open device nodes, issue ioctl/sysfs calls, enumerate PCIe, call VHAL/vendor services, map DMA/shared memory or inspect Safety Runtime state.

Java remains the owner of PackageManager/Binder identity, permissions, policy and storage. A vendor adapter can be added only after a published SDK/ABI is supplied and B3 proves the ordinary application can access it. Existing `DRV-GAP-001` and all hardware gaps remain open; added Driver/HAL development is zero.

First packaged ABIs are `arm64-v8a` and `x86_64`. ABI packaging evidence is not hardware validation. No vendor/AOSP/BSP source, Linux frontend or virtualization code is added. Req IDs: `XSC-004`, `NV-F-001`, `NV-F-011`, `NV-P-002`, `KH-003`, `KH-006`, `DEL-001`, `DEL-004`, `DEL-005`.

### B1 Native Artifact Driver/HAL Result

B1 now produces the two allowlisted userspace libraries and verifies that their dynamic dependencies contain no OpenCL, NPU, neural, vehicle or vendor library. Static source gating also rejects file/device, dynamic-loader and network calls. The C code owns only ABI validation, mutex-protected lease bookkeeping and health state.

No Driver/HAL gap is closed or newly triggered. `DRV-GAP-001` remains open because no published vendor NPU SDK/ABI or target hardware evidence exists; VHAL, PCIe, ioctl/sysfs, DMA/shared memory and Safety Runtime remain untouched. Added Driver/HAL development is zero. Req IDs: `XSC-004`, `NV-F-001`, `NV-F-011`, `NV-P-002`, `KH-003`, `KH-006`, `DEL-001`, `DEL-004`, `DEL-005`.

### B2 Native Process Integration Driver/HAL Result

B2 packages the same B1 userspace library into the ordinary Runtime APK and owns it from the Java `Application` lifecycle. Runtime and Diagnostic only query immutable health metadata; neither Service acquires a native slot, invokes a provider, opens a device node, calls VHAL/vendor service, maps shared memory or performs PCIe/DMA/IOMMU work.

API 33 process recovery proves Android application lifecycle behavior only. It does not prove target ABI, NPU/VHAL access, Driver/HAL availability or hardware recovery. `DRV-GAP-001` and all existing hardware gaps remain open, no new gap is triggered, and added Driver/HAL development remains zero. Req IDs: `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-001`, `NV-F-011`, `NV-G-003`, `NV-P-002`, `KH-003`, `KH-006`, `DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

### B3 Black-Box Preflight Driver/HAL Result

B3 reads public Android build/package properties and the installed application APK only. The Java probe uses `PackageManager`, `Build` and `Process`; the host tool uses read-only `getprop`, `pm path`, `dumpsys package`, APK pull and signer verification. It does not enumerate vendor services or device nodes, call ioctl/sysfs/VHAL, change SELinux, elevate privilege or write a partition.

Observed SELinux/verified-boot values are evidence fields, not a request to alter policy. The API 33 emulator result closes no Driver/HAL gap. `DRV-GAP-001` and all target hardware gaps remain open; added Driver/HAL development is zero. Req IDs: `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-001`, `NV-F-011`, `NV-G-005`, `NV-P-002`, `KH-003`, `KH-006`, `DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

### B4 Hybrid Delivery Driver/HAL Result

B4 packages and inspects userspace AAR/APK files only. The two native artifacts contain the same B1 lifecycle library; manifest `current_native_lifecycle_code_present=true` is paired with `vendor_npu_adapter_present=false`. Package verification reads ZIP/ELF/package/signer metadata and installer uses ordinary `adb install -r` after public package signer preflight.

No kernel module, HAL, VHAL, PCIe enumeration, device node, ioctl/sysfs, DMA/shared memory, Safety Runtime or vendor service is added or called. `DRV-GAP-001` remains open and added Driver/HAL development is zero. Hybrid package readiness cannot close physical NPU/vehicle/hardware evidence. Req IDs: `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-001`, `NV-F-011`, `NV-G-005`, `NV-P-002`, `KH-003`, `KH-006`, `DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

### B5 GitHub Remote Test Driver/HAL Result

B5 adds only release identity, host-side ADB orchestration, redacted evidence packaging and GitHub Issue
contracts. Target testers invoke the existing public application/package/Binder/logcat/dumpsys surfaces;
GitHub never connects to a device and the collector has no root/remount/fastboot/system-write path.

No kernel module, HAL, VHAL, PCIe enumeration, device node, ioctl/sysfs, DMA/shared memory, Safety
Runtime or vendor service is added or called. Raw target evidence remains local pending owner review.
`DRV-GAP-001` and every existing gap remain open, added Driver/HAL development is zero, and
`hardware_accessed=false`/`target_hardware_validated=false` remain required until separately reviewed
target evidence exists. Req IDs: `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-001`, `NV-F-012`,
`NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

The Private CougarOS repository, immutable RC2 release and 15-minute Issue polling change only the
host-side handoff path. GitHub/`gh` activation does not add a target service, socket, Binder, JNI, HAL or
driver call; Driver/HAL added development remains zero.

### 2026-07-14 Physical Android 13 Application Test Driver/HAL Result

The physical-target test used public adb, PackageManager, Binder, Room, app-private storage, Runtime
dumpsys and the packaged userspace C ABI only. Windows adb CRLF normalization and caller-selected ADB
propagation are host test-tool fixes; they do not add a device API, JNI entry, HAL service or driver call.

The target confirmed Android 13/API 33, arm64-v8a and ordinary `/data/app` execution for Runtime, Demo
and the replacement Client2 debug package.
This closes no Driver/HAL gap. No private vendor service or device node was enumerated; NPU, VHAL,
vehicle bus, DMA/shared memory, Safety Runtime and hardware metrics were not accessed. The Client2
signer mismatch was handled as an explicitly authorized application-package remove/install migration;
signature permission, Binder/UI and RenderService rendering then passed. This remains an application
signing/upgrade decision and triggers no Driver/HAL work unless a published target contract later proves
a lower-layer gap.

`DRV-GAP-001` remains Open and added Driver/HAL development remains zero. The accepted state is
`physical_controller_application_evidence_available=true`, `target_hardware_validated=false`,
`hardware_accessed=false`, `driver_development_triggered=false` and
`virtualization_development_triggered=false`. Req IDs: `APP-004`, `XSC-004`, `XSC-005`, `XSC-006`,
`NV-F-001`, `NV-F-011`, `NV-F-012`, `NV-G-005`, `NV-P-002`, `KH-003`, `KH-006`,
`DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

### 2026-07-15 Client2 Navigation Menu Driver/HAL Result

The navigation-menu increment changes only the isolated Client2 Android resource/Smali patch. It uses
ordinary `FrameLayout`/`View` visibility and click listeners plus UIAutomator/ADB acceptance. The transparent
touch target maps a Tuanjie-rendered bottom navigation location; it does not call or alter RenderService,
Unity/Tuanjie assets, a vendor service, VHAL, NPU or system UI.

No C/C++, JNI, Driver/HAL ABI, device node, ioctl/sysfs, PCIe, shared memory, vehicle bus, Safety Runtime,
system/vendor partition or virtualization API is added. The geometry dependency is an application/HMI
integration deviation under `DEV-017`/`ISSUE-019`, not evidence of a Driver/HAL gap. Existing DRV-GAP items
remain unchanged and added Driver/HAL development is zero. Req IDs: `APP-004`, `XSC-001`, `XSC-005`,
`XSC-006`, `NV-G-006`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`.

### 2026-07-16 GitHub Source-Of-Truth And Homepage Result

This increment changes repository governance, the root README architecture/progress presentation,
pre-push checks and GitHub Actions path coverage. It tracks maintained Java/AIDL/C/JNI source,
Client2 patch source, contracts, tools and engineering documents; it neither packages nor invokes a
target-side runtime.

No Binder/AIDL/C ABI, JNI entrypoint, Vendor NPU provider, VHAL service, device node, ioctl/sysfs,
PCIe, DMA/IOMMU, shared memory, vehicle bus, Safety Runtime, system/vendor partition or virtualization
interface is added or changed. No Driver/HAL gap is closed or triggered and added Driver/HAL work is
zero. Req IDs: `APP-004`, `XSC-001`, `XSC-004`, `XSC-005`, `XSC-006`, `NV-G-007`, `DEL-001`,
`DEL-003`, `DEL-004`, `DEL-005`.

### P1-W03 Event Contract Driver/HAL Boundary

P1-W03 adds application structured AIDL carriers, Java validation, checksum/static guards and Parcel tests.
The Event page contains bounded IDs, enums, timestamps, display text and SHA-256 digests only. It carries no
FD, SharedMemory, DMA buffer, raw vehicle/model payload, service handle or caller-supplied Safety authority.
The independent Event/callback interfaces are contract-only and have no Android Service owner in this increment.

No Binder publication, Room write, VHAL/CarProperty call, vendor service, JNI/C/C++ path, NPU runtime, PCIe,
device node, ioctl/sysfs, shared memory, vehicle bus, Safety Runtime, system/vendor partition or virtualization
interface is added or accessed. Existing `DRV-GAP-*` entries remain unchanged; added Driver/HAL work is zero and
`driver_development_triggered=false`. A later target API gap may be opened only after a published interface,
owner, permission and minimum missing capability are evidenced. Req IDs: `S2-SES-001`, `S2-EVT-001`,
`FW-U-003`, `NV-F-009`, `NV-G-003`, `NV-G-007`, `KH-003`, `KH-006`, `DEL-004`, `DEL-005`.

### P1-W04 Effect/Approval Contract Driver/HAL Boundary

P1-W04 只在 Android SDK AAR 中新增四个 app-layer structured parcelable、纯 Java 状态/绑定 validator、
checksum/static guards 和 Parcel tests。`EffectIntent` 传递 canonical ID、digest、typed scalar、deadline、
verification 和 compensation metadata；`EffectObservation` 传递状态、attempt、reported/evidence digest；
Approval/Undo 只传递有界 plan/action/context/policy/verified-observation 绑定和 TTL。

合同不包含 Binder Service/interface、approval grant、undo executor、Room 写入、FD/SharedMemory、原始
车辆/模型 payload、vehicle property、CAN/DBC、Vendor NPU handle、DMA/IOMMU、device node 或 ioctl。
Android 13/API 33 ARM64 instrumentation 只验证 Parcel 和纯合同拒绝路径，报告
`hardware_accessed=false` 并卸载临时 test APK。

状态：`effect_contract_v1_defined=true`、
`effect_parcel_physical_android13_arm64_verified=true`、`effect_runtime_service_published=false`、
`approval_response_service_published=false`、`undo_service_published=false`、
`driver_development_triggered=false`。本包没有发现新的公开 Android/Vendor SDK 能力缺口，
`DRV-GAP-001..005` 保持原状态；真实 Effect dispatch/readback、Safety authority 和 adapter 仍由 P8 与
既有 gap gate 约束。Req IDs：`S2-EFF-001`、`S2-SAF-001`、`NV-F-001`、`NV-G-005..007`、
`KH-003`、`KH-006`、`DEL-004`、`DEL-005`。

### P1-W05 SDK Facade Driver/HAL Boundary

P1-W05 只增加 Android 应用层 Java facade、app-layer Binder publication、Binder identity/capability、
进程内 transient Session/Event registry 与 callback lifecycle。同一 `CentralBrainRuntimeService` 按显式
action 返回已冻结的 Session/Event V1 Binder；Manifest 仍只有 Runtime/Governance/Diagnostic 三个
signature-protected app Service，没有新增 system/vendor/hal service。

Android 13/API 33 ARM64 instrumentation 通过普通 `/data/app` signature permission 完成 open、event
replay、连续 6 次健康 reconnect/active resubscribe、旧 callback 对称注销、cancel、close 和 Runtime
process-death recovery；调用链没有进入 JNI/C ABI、Vehicle/VHAL、
Vendor NPU、PCIe、DMA/IOMMU、SharedMemory、device node、ioctl/sysfs、CAN/DBC 或 Safety Runtime，报告
`hardware_accessed=false`。

当前 `session_runtime_service_published=true` 和 `event_callback_service_published=true` 只表示应用层
Binder 可用，不表示 Driver/HAL 或 production Event broker。P1-W05 初始交付时
`session_runtime_persistence_wired=false`、`session_runtime_process_death_rehydration=false`；P1-W06 后
两者已变为 true。
`scenario_execution_enabled=false`；Effect/approval
response/undo/vehicle adapter 均未发布。新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变，
`driver_development_triggered=false`、`virtualization_development_triggered=false`。

### P1-W06 Room v4 Driver/HAL Boundary

P1-W06 只修改 Android `/data/data/com.centralbrain.runtime/databases/` 下的应用私有 Room schema、Java
repository 和 app-layer Binder recovery。新增 `sessions/plans/plan_nodes/runtime_events/
effect_observations/compensations` 六类表；不打开 `/dev`、sysfs、VHAL、CarProperty、厂商 Service、
PCIe/NPU、共享内存或跨 SOC transport。

Android 13/API 33 ARM64 设备验证中的“进程死亡”只通过 debug-only、DUMP-protected receiver 杀死
Runtime app process，再由显式 Binder bind 重启；不是 ECU reset、kernel driver reset、NPU reset 或
跨域 failover。Room 恢复的是 Session/Event metadata 和 digest，不恢复硬件句柄、Binder callback、
native pointer、车辆 payload 或签名材料。

当前声明：`room_schema_version=4`、`session_runtime_persistence_wired=true`、
`session_runtime_process_death_rehydration=true`，但 `scenario_execution_enabled=false`、
`effect_runtime_service_published=false`、`hardware_accessed=false`。因此新增 Driver/HAL 开发量仍为 0，
`DRV-GAP-001..005` 不变，`driver_development_triggered=false`、
`virtualization_development_triggered=false`。

Req IDs：`S2-SES-001`、`S2-UX-001..003`、`S2-EVT-001`、`APP-004`、`XSC-001/006`、
`NV-G-003/004`、`KH-003/006`、`DEL-004/005`。

### P1-W07 Runtime Contract v2 Aggregate Driver/HAL Boundary

P1-W07 只增加 JSON/Java/JVM/static aggregate contract，读取已有 AIDL source/hash、Room schema、SDK test
source 和 capability XML。它不调用 ADB、JNI/C ABI、CarProperty/VHAL、Vendor Service、NPU/PCIe、DMA/
IOMMU、device node、ioctl/sysfs、CAN/DBC 或 Safety Runtime。

Event V2 cursor/ACK 仅完成独立 wire 演进决策，未发布 AIDL/Service/capability/Room ACK repository；因此
不触发 Driver/HAL。Binder latency 值是应用层预算，不是 ECU、总线、NPU 或跨 SoC 性能证据。

状态：`runtime_contract_v2_verified=true`、`event_v2_interface_published=false`、
`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。

### P2-W01 Vehicle Signal Driver/HAL Boundary

P2-W01 只新增纯 Java canonical schema、JVM test、debug-only Activity 和静态 checker。12 项 path 是
Runtime 内部 VSS-style 语义，不包含 `VehiclePropertyIds`、CarPropertyManager、vendor Binder/SOA、
CAN/DBC、device node、ioctl/sysfs 或 Driver/HAL ABI。探针只构造 `SIMULATED` value。

`SignalSource.AAOS/VENDOR` 是 provenance enum，不触发发现、连接、权限、readback 或授权。生产
Runtime/Governance Service 均不得引用 schema provider；真实映射继续由 `ISSUE-030`/P8 等待目标平台
SDK、service owner、权限与 area contract。

状态：`vehicle_signal_schema_defined=true`、`vehicle_signal_provider_wired=false`、
`vehicle_property_mapping_configured=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量
为 0，`DRV-GAP-001..005` 不变。Req IDs：`S2-CTX-001`、`S2-TWN-001`、`KH-003/006`、`DEL-004/005`。

### P2-W02 Capability Catalog Driver/HAL Boundary

P2-W02 只新增纯 Java capability metadata、range validator、JVM test、debug-only Activity 和静态 checker。
它复用 canonical `VehicleSignalPath`，但不引用 `VehiclePropertyIds`、CarPropertyManager、vendor Binder/
SOA、CAN/DBC、device node、ioctl/sysfs、JNI/C ABI 或 Driver/HAL。

`productionAvailable=false` 与 `productionAuthorized=false` 明确阻止 catalog 被当作 hardware discovery 或
activation evidence。Target range、risk 和 dependency 只服务 debug/test Twin 与后续 Plan validation，
不能替代 OEM 标定、Safety authority 或硬联锁。

状态：`vehicle_capability_catalog_defined=true`、`vehicle_production_capability_authorized_count=0`、
`vehicle_capability_adapter_registry_wired=false`、`vehicle_property_mapping_configured=false`、
`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。
Req IDs：`S2-TWN-001`、`S2-ADP-001`、`KH-003/006`、`DEL-004/005`。

### P2-W03 Digital Twin Driver/HAL Boundary

P2-W03 只新增纯 Java进程内 state store、JVM test、debug-only Activity、installer marker 和静态 checker。
它消费 P2-W01 的 typed value，不引用 `VehiclePropertyIds`、CarPropertyManager、vendor Binder/SOA、
CAN/DBC、device node、ioctl/sysfs、JNI/C ABI、PCIe/NPU 或 Driver/HAL。

`SignalSource.SIMULATED` 只用于 software probe；desired state 不是 command，reported state 不是由真实
硬件 readback 提供，reconciliation match 也不是 verified Effect evidence。Store 不发现或激活 adapter，
不创建 property mapping，也不要求修改已刷机 Android 13 系统。

状态：`vehicle_digital_twin_store_defined=true`、
`vehicle_digital_twin_persistence_wired=false`、`vehicle_digital_twin_adapter_wired=false`、
`vehicle_property_mapping_configured=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量
为 0，`DRV-GAP-001..005` 不变。Req IDs：`S2-TWN-001`、`KH-003/006`、`DEL-004/005`。

### P2-W04 Context Snapshot Driver/HAL Boundary

P2-W04 只新增纯 Java in-process policy/builder/value objects、JVM test、debug-only Activity、installer marker
和静态 checker。它读取已捕获的 `DigitalTwinSnapshot` 与 Runtime-owned Safety state object，不发现或调用
CarProperty/VHAL、vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、JNI/C ABI、PCIe/NPU 或
Driver/HAL。

`SourceMode.PLATFORM_UNVERIFIED` 与 `SignalSource.AAOS/VENDOR` 不表示实际 platform connection；Context
强制 `productionTrusted=false`。Runtime motion/source assurance 仅作为 typed test input，不建立 Safety
authority。P8 只有在提供 API/permission/property/area/readback evidence 后才能新增独立 activation mapping。

状态：`context_snapshot_defined=true`、`context_snapshot_production_trusted=false`、
`context_snapshot_production_wired=false`、`vehicle_signal_provider_wired=false`、
`vehicle_property_mapping_configured=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量
为 0，`DRV-GAP-001..005` 不变。Req IDs：`S2-CTX-001`、`S2-SAF-001`、`KH-003/006`、
`DEL-004/005`。

### P2-W05 Scenario Manifest Driver/HAL Boundary

P2-W05 只新增 APK assets、Gson-based pure-Java parser/catalog、JVM test、debug-only Activity、installer
marker 和静态 checker。Manifest 内容是 canonical Context/capability/node/policy metadata，不包含 target
hardware property、CAN/DBC、FD/SharedMemory、Vendor NPU handle、device node、ioctl/sysfs 或 Driver/HAL ABI。

Android 13/API 33 ARM64 probe 只从本 APK `assets/scenarios` 读取三份 build-owned JSON/schema/checksum，
验证 parser/catalog 拒绝路径。它不读取 Vehicle/VHAL/vendor Service、不调用 JNI/C ABI/NPU/PCIe、
不映射 DMA/IOMMU、不接 Safety Runtime，也不 dispatch Effect。Manifest 中 AAOS/vehicle capability 名称是
内部语义引用，不是 property discovery 或 hardware authorization。

状态：`scenario_manifest_schema_version=1`、`scenario_catalog_count=3`、
`scenario_manifest_artifact_crypto_verified=false`、`scenario_catalog_production_trusted=false`、
`scenario_runtime_wired=false`、`scenario_graph_execution_enabled=false`、`effect_dispatch_enabled=false`、
`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。
Req IDs：`S2-SCN-001`、`S2-SAF-001`、`KH-003/006`、`DEL-004/005`。

### P2-W06 Scenario Resolver Driver/HAL Boundary

P2-W06 只新增纯 Java in-process request/capability snapshot/resolver/resolution、JVM test、debug-only Activity、
installer marker 和静态 checker。它只读取 P2-W05 的 build-owned manifest、P2-W04 的 immutable Context 和
P2-W02 的 capability metadata，不发现或调用 CarProperty/VHAL、vendor Binder/SOA、CAN/DBC、device node、
ioctl/sysfs、JNI/C ABI、PCIe/NPU 或 Driver/HAL。

`SOFTWARE_SIMULATION` 只是显式 debug/test availability profile；它不注册 adapter、不读写 Twin、不生成
Effect。`PRODUCTION` profile 需要 productionAvailable+productionAuthorized 以及 trusted Context/capability，
当前全部不满足，因此 fail closed。固定文本 alias 只选择 catalog ID，不调用本地/云/NPU 模型。

状态：`scenario_resolver_defined=true`、`scenario_resolver_model_invoked=false`、
`scenario_resolver_runtime_wired=false`、`scenario_compiler_wired=false`、
`scenario_graph_execution_enabled=false`、`effect_dispatch_enabled=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为
0，`DRV-GAP-001..005` 不变。Req IDs：`S2-SCN-001`、`S2-SAF-001`、`KH-003/006`、`DEL-004/005`。

### P2-W07 Scenario Plan Compiler Driver/HAL Boundary

P2-W07 只新增纯 Java in-process compiler、digest、semantic validator、JVM test、debug-only Activity、
installer marker 和静态 checker。输入只来自 immutable Resolution/Context/Capability/manifest；输出是
digest-only P1 Plan DTO deep copy，不包含 property ID、target scalar、adapter handle、FD/SharedMemory、
CAN/DBC、device node、ioctl/sysfs、JNI/C ABI、PCIe/NPU 或 Driver/HAL ABI。

Compiler 在软件中移除 optional unavailable/`PARKED_ONLY` branch，并拒绝 required branch、digest drift、
cycle、缺 verify、HIGH Effect 缺 approval 前驱和 non-parked driver recline。该校验不读取真实车辆状态，
不替代 OEM Safety authority，也不调用 Effect/Graph/Room/Session Service。Android 13 ARM64 probe 只构造
SIMULATED Context/capability 并验证 typed DAG 与拒绝路径。

状态：`scenario_plan_compiler_defined=true`、`scenario_plan_compiler_runtime_wired=false`、
`scenario_plan_runtime_published=false`、`scenario_graph_execution_enabled=false`、
`effect_dispatch_enabled=false`、`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。
Req IDs：`S2-SCN-001`、`S2-GRF-001`、`S2-SAF-001`、`KH-003/006`、`DEL-004/005`。

### P2-W08 Simulated Effect Adapter Driver/HAL Boundary

P2-W08 只在 Runtime `src/debug` 新增纯 Java `SimulatedEffectAdapter`、`SimulationClock`、
`FaultInjectionProfile`、JVM tests、debug-only Activity、installer marker 和静态 checker。main/release source
无同名实现，production Runtime/Governance Service 不引用、不发现、不注册这些类。

adapter 只处理 typed `EffectAdapter.Invocation` 元数据与 defensive-copy material，在进程内保存最多 128 条
debug record；不读取 CarProperty/VHAL/vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、JNI/C ABI、
PCIe/NPU 或 Driver/HAL。Fault delay/timeout 使用手动单调时钟；simulation readback 固定 source `SIMULATED`
且 `productionTrusted=false`，不能替代 OEM readback 或 Safety authority。

状态：`simulated_effect_adapter_base_defined=true`、`simulated_effect_adapter_debug_only=true`、
`simulated_effect_adapter_production_registered=false`、`simulated_effect_adapter_runtime_wired=false`、
`vehicle_signal_provider_wired=false`、`effect_dispatch_enabled=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为
0，`DRV-GAP-001..005` 不变。Req IDs：`S2-ADP-001`、`S2-EFF-001`、`KH-003/006`、`DEL-004/005`。

### P2-W09 Simulated HVAC Adapter Driver/HAL Boundary

P2-W09 只在 Runtime `src/debug` 新增纯 Java typed HVAC target/adapter、JVM tests、DUMP-protected probe、
installer marker 和 checker。它复用内部 capability 与 Digital Twin software contract，不发现或调用
CarProperty/VHAL/vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、JNI/C ABI、PCIe/NPU 或 Driver/HAL。

power/temperature/fan 的 area/range/step 是 Stage 2 debug contract，不是 OEM property ID、标定或授权。
reported 由 manual simulation clock 生成并固定 source SIMULATED、production trust false；timeout/failure
不生成假回读。production source/Service 不含或注册 adapter，真实 HVAC 缺失仍必须返回 unavailable。

状态：`simulated_hvac_adapter_defined=true`、`simulated_hvac_debug_only=true`、
`simulated_hvac_production_registered=false`、`simulated_hvac_runtime_wired=false`、
`vehicle_property_mapping_configured=false`、`vehicle_signal_provider_wired=false`、
`effect_dispatch_enabled=false`、`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。
Req IDs：`S2-ADP-001`、`S2-EFF-001`、`KH-003/006`、`DEL-004/005`。

### P2-W10 Simulated Seat Adapter Driver/HAL Boundary

P2-W10 只在 Runtime `src/debug` 新增纯 Java typed Seat target/adapter、simulation-only occupant/approval
provider、JVM tests、DUMP-protected probe、installer marker 和 checker。它复用内部 capability、Safety snapshot
和 Digital Twin software contract，不发现或调用 CarProperty/VHAL/vendor Binder/SOA、CAN/DBC、device node、
ioctl/sysfs、JNI/C ABI、PCIe/NPU 或 Driver/HAL。

heat/vent/recline 的 area/range/step 与 admission+dispatch safety check 是 Stage 2 debug contract，不是 OEM
seat property、occupant/belt ECU、Safety authority、approval grant 或硬联锁。MOVING/UNKNOWN/stale/belt change
等路径在软件层 fail closed；source SIMULATED progress/reported 不能替代实体座椅位置回读。

状态：`simulated_seat_adapter_defined=true`、`simulated_seat_debug_only=true`、
`simulated_seat_production_registered=false`、`simulated_seat_runtime_wired=false`、
`vehicle_property_mapping_configured=false`、`vehicle_signal_provider_wired=false`、
`effect_dispatch_enabled=false`、`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。
Req IDs：`S2-ADP-001`、`S2-SAF-001`、`KH-003/006`、`DEL-004/005`。

### P2-W11 Simulated Media/Navigation Adapter Driver/HAL Boundary

P2-W11 只在 Runtime `src/debug` 新增纯 Java typed Media/Navigation adapter、replaceable simulation backend、
JVM tests、DUMP-protected probe、installer marker 和 checker。Media backend 只接收 enum/revision/time；
Navigation backend 只接收 POI query SHA-256，不接收真实位置或 raw query。

本包不引用 Android MediaPlayer/MediaSession、Intent/startActivity、LocationManager/Fused Location、network、
vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、JNI/C ABI、PCIe/NPU 或 Driver/HAL。synthetic POI/route
ID、distance/duration 是 debug observation，不是地图匹配、真实导航规划或车辆 HMI 证据。

状态：`simulated_media_adapter_defined=true`、`simulated_navigation_adapter_defined=true`、
`simulated_media_nav_debug_only=true`、`simulated_media_nav_production_registered=false`、
`simulated_media_nav_runtime_wired=false`、`external_activity_started=false`、`location_uploaded=false`、
`network_accessed=false`、`effect_dispatch_enabled=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，
`DRV-GAP-001..005` 不变。Req IDs：`S2-ADP-001`、`KH-003/006`、`DEL-004/005`。

### P2-W12 Debug Simulation Controller Driver/HAL Boundary

P2-W12 只在 Runtime `src/debug` 新增 AIDL、Java Service/controller、signature/capability policy grant、JVM
tests、DUMP-protected probe、installer marker 和 checker。controller 只维护 process-memory simulated
driving/signal/fault/clock/audit，调用 P2-W09..W11 debug adapters 的 fault setter。

本包不发现或调用 Android Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、CAN/DBC、device node、
ioctl/sysfs、JNI/C ABI、PCIe/NPU 或 Driver/HAL；不推断 COM/USB/ADB 设备等于车辆服务可用。release APK 无
控制 permission/AIDL/Service/capability grant，production Runtime/Governance 不引用该类。

状态：`debug_simulation_controller_debug_only=true`、
`debug_simulation_controller_release_source_absent=true`、
`debug_simulation_controller_production_exported=false`、`debug_simulation_controller_runtime_wired=false`、
`vehicle_signal_provider_wired=false`、`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。
Req IDs：`S2-CTX-001`、`S2-ADP-001`、`KH-003/006`、`DEL-004/005`。

### P3-W01 Agent Graph Runtime Driver/HAL Boundary

P3-W01 只在 Android Runtime main source 新增纯 Java graph/node state reducer、control-only node-type registry、
JVM tests、DUMP-protected debug probe、installer marker 和 checker。它只消费 P1 typed Plan metadata/digest，
不读取 node input material，不创建线程池，不调用 executor、P2 simulated adapter/controller、Effect、Model、
Tool 或 Memory。

本包不发现或调用 Android Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、CAN/DBC、device node、
ioctl/sysfs、JNI/C ABI、PCIe/NPU 或 Driver/HAL。manual clock 只验证 plan deadline 与 monotonic event timestamp；
Android 13 ARM64 probe 只运行 process-local state machine，不构成 vehicle/NPU/driver evidence。

状态：`agent_graph_runtime_defined=true`、`agent_graph_executor_dispatch_enabled=false`、
`agent_graph_runtime_persistence_wired=false`、`agent_graph_runtime_binder_published=false`、
`agent_graph_runtime_production_wired=false`、`effect_dispatch_enabled=false`、`model_invoked=false`、
`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`。
新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。Req IDs：`S2-GRF-001`、`KH-003/006`、
`DEL-004/005`。

### P3-W02 Typed Node Executor Driver/HAL Boundary

P3-W02 在 Runtime main source 只新增 immutable Java contract/schema/registry validation，在 `src/debug` 新增
deterministic executor/harness/JVM probe。input/output 只携带 ID、enum、count 和 digest，不接受 vendor object、
property ID、CAN/DBC payload、device path 或 NPU buffer。

Effect/Compensation executor 固定 NOT_DISPATCHED；Model/Tool/Memory 无 executor。Graph、Runtime Service、
P2 simulated adapter/controller、JNI/C ABI 和 Native Runtime 均不引用本包执行器。本包不发现或调用 Android
Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、CAN、device node、ioctl/sysfs、PCIe/NPU 或 Driver/HAL。

状态：`typed_node_executor_contract_defined=true`、
`typed_node_executor_graph_dispatch_enabled=false`、`typed_node_executor_production_wired=false`、
`effect_dispatch_enabled=false`、`model_invoked=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，
`DRV-GAP-001..005` 不变。Req IDs：`S2-GRF-001`、`S2-EFF-001`、`KH-003/006`、`DEL-004/005`。

### P3-W03 CheckpointSerializer Driver/HAL Boundary

P3-W03 在 Runtime main source 只新增纯 Java immutable primitive tree、registered DTO codec、strict streaming
JSON parser 和 SHA-256 envelope；debug source 只新增 DUMP-protected probe。它处理 ID、enum、bounded number/
string/container 和 digest，不接受 Binder/Parcel object、fd/shared memory、file/device path、native pointer、
vehicle property、CAN/DBC material、Vendor NPU handle 或任意硬件 buffer。

Serializer 未接 `AgentGraphRuntime`、Room/SQLite、Session/Binder Service 或 restart recovery，也不调用 P2
simulation、Effect、Model、JNI/C ABI 和 Native Runtime。本包不发现或调用 Android Car/CarProperty、VHAL、
vendor Binder/SOA、device node、ioctl/sysfs、PCIe/NPU 或 Driver/HAL。

状态：`checkpoint_serializer_defined=true`、
`checkpoint_serializer_java_serialization_enabled=false`、
`agent_graph_runtime_persistence_wired=false`、`agent_graph_executor_dispatch_enabled=false`、
`effect_dispatch_enabled=false`、`model_invoked=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，
`DRV-GAP-001..005` 不变。Req IDs：`S2-GRF-001`、`NV-G-003/006/007`、`KH-003/006`、`DEL-004/005`。

### P3-W04 Retry/Timeout Driver/HAL Boundary

P3-W04 在 Runtime main source 只新增纯 Java `NodeRetryPolicy`、`NodeTimeoutPolicy` 和
`BackoffCalculator`；debug source 只新增 DUMP-protected probe。它只处理 typed Plan metadata、monotonic elapsed
time、attempt/reconcile enum 与 digest，不读取 wall clock/thread timer，也不持有 adapter/provider object。

Policy 未接 `AgentGraphRuntime`、Room/Binder、P2 simulation、production Effect/Model、JNI/C ABI 或 Native
Runtime。本包不发现或调用 Android Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、CAN/DBC、device node、
ioctl/sysfs、PCIe/NPU 或 Driver/HAL。API 33 ARM64 probe 只证明软件策略可执行，不是实时性或车辆副作用证据。

状态：`node_retry_policy_defined=true`、`node_timeout_policy_defined=true`、
`retry_timeout_policy_runtime_wired=false`、`agent_graph_executor_dispatch_enabled=false`、
`effect_dispatch_enabled=false`、`model_invoked=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，
`DRV-GAP-001..005` 不变。Req IDs：`S2-GRF-001`、`NV-G-004`、`KH-003/006`、`DEL-004/005`。

### P3-W05 Approval Interrupt Driver/HAL Boundary

P3-W05 在 Runtime main source 只新增 pure Java immutable approval record、状态转换器、checkpoint codec 与 resume
validator；debug source 只新增 DUMP-protected probe。它只处理 canonical ID、enum、boolean、caller-supplied time
和 SHA-256，不接收 authority token、raw Context/Safety/vehicle payload、Binder object、fd/shared memory、native
pointer、NPU handle 或硬件 buffer。

合同未接 `AgentGraphRuntime`、Room/Binder、P2 simulation、production Effect/Model、JNI/C ABI 或 Native Runtime。
本包不发现或调用 Android Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、
PCIe/NPU 或 Driver/HAL。Safety State 是 caller 提供的 trusted digest contract，不是 OEM Safety authority 证据。

状态：`approval_interrupt_record_defined=true`、`approval_interrupt_persistence_wired=false`、
`approval_grant_service_published=false`、`agent_graph_executor_dispatch_enabled=false`、
`effect_dispatch_enabled=false`、`model_invoked=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，
`DRV-GAP-001..005` 不变。Req IDs：`S2-SAF-001`、`S2-UX-003`、`S2-GRF-001`、`NV-G-005/006/007`、
`KH-003/006`、`DEL-004/005`。

### P3-W06 EffectCoordinator Driver/HAL Boundary

P3-W06 在 Runtime main source 只新增 pure Java batch/dependency/registry/coordinator 合同；debug source 只新增
DUMP-protected API 33 probe。它接收 P1 typed Effect metadata、caller-supplied epoch、transient canonical bytes 和
SHA-256，不发现设备、不查询 Android system service，也不定义 OEM property ID/area/permission。

`AdapterRegistry` 的 production profile 是空接口门禁，不是 Driver/HAL 实现。当前没有 production registration，
不存在 simulation fallback；probe 中的 nested fake adapter 只在 debug source。Coordinator 未接 P2 simulation、
Graph/Room/Binder、JNI/C ABI 或 Native Runtime，不调用 Android Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、
CAN/DBC、device node、ioctl/sysfs、PCIe/NPU。before-state 只是 fake preparation 提供的 digest，不是车辆 readback。

状态：`effect_coordinator_graph_wired=false`、`effect_coordinator_persistence_wired=false`、
`production_effect_adapter_registered=false`、`production_effect_dispatch_enabled=false`、
`effect_verification_reconciliation_wired=false`、`model_invoked=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，
`DRV-GAP-001..005` 不变。Req IDs：`S2-EFF-001`、`S2-SAF-001`、`NV-G-005/006/007`、
`KH-003/006`、`DEL-004/005`。

### P3-W07 Effect Verification Driver/HAL Boundary

P3-W07 在 Runtime main source 只新增 pure Java typed verifier 与 process-local Twin reconciler；debug source 只新增
DUMP-protected API 33 probe。Verifier 处理 P1 metadata、bounded typed scalar、SHA-256 与 caller-supplied epoch；
Reconciler 只调用既有 safe adapter 的 `queryStatus` 并读取传入的 immutable `DigitalTwinSnapshot`，源码禁止调用
`apply`，也不创建 timer/thread。

当前 Twin 只有 SIMULATED debug/test provenance，不读取 Android Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、
CAN/DBC、device node、ioctl/sysfs、PCIe/NPU。PRODUCTION profile 在 adapter query 前返回
`PRODUCTION_READBACK_UNAVAILABLE`，不会将 debug Twin 当量产 readback。没有新增 property ID、area mapping、
permission、JNI/C ABI、fd/shared memory 或 Driver/HAL 合同。

状态：`effect_verifier_defined=true`、`effect_verification_reconciliation_runtime_wired=false`、
`effect_verification_scheduler_wired=false`、`effect_verification_persistence_wired=false`、
`effect_verification_production_readback_wired=false`、`effect_verification_graph_wired=false`、
`production_effect_dispatch_enabled=false`、`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。Req IDs：
`S2-EFF-001`、`S2-TWN-001`、`NV-G-005/006/007`、`KH-003/006`、`DEL-004/005`。

### P3-W08 Compensation/Undo Driver/HAL Boundary

P3-W08 在 Runtime main source 只新增 pure Java planner/admission；debug source 只新增 DUMP-protected API 33
probe。`BeforeSnapshot` 接收调用方提供的 immutable `SignalValue`、Context digest/version 与 capture time，处理
typed scalar、枚举、ID、TTL 和 SHA-256，不读取或发现 Android/Vendor service。`UndoService` 名称表示领域职责，
它不继承 Android `Service`、不发布 Binder、AIDL 或 permission。

Planner 只构造新的 absolute compensation Effect plan；Undo admission 只返回新的 governed task metadata，源码不
调用 adapter `apply`/`compensate`。PRODUCTION profile 固定 `PRODUCTION_COMPENSATION_UNAVAILABLE`，因此 debug
SIMULATED before state、caller Governance/Safety boolean 或 API 33 probe 均不能被解释为量产 rollback/readback 证据。

本包不调用 Android Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、JNI/C
ABI、PCIe/NPU、fd/shared memory 或 Driver/HAL；没有新增 property ID、area mapping 或 permission。状态：
`compensation_undo_runtime_wired=false`、`compensation_undo_persistence_wired=false`、
`undo_binder_service_published=false`、`compensation_dispatch_enabled=false`、
`production_compensation_authority_wired=false`、`effect_dispatch_enabled=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，
`DRV-GAP-001..005` 不变。Req IDs：`S2-EFF-001`、`S2-UX-003`、`S2-SAF-001`、
`NV-G-005/006/007`、`KH-003/006`、`DEL-004/005`。

### P3-W09 Restart recovery Driver/HAL Boundary

P3-W09 的 main source 只新增纯 Java reducer 和 Room v4 repository。Reducer 处理 canonical ID、显式 Graph/Node state、
checkpoint/effect status enum、caller-supplied epoch 和 SHA-256；Repository 处理已存在的 SQLite entity 与 digest-only
audit。Debug source 只新增 DUMP-protected process-death Activity。

本包不发现或调用 Android Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、
JNI/C ABI、PCIe/NPU、fd/shared memory 或 Driver/HAL。它不新增 OEM property ID、area mapping、permission、DMA/IOMMU
合同，也不读取车辆或 NPU buffer。UNKNOWN Effect 只生成 reconcile directive，绝不 dispatch。

状态：`graph_restart_repository_implementation_available=true`、`graph_restart_runtime_wired=false`、
`graph_restart_binder_published=false`、`graph_restart_executor_dispatch_enabled=false`、
`graph_restart_effect_dispatch_enabled=false`、`production_effect_dispatch_enabled=false`、
`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`。
新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。真实 Effect readback/retry/rollback 仍需 P8 由目标平台 owner
提供 API、permission、Safety authority 和证据。Req IDs：`S2-SES-001`、`S2-GRF-001`、`S2-EFF-001`、
`NV-G-005/006/007`、`KH-003/006`、`DEL-004/005`。

### P4-W01 Client2 Session/Event bridge Driver/HAL Boundary

本包只修改 Client2 secondary-dex Java bridge、callback contract、APK acceptance scripts 和文档。Client2 通过已发布
Session/Event Binder action 与 Runtime 通信；canonical alias 映射、UUID/deadline 构造、snapshot/event/replay 投影
均为应用层逻辑。

本包不调用 Android Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、JNI/C
ABI、PCIe/NPU、fd/shared memory 或 Driver/HAL；不新增 property ID、area mapping、车辆 permission、DMA/IOMMU 或
model buffer contract。Runtime 当前只持久化 Session/Event admission，不执行 scenario/Graph/Effect，因此 UI 摘要不能
解释为车控结果。

状态：`client2_session_event_primary_api=true`、`client2_session_android13_arm64_verified=true`、
`scenario_execution_enabled=false`、`service_dispatch_triggered=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，
`DRV-GAP-001..005` 不变。P4-W02 只处理 HMI state/lifecycle；真实 HVAC/Seat adapter 仍由 P8 和 `ISSUE-030` 关闭。
Req IDs：`S2-UX-001`、`S2-HMI-005`、`XSC-001/005/006`、`KH-003/006`、`DEL-004/005`。

### P4-W02 Cockpit HMI state/reducer/reconnect Driver/HAL Boundary

本包只修改 Client2 application secondary-dex、MainActivity 单行 bootstrap、app-private checkpoint、host/device test 和
文档。Reducer 处理 bounded primitive、SessionHandle metadata、event sequence 和 opaque cursor；Coordinator 使用普通
Android View、ActivityLifecycleCallbacks、SharedPreferences 和已发布 Session/Event SDK。

本包不调用 Android Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、JNI/C ABI、
PCIe/NPU、fd/shared memory、Safety Runtime 或 Driver/HAL；不新增 property ID、area mapping、车辆 permission、
DMA/IOMMU 或 model buffer contract。Private checkpoint 不是硬件存储接口，也不触发新 Driver work。

状态：`cockpit_hmi_state_reducer_implemented=true`、`cockpit_hmi_lifecycle_owner_java=true`、
`client2_hmi_checkpoint_text_persisted=false`、`scenario_execution_enabled=false`、`service_dispatch_triggered=false`、
`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`。
新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。P4-W03 仍是应用 UI；真实 HVAC/Seat adapter 由 P8/
`ISSUE-030` 关闭。Req IDs：`S2-UX-001..003`、`S2-HMI-003/005/006`、`XSC-001/005/006`、
`KH-003/006`、`DEL-004/005`。

### P4-W03 Intent-first four-stage shell Driver/HAL Boundary

本包只修改 Client2 application XML/vector resources、secondary-dex Java HMI state/reducer/coordinator、host checks、ADB
acceptance 和文档。四阶段、Header、source/driving unavailable 投影、device drawer、safe frame 和 alpha 均为普通 Android
View/resource 行为；Session 仍只进入已发布 app-layer SDK/Binder admission。

本包不调用 Android Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、JNI/C ABI、
PCIe/NPU、fd/shared memory、Safety Runtime 或 Driver/HAL；不新增 property ID、area mapping、车辆 permission、DMA/IOMMU、
model buffer 或 readback contract。`UNAVAILABLE`/`NOT DISPATCHED` 是缺口投影，不是模拟硬件结果。

状态：`cockpit_hmi_four_stage_shell_implemented=true`、`cockpit_hmi_safe_frame_1920x1080_verified=true`、
`cockpit_hmi_device_drawer_scaffolded=true`、`cockpit_hvac_surface_implemented=false`、
`cockpit_seat_surface_implemented=false`、`scenario_execution_enabled=false`、`service_dispatch_triggered=false`、
`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`。
新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。P4-W04/P4-W05 仍只能通过受治理 typed target；真实
HVAC/Seat adapter 由 P8/`ISSUE-030` 关闭。Req IDs：`S2-UX-001..003`、`S2-HMI-001..003/006`、
`XSC-001/005/006`、`KH-003/006`、`DEL-004/005`。

### P4-W07 Approval/recovery UX Driver/HAL Boundary

本包只新增 Client2 application XML、纯 Java immutable `CockpitRecoveryState`、HMI reducer/coordinator projection、
host/static/ADB tests 和文档。它只消费 app-layer `SessionSnapshot/RuntimeEvent`，不读取车辆信号、设备状态或 Driver 返回值。

本包不调用 Android Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、JNI/C ABI、
PCIe/NPU、fd/shared memory、Safety Runtime 或 Driver/HAL；不发布 approval/retry/undo service，不创建 `UndoHandle`，不执行
compensation，不注册 Adapter，不触发 Effect dispatch。disabled controls 是接口缺口呈现，不是 Driver 能力探测。

状态：`cockpit_recovery_state_reducer_owned=true`、`cockpit_approval_response_service_published=false`、
`cockpit_retry_service_published=false`、`cockpit_undo_service_published=false`、
`cockpit_recovery_commands_enabled=false`、`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变；真实 vehicle effect/
readback 和 Adapter 仍由 P8/`ISSUE-030` 关闭。Req IDs：`S2-UX-003`、`S2-HMI-003`、`S2-SAF-001`、
`S2-EFF-001`、`XSC-001/005/006`、`KH-003/006`、`DEL-004/005`。

### P4-W04 HVAC control surface Driver/HAL Boundary

本包只新增 Client2 application XML、纯 Java `HvacControlIntent/CockpitHvacState`、HMI reducer/coordinator、现有
Session/Event SDK bridge 的 bounded manual request、host/static/ADB tests 和文档。温度、风量、zone、mode 和 preset
只是应用层 desired contract，不是 OEM property ID、area mapping、标定或生产授权。

本包不调用 Android Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、JNI/C ABI、
PCIe/NPU、fd/shared memory、Safety Runtime 或 Driver/HAL；不注册 debug/production HVAC Adapter，不生成 reported/readback，
不触发 Effect dispatch。Session V1 `HVAC1` 兼容承载由 `DEV-054` 跟踪，不是 Driver 协议。

状态：`cockpit_hvac_surface_implemented=true`、`cockpit_hvac_governed_manual_session=true`、
`cockpit_hvac_reported_readback_available=false`、`hvac_manual_typed_parameter_field=false`、
`scenario_execution_enabled=false`、`production_effect_dispatch_enabled=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，
`DRV-GAP-001..005` 不变；真实 HVAC adapter/readback 仍由 P8/`ISSUE-030` 关闭。Req IDs：
`S2-HMI-001/003/004/005`、`S2-ADP-001`、`XSC-001/005/006`、`KH-003/006`、`DEL-004/005`。

### P4-W05 Seat control surface Driver/HAL Boundary

本包只新增 Client2 application XML、纯 Java `SeatControlIntent/CockpitSeatState`、HMI reducer/coordinator、现有
Session/Event SDK bridge 的 bounded manual request、host/static/ADB tests 和文档。四座区、heat/vent、massage、recline、
preset 和 UNKNOWN_RESTRICTED decision 都是应用层 desired/safety projection，不是 OEM property、标定或授权。

本包不调用 Android Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、JNI/C ABI、
PCIe/NPU、fd/shared memory、真实 Safety authority 或 Driver/HAL；不注册 Seat Adapter，不读取 occupancy/belt/gear/speed，
不生成 reported/readback，不触发 Effect dispatch。Session V1 `SEAT1`/approval 兼容承载由 `DEV-055` 跟踪，不是 Driver
协议。UI 的 BLOCKED/WAITING_APPROVAL 只用于失败关闭，不替代 dispatch 前 Runtime/Adapter 重验。

状态：`cockpit_seat_surface_implemented=true`、`cockpit_seat_governed_manual_session=true`、
`cockpit_seat_unknown_restricted_fail_closed=true`、`cockpit_seat_reported_readback_available=false`、
`seat_manual_typed_parameter_field=false`、`scenario_execution_enabled=false`、`production_effect_dispatch_enabled=false`、
`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增
Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变；真实 Seat adapter/Context/readback 仍由 P8/`ISSUE-029/030` 关闭。
Req IDs：`S2-HMI-002..005`、`S2-SAF-001`、`S2-ADP-001`、`XSC-001/005/006`、`KH-003/006`、`DEL-004/005`。

### P4-W06 Observable execution timeline Driver/HAL Boundary

本包只新增 Client2 application XML、纯 Java immutable `CockpitExecutionTimeline`、HMI reducer/coordinator projection、
host/static/ADB tests 和文档。它读取 app-layer `SessionSnapshot/RuntimeEvent`，不读取车辆信号或设备状态。

本包不调用 Android Car/CarProperty、Vehicle/VHAL、vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、JNI/C ABI、
PCIe/NPU、fd/shared memory、Safety Runtime 或 Driver/HAL；不注册 Adapter，不生成 observation，不触发 Effect dispatch。
Plan NOT PUBLISHED、Graph NOT WIRED、Effect NOT DISPATCHED、Readback UNAVAILABLE 是软件缺口投影，不是硬件结果。

状态：`cockpit_execution_timeline_implemented=true`、`cockpit_execution_typed_event_projection=true`、
`cockpit_execution_plan_published=false`、`cockpit_execution_effect_dispatch_enabled=false`、
`cockpit_execution_readback_available=false`、`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变；真实车辆 observation/
Adapter 仍由 P8/`ISSUE-030` 关闭。Req IDs：`S2-UX-001`、`S2-HMI-003/006`、`S2-EVT-001`、
`XSC-001/005/006`、`KH-003/006`、`DEL-004/005`。

### P4-W08 Driving restriction renderer Driver/HAL Boundary

本包只新增 Client2 application XML 和纯 Java immutable presentation policy/state/reducer/coordinator 行为。Driving input
只消费现有 typed `CockpitSeatState.SafetyContext`；不发现或读取 Android Car/CarProperty、Vehicle/VHAL、Vendor service、
CAN/DBC、Safety Runtime、device node、ioctl/sysfs、JNI/C ABI、PCIe/NPU、fd/shared memory 或任何 Driver/HAL 返回值。

UNKNOWN/MOVING/unavailable/untrusted 输入统一禁用参数编辑和高风险场景，是应用层失败关闭，不是车辆信号探测或硬联锁。
PARKED_FULL 也只改变呈现，`isEffectAuthorizationSource=false`，不能授权 Adapter 或 Effect。当前实体没有 production
Context provider，P4-W08 不添加伪 property、假 provider 或本地 PARKED fallback。

状态：`vehicle_signal_provider_wired=false`、`cockpit_driving_ux_policy_implemented=true`、
`cockpit_runtime_policy_authority_independent=true`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，
`DRV-GAP-001..005` 不变；真实 driving/Safety authority 仍由 P8 与 `ISSUE-023/029/030` 关闭。Req IDs：
`S2-UX-002`、`S2-HMI-002`、`S2-SAF-001`、`XSC-001/005/006`、`KH-003/006/007`、`DEL-004/005`。

### P4-W09 Engineer simulation drawer Driver/HAL Boundary

本包只增加 Client2 application XML、纯 Java immutable state/reducer、debug Binder client、AIDL 生成步骤和测试。Binder
目标是项目自有 Runtime debug Service，不是 Android Car、Vehicle/VHAL 或 vendor service。occupancy/belt canonical path、
adapter ID 和 fault profile 是 P2 debug contract，不映射 OEM property ID、CAN signal、area/permission 或真实设备状态。

本包不发现、不打开也不调用 CarPropertyManager、VHAL、vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、JNI/C ABI、
PCIe/NPU、fd/shared memory 或任何 Driver/HAL。Controller 写入隔离 debug store；Client2 只接收成功状态码和单调 revision。
PARKED/MOVING/UNKNOWN 与 SIMULATED source 只验证 HMI 呈现，不能授权 Adapter/Effect。release Runtime 无该 Service，生产
capability policy 无 debug grant。

状态：`cockpit_engineer_simulation_drawer_implemented=true`、
`cockpit_engineer_runtime_release_service_absent=true`、`cockpit_engineer_effect_authorization_source=false`、
`cockpit_engineer_production_available=false`、`vehicle_signal_provider_wired=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。新增 Driver/HAL 开发量为 0，
`DRV-GAP-001..005` 不变；真实 vehicle Context/Safety/Effect/readback 仍由 P8 与 `ISSUE-023/029/030` 关闭。
Req IDs：`S2-HMI-004`、`S2-ADP-001`、`S2-OBS-001`、`XSC-001/005/006`、`KH-003/006/007`、
`DEL-004/005`。

### P4-W10 Scenario/manual synchronization Driver/HAL boundary

本包只调整 Client2 Java immutable state/reducer、SDK interface dependency、layout 文本投影、host/static test 与 ADB UI 测试。
`CockpitScenarioControlState` 只保存固定 alias/canonical、catalog device role、Session lifecycle、Plan revision 和 event sequence；
不保存 property ID、CAN/DBC、area mapping、车辆 payload、fd、device node 或 vendor handle。

Bridge 通过 `ScenarioClient` 接口打开和观察 Session；Coordinator/Reducer 不 import、不发现也不调用 Adapter、Android Car、
CarPropertyManager、VHAL、Vendor Binder/SOA、JNI/C ABI、PCIe/NPU 或 Driver/HAL。P4-W10 新增 Driver/HAL 开发量为 0，
`DRV-GAP-001..005` 不变。真实 target、dispatch 与 readback 仍由 P8 和 `ISSUE-030` 关闭。

状态：`cockpit_scenario_manual_shared_client=true`、`cockpit_scenario_device_session_synchronized=true`、
`cockpit_scenario_effect_dispatch_enabled=false`、`cockpit_scenario_readback_available=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。Req IDs：`S2-HMI-001..006`、
`S2-SCN-001`、`XSC-001/005/006`、`KH-003/006/007`、`DEL-004/005`；tracking：`DEV-060`、`ISSUE-030/033`。

### P4-W11 Accessibility/display matrix Driver/HAL boundary

本包只增加 Client2 XML、pure Java `CockpitDisplayPolicy`、Coordinator accessibility 属性和 host/static/ADB UI 测试。
策略只读取 Android `DisplayMetrics` 与 `Configuration` 中的 width/height/density/fontScale/orientation，不发现车辆或
硬件能力，也不把屏幕 profile 写入 Runtime、Context、Policy、Safety、Effect 或 Adapter。

本包不调用 Android Car、CarPropertyManager、Vehicle/VHAL、Vendor Binder/SOA、CAN/DBC、device node、ioctl/sysfs、
JNI/C ABI、PCIe/NPU、fd/shared memory 或 Driver/HAL。unsupported profile 仅在应用层禁用导航入口；不触发硬件重配、
display driver 变更或系统属性写入。新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。

状态：`cockpit_display_matrix_defined=true`、`cockpit_touch_target_min_dp=48`、
`cockpit_accessibility_semantics_runtime_owned=true`、`cockpit_display_matrix_android13_arm64_verified=true`、
`cockpit_display_effect_authorization_source=false`、`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。Req IDs：`S2-UX-003`、`S2-HMI-001/002`、`XSC-001/005/006`、
`KH-003/006/007`、`DEL-004/005`；tracking：`DEV-061`、`ISSUE-019/033`。

### P4-W12 Aggregate acceptance Driver/HAL boundary

本包只编排已有 Android application/static/ADB 测试，读取 API level、ABI、package state、UI tree 和 Android crash buffer。
这些数据仅用于本地临时验收，不进入仓库；runner 不读取车辆信号、设备节点、NPU 状态或 vendor payload。

本包不调用 Android Car、CarPropertyManager、Vehicle/VHAL、Vendor Binder/SOA、CAN/DBC、ioctl/sysfs、JNI/C ABI、
PCIe/NPU、fd/shared memory 或 Driver/HAL。protected engineer fault 仍写入隔离 debug store；Runtime release absence 是
source/APK 验证，不是硬件探测。新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。

状态：`p4_w12_application_acceptance_complete=true`、`p4_android13_arm64_aggregate_verified=true`、
`p4_crash_buffer_clean=true`、`p4_automatic_plan_runtime_published=false`、
`p4_production_effect_dispatch_enabled=false`、`p4_vehicle_readback_available=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。Req IDs：`S2-UX-001..003`、
`S2-HMI-001..006`、`XSC-001/005/006`、`KH-003/006/007`、`DEL-004/005`；tracking：`DEV-062`、
`ISSUE-022/026/030/033`。

## P5-W01 Tool Manifest/Schema Driver/HAL Boundary

`ToolManifest.capabilityId` 是语义授权标签，不是 Android property ID、service name、device node、ioctl、DMA handle 或
Vendor SDK symbol。`HealthContract.checkId` 也是静态健康合同标识，不探测 PCIe/NPU/VHAL/Driver/HAL，不允许通过命名约定
猜测底层接口。

P5-W01 的 main source 只使用 Java value object、SHA-256 和现有 Plan timeout 上限；静态门禁禁止 Android Car、VHAL、
network、Room、reflection、serialization、`/dev`、sysfs 和 ioctl 引用。API 33 ARM64 probe 只执行 schema 正负例，
`vehicle_readback_accessed=false`、`npu_accessed=false`、`hardware_accessed=false`。

P5-W02/W03 的 Registry/Resolver/RuleSolver 仍不得触发硬件。P5-W04 即使增加 built-in ToolExecutor，也必须先经过
capability、Safety、deadline/cancel/audit 边界；真实车辆/NPU Tool 只有在 OEM/Vendor contract 明确 property/service/
permission/ABI/readback 后才能映射 Adapter。现阶段新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。

状态：`tool_manifest_contract_defined=true`、`tool_manifest_health_fail_closed=true`、
`tool_manifest_android13_arm64_verified=false`、`tool_registry_published=false`、
`tool_execution_enabled=false`、`production_tool_artifact_loaded=false`、
`vehicle_readback_accessed=false`、`npu_accessed=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。Req IDs：`S2-TOL-001`、
`S2-SAF-001`、`XSC-001/005/006`、`KH-003/006/007`、`DEL-004/005`；tracking：`DEV-063`、`ISSUE-036`。

## P5-W02 Tool Registry/Resolver Driver/HAL Boundary

`ToolRegistry` 只索引 P5-W01 family/version/digest；`ToolResolver` 只做 capability/digest/health eligibility。family、capability、
health check ID 均为 AIOS 语义标识，不得解释为 Android service、VHAL property、Vendor symbol、device node、ioctl、PCIe
function、DMA/IOMMU handle 或 Driver/HAL endpoint。

`ToolHealthSnapshot` 不收集硬件健康。它只消费调用方已经提供的 bounded observation，并使用 elapsed-realtime freshness
失败关闭。P5-W02 没有定义谁读取温度、PCIe link、NPU runtime、车辆 ECU 或 VHAL；这些 publisher 和信任 owner 仍由
`ISSUE-037`/P8 外部合同确认。缺少 publisher 时 Resolver 返回 NOT_USABLE，禁止构造 synthetic HEALTHY fallback。

main source 仅使用 Java collections、regex 和 SHA-256；静态门禁禁止 Binder、Room、network、Android Car/VHAL、reflection、
serialization、`/dev`、sysfs 和 ioctl。P5-W03 RuleSolver 同样不得触发硬件；P5-W04 Executor 也只能在明确 capability/Safety/
deadline/cancel/audit 边界后执行 signed built-in software Tool。真实 Vehicle/NPU Tool 必须等待 OEM/Vendor API、权限、
readback 和故障合同。当前新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。

状态：`tool_registry_contract_defined=true`、`tool_resolver_contract_defined=true`、
`tool_health_dynamic_snapshot_defined=true`、`tool_registry_android13_arm64_verified=false`、
`tool_registry_published=false`、`tool_registry_runtime_wired=false`、`tool_execution_enabled=false`、
`production_tool_registered=false`、`vehicle_readback_accessed=false`、`npu_accessed=false`、
`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`。
Req IDs：`S2-TOL-001`、`S2-SAF-001`、`XSC-001/005/006`、`KH-003/006/007`、`DEL-004/005`；
tracking：`DEV-064`、`ISSUE-037`。

## P5-W03 Tool RuleSolver Driver/HAL Boundary

`ToolRuleSet` 的 family、condition、child 和 terminal 是 AIOS workflow 标识，不是 Android Service、VHAL property、Vendor
symbol、device node、ioctl、PCIe function、DMA/IOMMU handle 或 Driver/HAL endpoint。`ConditionSnapshot` 是调用方提供的
bounded tri-state value；P5-W03 不读取 gear/speed/seat/HVAC/NPU/PCIe 状态，也不实现 condition publisher。

RuleSolver 只做集合缩减：静态 rule allowset 与 model-selected family、P5-W02 USABLE family 求交。它不调用模型、不查询
硬件 health、不写 desired state、不做 readback，也不把 requires-approval 变成授权。main source 只使用 Java collections、
regex 和 SHA-256；静态门禁禁止 Binder、Room、network、Android Car/VHAL、reflection、serialization、`/dev`、sysfs、ioctl。

当前新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。P5-W04 只能先实现 signed built-in software Tool executor
边界；Vehicle/NPU Tool 仍需 P8 确认 OEM/Vendor API、权限、Safety、readback、取消和故障合同后才能触发 Driver/HAL 缺口。

状态：`tool_rule_set_contract_defined=true`、`tool_rule_solver_android13_arm64_verified=false`、
`tool_rule_solver_published=false`、`tool_rule_solver_runtime_wired=false`、`tool_approval_authority_available=false`、
`tool_execution_enabled=false`、`vehicle_readback_accessed=false`、`model_invoked=false`、`npu_accessed=false`、
`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`、
`implementation_stage=P9-W03`。Req IDs：`S2-TOL-001`、`S2-SAF-001`、`XSC-001/005/006`、`KH-003/006/007`、
`DEL-004/005`；tracking：`DEV-065`、`ISSUE-038`。

## P5-W04 Tool Executor Driver/HAL Boundary

`ToolInvocationContext` 的 family/capability/digest 只属于 AIOS 软件语义；它们不是 Android Service、VHAL property、vendor
symbol、device node、ioctl、PCIe function、DMA/IOMMU handle 或 Driver/HAL endpoint。`InProcessBuiltInToolExecutor` 只调用
同 APK/JVM 已注册的 Java built-in，不探测或打开任何硬件接口。

签名 digest 在本阶段是受信构造输入，不读取 keystore、TEE、PackageManager signing history 或 vendor trust store。deadline/
cancel 是 Java cooperative checkpoint，不等同于中断 native ioctl、NPU queue 或车辆 service transaction。真实 Vehicle/NPU
Tool 必须在 P8 获得 OEM/Vendor capability、权限、area、readback、cancellation、fault 和 Safety contract 后，才能判断是否
触发 `DRV-GAP-001..005`；当前新增 Driver/HAL 开发量为 0。

静态门禁禁止 Android Car/VHAL、Binder、network、Room、reflection/dynamic class loading、subprocess、`/dev`、sysfs、ioctl。
不开发 Hypervisor 或 OS virtualization。状态：`tool_executor_contract_defined=true`、
`tool_executor_android13_arm64_verified=false`、`tool_executor_runtime_wired=false`、`tool_execution_enabled=false`、
`production_tool_execution_enabled=false`、`vehicle_readback_accessed=false`、`model_invoked=false`、`npu_accessed=false`、
`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`、
`implementation_stage=P9-W03`。Req IDs：`S2-TOL-001`、`S2-SAF-001`、`XSC-001/005/006`、`KH-003/006/007`、
`DEL-004/005`；tracking：`DEV-066`、`ISSUE-039`。

## P5-W05 Skill package verifier Driver/HAL Boundary

`SkillSignerPolicy` 的 signer digest/state/epoch、`SkillVersionPolicy` 的 semantic version/artifact epoch 和
`SkillArtifactVerifier` 的 manifest/artifact/capability 都是 AIOS 软件合同，不是 Android Service、VHAL property、vendor symbol、
device node、ioctl、PCIe function、DMA/IOMMU handle、NPU queue 或 Driver/HAL endpoint。

Verifier 不打开 APK/JAR/dex/certificate，不读取 PackageManager、keystore、TEE 或 vendor trust store，也不访问 file/network/
Binder/Room/Android Car/VHAL。observed signer 和 measured artifact digest 是调用方提供的静态证据；本阶段不能将其解释为
hardware-backed attestation。未来若 owner 明确要求 TEE/secure element attestation，只有公开 Android/target API 无法满足经
批准接口时，才登记新的最小 DRV-GAP。

当前新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变，不开发 vendor/AOSP/BSP 或虚拟化。状态：
`skill_artifact_verifier_contract_defined=true`、`trusted_skill_evidence_source_configured=false`、
`package_signature_cryptographically_verified=false`、`dynamic_skill_loading_enabled=false`、
`skill_execution_enabled=false`、`skill_package_verifier_runtime_wired=false`、`vehicle_readback_accessed=false`、
`model_invoked=false`、`npu_accessed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`、`implementation_stage=P9-W03`。Req IDs：`S2-TOL-001`、`S2-SAF-001`、
`FW-U-008`、`XSC-001/005/006`、`KH-003/006/007`、`DEL-004/005`；tracking：`DEV-067`、`ISSUE-040`。

## P5-W06 WorkingMemoryStore Driver/HAL Boundary

`WorkingMemoryStore` 的 owner/session/item/schema ID、opaque bytes、token count、TTL 和 quota 都是 Android application process
内的软件合同，不是 Android Service、VHAL property、vendor symbol、device node、ioctl、PCIe function、DMA/IOMMU handle、
NPU buffer/queue 或 Driver/HAL endpoint。

store 只使用 Java collections、byte array、SHA-256、elapsed clock 和 synchronized monitor。它不访问 Binder/Room/file/network/
Android Car/VHAL/ModelProvider/NPU，也不读取硬件时钟或系统内存。`Arrays.fill` 只覆零 JVM store-retained array，不是 secure
erase、DMA buffer wipe、TEE memory policy 或硬件证据。token count 由受信调用方提供，不触发 NPU tokenizer 接口开发。

当前新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变，不开发 vendor/AOSP/BSP 或虚拟化。未来 Working Memory 若需要
Vendor NPU shared buffer、secure memory 或跨进程零拷贝，必须先获得公开/vendor API、owner、安全与生命周期合同；只有确认
现有能力不足并批准最小缺口后才新增 DRV-GAP。

状态：`working_memory_store_defined=true`、`working_memory_android13_arm64_verified=false`、
`working_memory_process_local=true`、`working_memory_persistence_wired=false`、`working_memory_runtime_wired=false`、
`working_memory_model_context_published=false`、`working_memory_tokenizer_verified=false`、`model_invoked=false`、
`npu_accessed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`、`implementation_stage=P9-W03`。Req IDs：`S2-MEM-001`、`S2-SAF-001`、
`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007`、`DEL-004/005`；tracking：`DEV-068`、`ISSUE-041`。

## P5-W07 ProfileMemoryStore Driver/HAL Boundary

`ProfileMemoryStore` 的 owner fingerprint、user/seat scope、field/value、consent/authorization evidence、elapsed retention 与
`SealedPayload` 是 Android application software contract，不是 VHAL property、vendor service、device node、ioctl、PCIe/NPU
buffer、DMA/IOMMU handle 或 Driver/HAL endpoint。

main source 只使用 Java collections、UTF-8 byte array、elapsed clock 和 synchronized monitor；没有 Android Keystore/TEE、
Room/file/network/Android Car/VHAL/NPU 访问。`EncryptionOwner` 是应用层 owner gate interface，当前没有 vendor implementation。
debug/test XOR 只验证 state binding 和 ciphertext cleanup，不触发硬件加密、secure erase 或 BSP 开发。

当前新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变。未来 OEM 若要求 hardware-backed key、TEE sealed storage、跨 SoC
profile replication 或 secure memory，必须先取得公开/vendor SDK 的 key/storage/identity API 和证据；只有确认能力不足并批准
最小缺口后才登记新 DRV-GAP，不得由本合同猜测 vendor 接口。

状态：`profile_memory_store_defined=true`、`profile_memory_android13_arm64_verified=false`、
`profile_memory_process_local=true`、`profile_memory_durable_storage_wired=false`、
`profile_memory_production_encryption_owner_configured=false`、`profile_memory_consent_authority_production_wired=false`、
`profile_memory_runtime_wired=false`、`model_invoked=false`、`npu_accessed=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`、`implementation_stage=P9-W03`。
Req IDs：`S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007`、
`DEL-004/005`；tracking：`DEV-069`、`ISSUE-042`。

## P5-W08 EpisodicMemoryStore Driver/HAL Boundary

`EpisodicMemoryStore` 的 owner/episode、catalog digest、trigger/result/outcome enum、action count、elapsed retention 与 policy/read/
erase evidence 是 Android application software contract，不是 VHAL property、vendor service、device node、ioctl、PCIe/NPU buffer、
DMA/IOMMU handle 或 Driver/HAL endpoint。

main source 只使用 Java collections、SHA-256、elapsed clock 和 synchronized monitor；没有 byte payload、Android Car/VHAL、
Binder、Room/file/network、ModelProvider 或 NPU 访问。`ScenarioCatalogAuthority`、`StoragePolicyAuthority`、`ReadAuthority` 和 `EraseAuthority`
是应用层 owner gate interface，当前没有 vendor implementation，也不要求芯片厂商修改已刷写 Android 13 软件。

当前新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变，不开发 vendor/AOSP/BSP 或虚拟化。未来 OEM 若要求跨 SoC episode
replication、hardware-backed encrypted repository 或安全时钟，必须先取得公开/vendor SDK 的 storage/key/time API 和证据；只有
确认现有能力不足并批准最小缺口后才登记新 DRV-GAP，不得由本合同猜测 vendor 接口。

状态：`episodic_memory_store_defined=true`、`episodic_memory_android13_arm64_verified=false`、
`episodic_memory_process_local=true`、`episodic_memory_raw_continuous_signal_stored=false`、
`episodic_memory_read_fail_closed=true`、`episodic_memory_production_read_authority_wired=false`、
`episodic_memory_persistence_wired=false`、`episodic_memory_runtime_wired=false`、
`episodic_memory_model_context_published=false`、`model_invoked=false`、`npu_accessed=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`、`implementation_stage=P9-W03`。
Req IDs：`S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007`、
`DEL-004/005`；tracking：`DEV-070`、`ISSUE-043`。

## P5-W09 ContextBudgetManager Driver/HAL Boundary

`ContextBudgetManager` 的 category、canonical descriptor ID、token/byte size、required/summary flag、priority、budget envelope 与
handling directive 是 Android application software contract，不是 VHAL property、vendor model API、NPU tensor/buffer、device node、
ioctl、PCIe transport、DMA/IOMMU handle 或 Driver/HAL endpoint。

main source 只使用 Java collections/regex 和 method-local counters；不读取内容、系统内存、hardware counter、ModelProvider、
tokenizer 或 NPU。token/byte count 是受信调用方 metadata，当前不得解释为 vendor tokenizer evidence。debug probe 只验证
deterministic allocation 和 false boundary，不触发模型或硬件。

当前新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变，不开发 vendor/AOSP/BSP 或虚拟化。未来 production context
composition 若需要 vendor tokenizer、NPU-side prompt packing 或 secure shared buffer，必须先取得公开/vendor SDK、owner、格式、
lifecycle 与 evidence 合同；只有确认现有能力不足并批准最小缺口后才新增 DRV-GAP。

状态：`context_budget_manager_defined=true`、`context_budget_android13_arm64_verified=false`、
`context_budget_decision_only=true`、`context_budget_text_payload_accepted=false`、
`context_budget_tokenizer_wired=false`、`context_budget_summarizer_wired=false`、
`context_budget_production_authority_wired=false`、`context_budget_runtime_wired=false`、
`model_invoked=false`、`npu_accessed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`、`implementation_stage=P9-W03`。Req IDs：`S2-MEM-001`、`S2-MDL-001`、
`S2-SAF-001`、`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007`、`DEL-004/005`；tracking：`DEV-071`、
`ISSUE-044`。

## P5-W10 Memory consent HMI/API Driver/HAL Boundary

`MemoryConsentController` 的 source/purpose/retention、owner fingerprint、driving state、mutation evidence、projection revision 与 HMI
状态是 Android application software contract，不是 VHAL property、vendor service、device node、ioctl、PCIe/NPU buffer、DMA/IOMMU
handle 或 Driver/HAL endpoint。

main source 只使用 Java collections/regex 和 injected elapsed clock；debug Activity 只使用 Android View、`SystemClock` 和固定 test
authority。它不访问 Android Car/VHAL、Binder、Room/file、Keystore/TEE、ModelProvider、NPU 或设备节点。PARKED/MOVING/UNKNOWN
当前是调用方提供的 typed state，不触发新增车辆信号 driver；production trusted Context 仍由 ISSUE-045/P8 跟踪。

当前新增 Driver/HAL 开发量为 0，`DRV-GAP-001..005` 不变，不开发 vendor/AOSP/BSP 或虚拟化。未来若 OEM consent owner 依赖
hardware-backed identity/key、secure storage 或 VHAL driving state，必须先取得公开/vendor SDK、permission、lifecycle 和 evidence；
只有确认现有能力不足并批准最小缺口后才新增 DRV-GAP。

状态：`memory_consent_controller_defined=true`、`memory_consent_android13_arm64_verified=false`、
`memory_consent_hmi_projection_only=true`、`memory_consent_repository_mutation_wired=false`、
`memory_consent_production_authority_wired=false`、`memory_consent_runtime_wired=false`、
`memory_consent_model_context_published=false`、`model_invoked=false`、`npu_accessed=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`、`implementation_stage=P9-W03`。
Req IDs：`S2-MEM-001`、`S2-UX-003`、`S2-SAF-001`、`S2-OBS-001`、`XSC-001/004/005/006`、
`KH-003/006/007`、`DEL-004/005`；tracking：`DEV-072`、`ISSUE-045`。

## P6-W01 EventBroker Driver/HAL Boundary

P6-W01 仅新增 Java 进程内 typed Event contract，不新增 C/JNI、内核驱动、VHAL property、device node、ioctl、共享内存、
DDS/SOME-IP 或 vendor middleware 调用。`hardware_accessed=false`，所以当前 Driver/HAL 新增开发量为 0。

后续只有在目标 SDK/BSP 公开接口确认后才可登记：

1. production Event middleware transport 与 discovery/QoS API；
2. publisher sequence、transactional append 与 process-death cursor repository owner；
3. Binder/SDK caller identity 到 middleware identity/policy 的映射；
4. Android 13 service lifecycle、SELinux、signer/permission 与跨进程 callback ownership；
5. critical Action/Approval/Effect event 的 no-silent-drop、ack/replay 和 fault evidence；
6. 多 SOC 黄色太阳组件需要的 topic/schema compatibility 与 Android 主路径部署接口。

在接口证据缺失前不得猜测 vendor topic、socket、shared-memory layout、property ID 或 ioctl，也不得把旧
`DurableEventCursorRepository` 与本轮 process-local retention 描述成已接线。状态：
`event_broker_durable_persistence_wired=false`、`event_broker_dds_transport_wired=false`、
`event_broker_production_published=false`、`event_broker_runtime_wired=false`、`driver_development_triggered=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-073`、`ISSUE-046`。

## P6-W02 Event Backpressure/QoS Driver/HAL Boundary

P6-W02 只新增 Java process-local list/counter/elapsed-clock 策略。priority、deadline、coalesce key、overflow reason 和 replay cursor
是应用层 metadata，不是 TSN/DDS QoS、vendor middleware queue、shared-memory ring、VHAL property、device node 或 Driver/HAL ABI。

当前不新增 C/JNI、内核驱动、SELinux、SOME-IP/DDS、socket、共享内存、ioctl、PCIe/NPU 或 vendor SDK 调用；
`driver_development_triggered=false`，现有 `DRV-GAP-004` 保持 Open。黄色太阳多 SOC 组件的 production topic/schema/QoS 只有在 OEM
公开 middleware owner、API/ABI、security、buffer ownership、fault/recovery 与 target load evidence 后才能设计，不能从本轮
process-local policy 猜测。

critical no-silent-drop 当前只保证结果显式并要求 cursor replay，不证明跨进程/重启无损。未来必须由 ISSUE-046/DRV-GAP-004
关闭 durable append/ACK、Binder death、disk-full、network partition、slow consumer 与 target latency/fault evidence。

状态：`event_qos_contract_defined=true`、`event_qos_android13_arm64_verified=true`、
`event_qos_process_local=true`、`event_qos_broker_wired=false`、`event_qos_durable_persistence_wired=false`、
`event_qos_production_middleware_wired=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。Req IDs：`S2-EVT-001`、`NV-G-004`、
`S2-SAF-001`、`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007/008/009`、`DEL-004/005`；tracking：
`DEV-073`、`ISSUE-046`。

## P6-W03 TriggerEngine Driver/HAL Boundary

P6-W03 只新增 pure-Java manifest/state/counter/elapsed-clock 逻辑。`Metric` 是 AIOS 内部 canonical trigger metadata，不是 VHAL
property ID、DMS register、CAN signal、sensor HAL、DDS topic、shared-memory layout、device node 或 Driver/HAL ABI。

当前不新增 C/JNI、kernel driver、SELinux、Vehicle HAL、DMS/OMS、SOME-IP/DDS、socket、PCIe/NPU 或 vendor SDK 调用；
`driver_development_triggered=false`。P6-W05 只有在 OEM/Vendor 公开 source API、单位/范围、freshness/quality、identity、permission、
rate/backpressure 和 target evidence 后，才能把 adapter observation 映射到 fixed metric，不能由本规则引擎猜测。

黄色太阳多 SOC 组件若未来承载 Trigger source，需要另行冻结跨 SOC 时钟、sequence、quality、schema compatibility、privacy、network
partition 和 cooldown ownership。当前 `CooldownStore` 重启即丢失，不能作为整车免打扰或频控证据。无新增 Driver/HAL gap；
既有 `DRV-GAP-004` 保持 Open。

状态：`trigger_rule_manifest_defined=true`、`trigger_engine_android13_arm64_verified=true`、
`trigger_engine_process_local=true`、`trigger_cooldown_persistence_wired=false`、
`trigger_source_adapter_wired=false`、`trigger_auto_execution_enabled=false`、`trigger_runtime_wired=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。Req IDs：`S2-EVT-001`、
`S2-SCN-001`、`S2-SAF-001`、`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007/008/009`、
`DEL-004/005`；tracking：`DEV-074`、`ISSUE-031`。

## P6-W04 Proactive consent/policy Driver/HAL Boundary

本包是 Android-independent consent/policy 合同，不新增 Driver/HAL 开发量。它只引用 build-owned capability enum、zone enum 与
SHA-256 evidence，不读取 Vehicle property、DMS signal、PCIe/NPU、device node、DMA/IOMMU、ioctl、sysfs 或 vendor handle。

未来 source adapter、真实 vehicle effect 或跨重启 grant repository 若暴露已确认的公开/Vendor API 缺口，必须先在
`ISSUE-031/030/046` 记录 owner、接口、权限、ABI、故障语义和证据，再决定是否触发最小 Driver/HAL 工作包。P6-W04 不预设
property ID、binder service、SELinux policy 或厂商源码改动。

状态：`proactive_consent_policy_defined=true`、`proactive_consent_android13_arm64_verified=true`、
`proactive_policy_process_local=true`、`proactive_grant_persistence_wired=false`、
`proactive_consent_authority_wired=false`、`proactive_auto_execution_enabled=false`、
`proactive_runtime_wired=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。Req IDs：`S2-SAF-001`、`S2-MEM-001`、
`S2-EVT-001`、`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007/008/009`、`DEL-004/005`；
tracking：`DEV-075`、`ISSUE-031`。

## P6-W05 Context source adapters Driver/HAL Boundary

本包不新增 Driver/HAL 开发量。Runtime health 与 time 仅转换 caller-owned sample；simulated vehicle adapter 只转换已存在的
`SignalValue(source=SIMULATED)`，不发现或读取 CarPropertyManager、VHAL、Vendor SOA/Binder、CAN/DBC、device node、ioctl、
sysfs、JNI/C ABI、PCIe/NPU 或 DMA/IOMMU。

`SignalSource.AAOS/VENDOR/DERIVED` 在该 adapter 中全部失败关闭，不能因为设备已通过 ADB 连接就推断 vehicle service 可用。
真实 vehicle source 仍属于 P8：必须先取得 SDK API、service owner、permission/SELinux、property+area mapping、rate/freshness、
fault/readback 和签名证据，再判断 `DRV-GAP-001..005` 是否需要最小新增工作。

状态：`context_source_adapter_contract_defined=true`、`context_source_count=3`、
`context_source_android13_arm64_verified=true`、`context_source_production_registry_published=false`、
`context_source_runtime_wired=false`、`context_source_trigger_engine_wired=false`、
`vehicle_signal_provider_wired=false`、`vehicle_property_mapping_configured=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。Req IDs：`S2-CTX-001`、
`S2-EVT-001`、`S2-SAF-001`、`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007/008/009`、
`DEL-004/005`；tracking：`DEV-076`、`ISSUE-030/031`。

## P6-W06 Active suggestion UX Driver/HAL Boundary

本包不新增 Driver/HAL 开发量。Controller 只处理 caller-owned digest metadata 与 injected elapsed clock；debug HMI 只显示固定
why/plan 文案。代码不读取 CarPropertyManager/VHAL/Vendor SOA/CAN/DBC、device node、ioctl/sysfs、JNI/C ABI、PCIe/NPU，
也不发送空调、座椅、媒体或导航命令。

HMI 中“预览升温/座椅加热”与“预览座椅舒缓/通风”仅为 plan projection，不是 capability availability 或 Effect receipt。
未来接入 Client2/真实车控必须先由 P8 adapter 冻结 SDK API、permission/SELinux、capability、desired/reported、fault、rollback 和
readback；P6-W06 不猜测厂家接口。

状态：`active_suggestion_controller_defined=true`、`active_suggestion_android13_arm64_verified=true`、
`active_suggestion_hmi_projection_only=true`、`active_suggestion_production_source_wired=false`、
`trigger_engine_wired=false`、`graph_execution_enabled=false`、`effect_dispatch_enabled=false`、
`vehicle_signal_provider_wired=false`、`vehicle_property_mapping_configured=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。Req IDs：`S2-UX-002`、
`S2-TRG-002`、`S2-SAF-001`、`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007/008/009`、
`DEL-004/005`；tracking：`DEV-077`、`ISSUE-030/031`。

## P7-W01 ModelRequest/Result v2 Driver/HAL Boundary

本包是 pure-Java metadata contract，不新增 C/JNI、Driver/HAL 或 vendor SDK 开发量。Request/Result 只操作 enum、bounded number、
canonical identifier 与 SHA-256；不打开 device node，不调用 ioctl/sysfs、PCIe/NPU runtime、DMA/IOMMU、CarPropertyManager/VHAL、
Vendor SOA/Binder 或 network。

`RequiredCapability` 是模型能力类别，不是 NPU opcode、firmware capability、device feature bit 或模型格式。P7-W02 registry 可以只发布
empty/test placeholder；只有取得 Vendor SDK/ABI、模型 artifact 格式、buffer ownership/alignment、cancel/deadline、fault/thermal/power
和目标性能证据后，才允许在 `ISSUE-024` 下评估最小 C/JNI/Driver-HAL gap。

状态：`model_contract_v2_defined=true`、`model_contract_v2_android13_arm64_verified=true`、
`model_provider_registry_wired=false`、`model_policy_router_wired=false`、`vendor_npu_provider_available=false`、
`model_invoked=false`、`npu_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。Req IDs：`S2-MDL-001`、`S2-SAF-001`、
`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007/008/009`、`DEL-004/005`；tracking：`DEV-078`、`ISSUE-024`。

## P7-W02 ModelProviderRegistry/health Driver/HAL Boundary

本包不新增 C/JNI、Driver/HAL 或 vendor SDK 工作。`hardwareExpected=true` 只标记 vendor NPU 未来部署类型；
`networkRequired=true` 只标记 cloud 未来依赖。两者都不是当前访问、权限、ABI 或 readiness 证据。

Vendor HEALTHY report 仅为 metadata 测试，不能设置 implementation available、production eligible、routing 或 hardware accessed。
真实 vendor health source 必须在 `ISSUE-024` 下冻结 SDK/C ABI、provider process owner、device/firmware health、clock/revision、fault
isolation、permission/SELinux 和签名证据后接入。Cloud 同理需要 network/privacy/consent owner，不属于 Driver/HAL。

状态：`model_provider_registry_defined=true`、`model_provider_count=4`、`model_production_ready_count=0`、
`model_provider_registry_android13_arm64_verified=true`、`model_provider_registry_runtime_wired=false`、
`vendor_npu_provider_available=false`、`model_policy_router_wired=false`、`model_invoked=false`、
`network_accessed=false`、`npu_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。Req IDs：`S2-MDL-001`、`S2-SAF-001`、
`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007/008/009`、`DEL-004/005`；tracking：`DEV-079`、`ISSUE-024`。

## P7-W03 PolicyAwareModelRouter Driver/HAL Boundary

本包是 pure-Java metadata admission，不新增 C/JNI、Driver/HAL 或 vendor SDK 工作。`NetworkState`、`ThermalState` 和 quota 是调用方
提交的 typed snapshot；Router 不调用 ConnectivityManager、Power/Thermal service、NPU runtime、device node、ioctl/sysfs、PCIe、
DMA/IOMMU、CarProperty/VHAL、Vendor SOA/Binder 或网络。

`thermalAdmissionRequired` 和 minimum latency 是 build-owned route policy，不是温度传感器读取、DVFS 控制、NPU benchmark 或硬件
能力发现。Vendor/cloud placeholder 即使 policy eligible，也仍受 P7-W02 implementation/production readiness gate，不能触发 inference。
真实 producer/Provider 必须在 `ISSUE-024` 下冻结 SDK/ABI、identity/permission、clock/revision、resource/thermal/quota semantics、
cancel/deadline、fault isolation 和目标证据后再接入。

状态：`model_policy_router_defined=true`、`model_policy_router_android13_arm64_verified=true`、
`model_policy_router_runtime_wired=false`、`vendor_npu_provider_available=false`、`provider_invoked=false`、
`model_invoked=false`、`network_accessed=false`、`npu_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。Req IDs：`S2-MDL-001`、`S2-SAF-001`、
`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007/008/009`、`DEL-004/005`；tracking：`DEV-080`、`ISSUE-024`。

## P7-W04 LocalModelProvider Driver/HAL Boundary

本包不新增 C/JNI、Driver/HAL 或 vendor SDK 工作。`LocalModelProvider` 只存在于 debug source set，通过 Java injected
`LocalInferenceEngine` 在同一进程运行；它不打开 device node，不调用 ioctl/sysfs、PCIe、DMA/IOMMU、NPU runtime、firmware、
CarProperty/VHAL、Vendor SOA/Binder 或网络。

`android.local.development` 的 catalog availability 只表示 debug implementation artifact 已存在，不是 NPU capability、模型已加载、
production health 或硬件 readiness。descriptor 固定 hardware=false、production=false、fallback=NEVER；release source 不包含 executable
Provider，不能把 Vendor NPU failure 隐式导向本地开发 engine。

未来 Vendor NPU Provider 必须在 `ISSUE-024` 下单独冻结 SDK/C ABI/JNI、model/buffer ownership、alignment/cache、deadline/cancel、
streaming semantics、fault isolation、thermal/power/resource admission、permission/SELinux、firmware compatibility 和目标性能证据。
只有公开/Vendor API 明确不足时才记录最小 Driver/HAL gap；P7-W04 不预判该缺口。

状态：`local_model_provider_debug_only=true`、`local_model_provider_release_source_absent=true`、
`local_model_provider_runtime_wired=false`、`local_model_provider_vendor_npu_fallback_enabled=false`、
`local_model_provider_android13_arm64_verified=true`、`production_inference_enabled=false`、`network_accessed=false`、
`npu_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。
Req IDs：`S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、`XSC-001/004/005/006`、
`KH-003/006/007/008/009`、`DEL-004/005`；tracking：`DEV-081`、`ISSUE-024`。

## P7-W05 Structured Model Output Driver/HAL Boundary

本包只在 Java main source 中解析和验证 bounded UTF-8 JSON，不新增 C/JNI、Driver/HAL、Vendor SDK 或 Android system API。它读取调用方
提供的 build-owned ScenarioCatalog/CapabilityCatalog 元数据，不访问 CarProperty/VHAL、Vendor SOA/Binder、device node、ioctl/sysfs、
PCIe、DMA/IOMMU、NPU firmware/runtime 或网络。

响应中的 `capabilityId` 是软件 catalog key，不是已发现硬件能力，也不允许映射任意 vendor property。validator 只确认软件 schema/range；
`CapabilityAvailability.canUseProduction`、adapter readiness、Safety、permission/SELinux 和 readback 仍由 P8 target adapter 与生产 authority
单独决定。本包不触发 Driver/HAL gap。

状态：`structured_model_output_verified=true`、`model_output_schema_runtime_wired=false`、
`structured_model_output_android13_arm64_verified=true`、`model_invoked=false`、`network_accessed=false`、
`npu_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。Req IDs：`S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、
`XSC-001/004/005/006`、`KH-003/006/007/008/009`、`DEL-004/005`；tracking：`DEV-082`、`ISSUE-024`。

## P7-W06 Scenario Evaluation Driver/HAL Boundary

本包只在 Java main source 中运行 fixed synthetic metadata evaluation，不新增 C/JNI、Driver/HAL、Vendor SDK 或 Android system API。
ScenarioCatalog/CapabilityCatalog 和 request resource measurements 均由调用方提供；harness 不读取 CarProperty/VHAL、Vendor SOA/Binder、
device node、ioctl/sysfs、PCIe、DMA/IOMMU、NPU firmware/runtime、温度传感器或网络。

`DrivingState`、Safety freshness 和 unavailable capability 是 synthetic case enum，不是目标车辆采样。latency/token 是 caller-owned test
metadata，不是 Vendor profiler 或 NPU telemetry。P7-W07 资源/热准入仍只能消费明确 owner 发布的 metadata；P8 真实硬件接口必须另行
冻结 API、权限、时间基准和 evidence，不能复用本评测结果冒充硬件发现。本包不触发 Driver/HAL gap。

状态：`scenario_evaluation_verified=true`、`scenario_evaluation_runtime_wired=false`、
`raw_evaluation_content_logged=false`、`scenario_evaluation_android13_arm64_verified=true`、`model_invoked=false`、
`network_accessed=false`、`npu_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。Req IDs：`S2-MDL-001`、`S2-SAF-001`、
`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007/008/009`、`DEL-004/005`；tracking：`DEV-083`、`ISSUE-024`。

## P7-W07 Resource and Thermal Admission Driver/HAL Boundary

本包仅消费调用方发布的 typed `PolicySnapshot/ResourceSnapshot`，不新增 C/JNI、Driver/HAL、Vendor SDK 或 Android system service
调用。available slots、capacity、thermal、revision、elapsed validity 和 evidence digest 都是输入合同，不是本类从 sensor、firmware、
NPU runtime、device node、ioctl/sysfs、PCIe、DMA/IOMMU、CarProperty/VHAL 或 Vendor Binder 读取的事实。

`forFixedAdmissionContract` 只允许 deterministic stub 进入测试 scheduler，不能作为 production route。真实 producer 必须在
`ISSUE-024/P8-W01..W05` 下冻结 owner、SDK/API、permission/SELinux、clock/revision、capacity/thermal semantics、fault、deadline/cancel
和性能证据。只有公开/Vendor API 被证明不足后才记录最小 Driver/HAL gap；当前未触发。

状态：`model_resource_admission_verified=true`、`resource_snapshot_producer_wired=false`、
`resource_admission_runtime_wired=false`、`vendor_npu_provider_available=false`、`provider_invoked=false`、
`model_invoked=false`、`network_accessed=false`、`npu_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。Req IDs：`S2-MDL-001`、`S2-SAF-001`、
`S2-OBS-001`、`NV-G-004`、`XSC-001/004/005/006`、`KH-003/006/007/008/009`、`DEL-004/005`；
tracking：`DEV-084`、`ISSUE-024`。

## P8-W01 Target Capability Discovery Driver/HAL Boundary

P8-W01 collector 只执行 ADB shell 可见的公开只读 inventory，不打开 device node，不调用 ioctl/sysfs、PCIe/DMA/IOMMU/NPU runtime、
CarProperty getter/setter、VHAL、Vendor Binder API、CAN/DBC 或 SOA command。service list 只用于生成内部 evidence digest 和 count，不能
解释为接口可调用、权限可获得或车辆 capability 可写。

Driver/HAL gap 触发条件保持不变：必须先取得并评审 public/Vendor API、permission、owner/version、readback/fault/rollback 完整矩阵，
然后证明特定 capability 无法由公开接口满足，才可登记最小 gap。当前没有该证据，所以 P8-W02..W06 不启动，也不新增 C/JNI/driver。

API 33 上已完成公开只读 inventory，脱敏聚合为 feature/Binder/car-match/command count 69/274/2/265；这不构成任何 Driver/HAL
缺口证据，不触发 device-node、JNI 或内核开发。

状态：`target_capability_discovery_contract_defined=true`、`target_capability_read_only_collector_verified=true`、
`target_public_inventory_collected=true`、`target_public_inventory_identity_redacted=true`、
`target_public_inventory_privacy_confirmed=true`、`target_public_inventory_api33_verified=true`、
`target_capability_matrix_complete=false`、`target_capability_discovery_external_blocked=true`、
`vehicle_property_mapping_configured=false`、`production_adapter_registered=false`、
`vendor_npu_provider_available=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。Req IDs：`S2-ADP-002`、`S2-OBS-001`、
`XSC-001/004/005/006`、`KH-003/006/007`、`DEL-004/005`；tracking：`DEV-085/110`、`ISSUE-047`。

## P9-W01 Performance Budget Driver/HAL Boundary

主预算合同不新增 C/JNI、Driver/HAL、Vendor SDK 或 Android system-service 调用。它不读取 `clock_gettime`、`/proc`、Perfetto、simpleperf、
PSS/CPU API、DB file、Binder stats、CarProperty/VHAL、PCIe/DMA/IOMMU 或 NPU telemetry；所有 measurement 都是受信采集方输入的聚合值。

Binder/Plan/Effect admission 是应用层范围，不包含真实车辆总线 apply/readback 或 NPU model latency。未来目标采集优先使用公开 Android
profiling/testing surfaces；只有公开接口被证明无法提供某项必要证据后，才能在独立 Issue 中评估最小 Driver/HAL gap。本包不触发该条件。

状态：`performance_budget_contract_defined=true`、`performance_budget_target_owner_approved=false`、
`performance_budget_target_measurement_complete=false`、`performance_budget_android13_arm64_verified=false`、
`performance_budget_runtime_wired=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。Req IDs：`S2-OBS-001`、`S2-REL-001`、
`XSC-001/004/005/006`、`KH-003/006/007/008/009`、`DEL-001/004/005`；tracking：`DEV-086`、`ISSUE-048`。

## P9-W02 Stability Fault Matrix Driver/HAL Boundary

主稳定性合同不新增 C/JNI、Driver/HAL、Vendor SDK 或 system-service 调用。它不杀 Binder/进程、不改变 storage/network、不读取
tombstone、ANR、`/proc`、Perfetto、Vehicle/VHAL、PCIe/DMA/IOMMU 或 NPU telemetry；所有 observation 都是受信采集方输入的聚合值。

未来 fault injection 优先使用 Android application/test、公开 process lifecycle、受控存储和网络测试 surface。只有公开接口被证明无法
形成必要的目标证据后，才能单独评估最小 Vendor/Driver-HAL gap；本包不触发该条件，也不开发虚拟化。

状态：`stability_fault_matrix_contract_defined=true`、`stability_target_72h_complete=false`、
`stability_android13_arm64_verified=false`、`stability_fault_injection_runtime_wired=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。
Req IDs：`S2-REL-001`、`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007/008/009`、
`DEL-001/004/005`；tracking：`DEV-087`、`ISSUE-049`。

## P9-W03a Parser Security Driver/HAL Boundary

本增量只在 host JVM 中向现有 Java parser/validator 提交有界恶意输入；不新增 C/JNI、Driver/HAL、Vendor SDK、Android
system-service、device node、ioctl/sysfs、CarProperty/VHAL、PCIe/DMA/IOMMU 或 NPU 调用。Scenario source path 仅作为 parser
字符串输入验证，测试不会打开该路径。

覆盖或模糊测试发现 Java 应用层缺陷时必须先在对应 parser/validator 修复。只有未来目标安全需求明确要求硬件/固件 evidence，且公开
Android/Vendor surface 经验证无法满足后，才可单独登记最小 Driver/HAL gap；W03a 不触发该条件，不开发虚拟化。

状态：`security_parser_corpus_defined=true`、`security_parser_case_count=18`、
`security_parser_fail_closed_regression_verified=true`、`security_coverage_guided_fuzz_complete=false`、
`security_android13_arm64_verified=false`、`security_runtime_wired=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。Req IDs：
`S2-SAF-001`、`S2-TOL-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-088`、`ISSUE-050`。

## P9-W03b Identity/Replay Security Driver/HAL Boundary

No Driver/HAL development is triggered. The increment calls Java policy and process-local registry interfaces only.
Production caller evidence remains Android framework `Binder.getCallingUid()`, `UserManager` and `PackageManager`
current signer data; no vendor service, device node, ioctl, sysfs, CarProperty, vehicle bus or NPU interface is added.

Target integration must later provide a controlled Android test caller, approved package/signing setup and sanitized
evidence without exposing serials, fingerprints, signing material, package inventory or raw payloads. Until that
evidence exists, `security_binder_calling_uid_spoof_android_verified=false`,
`security_package_signature_cryptographically_verified=false`, `security_android13_arm64_verified=false`,
`driver_development_triggered=false`, `hardware_accessed=false`, `production_ready=false`,
`target_hardware_validated=false`, `implementation_stage=P9-W03`. Req IDs: `S2-SAF-001`, `S2-TOL-001`,
`S2-SES-001`, `S2-OBS-001`, `DEL-001/004/005`; tracking: `DEV-089`, `ISSUE-050`.

## P9-W03c Security Boundary Inventory Driver/HAL Boundary

No Driver/HAL development is triggered. The inventory reads repository AIDL source only in test/checker code; the
Android probe calls Java validators inside the debug APK. No vendor service, CarProperty, device node, ioctl, sysfs,
vehicle bus, NPU, kernel Binder modification or system image change is added.

Future target evidence may use the existing Android framework Binder/PackageManager surfaces and approved test APKs.
Any missing vendor contract must be recorded separately before Driver/HAL work. Current:
`security_android_debug_probe_available=true`, `security_android_debug_probe_executed=false`,
`security_binder_calling_uid_spoof_android_verified=false`,
`security_package_signature_cryptographically_verified=false`, `security_android13_arm64_verified=false`,
`driver_development_triggered=false`, `virtualization_development_triggered=false`, `hardware_accessed=false`,
`production_ready=false`, `target_hardware_validated=false`, `implementation_stage=P9-W03`. Req IDs:
`S2-SAF-001`, `S2-TOL-001`, `S2-SES-001`, `S2-MDL-001`, `S2-OBS-001`, `DEL-001/004/005`; tracking:
`DEV-090`, `ISSUE-050`.

## P9-W04a Privacy Data Inventory Driver/HAL Boundary

W04a 只枚举现有 Java/Room/process-local/transient 数据边界，不调用数据库实例、Android framework、Vendor service、CarProperty、
文件、设备节点、网络、NPU 或 Driver/HAL。没有新增驱动开发量，也不修改芯片厂商 SDK 或已刷机系统。

未来 W04b 的 retention/delete/export policy 仍应在应用层和现有 repository boundary 实现；只有明确发现系统级安全存储、硬件密钥
或 Vendor persistence contract 缺口时才单独触发 Driver/HAL 评审。当前 `driver_development_triggered=false`、
`virtualization_development_triggered=false`、`privacy_android13_arm64_verified=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W04`。Req IDs：
`S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-091`、`ISSUE-051`。

## P9-W04b Privacy Policy Admission Driver/HAL Boundary

W04b 是应用层纯 Java metadata validator。它不打开 Room、文件、日志、Binder、Vendor service、CarProperty、设备节点、网络或 NPU，
不修改芯片厂商 SDK/系统镜像，也不触发 Driver/HAL 或虚拟化开发。LifecycleStateSnapshot 由 JVM fixture 提供，不读取真实车辆或设备状态。

未来 repository enforcement 若需要系统级 secure storage、hardware-backed key 或 Vendor persistence API，必须在目标公开合同与 owner policy
到位后另立缺口；当前不得推断。`driver_development_triggered=false`、`virtualization_development_triggered=false`、
`privacy_repository_mutation_wired=false`、`privacy_android13_arm64_verified=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W04`。Req IDs：
`S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-092`、`ISSUE-051`。

## P9-W04c Privacy Redaction/Audit Probe Driver/HAL Boundary

W04c 只使用 Android app debug Activity、`android.permission.DUMP` 和 Log API；main projection 是纯 Java。它不读取 Room/file/device node、
不调用 Binder/Vendor/CarProperty/network/NPU，不修改系统镜像/SELinux，也不新增 Driver/HAL 或虚拟化开发量。Nonce 是有界非秘密关联值。

未来合规审计持久化或 hardware-backed evidence 若要求目标专有服务，必须先获得公开 SDK contract 和 owner policy 后单独评审；debug Log
不能替代。当前 `driver_development_triggered=false`、`virtualization_development_triggered=false`、
`privacy_android_debug_probe_executed=false`、`privacy_android13_arm64_verified=false`、
`privacy_repository_mutation_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W04`。Req IDs：`S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`、
`DEL-001/004/005`；tracking：`DEV-093`、`ISSUE-051`。
## P9-W05a Production Release Admission Driver/HAL Boundary

W05a 是应用层纯 Java metadata validator，不调用 PackageManager、keystore、Room、ADB、Vendor service、CarProperty、设备节点、网络、
NPU、Driver/HAL，也不修改系统镜像/SELinux。Signer/artifact/source/archive 均只作为调用方提供的 SHA-256 metadata，不读取证书或 APK。

未来 production signer measurement 和 OTA/MDM installer 优先使用 Android 标准 package/signing API 与 OEM 已发布管理接口。只有 owner
确认公开 surface 无法满足且给出最小接口合同后，才评估系统/Driver 缺口；本增量不触发。当前
`driver_development_triggered=false`、`virtualization_development_triggered=false`、
`production_signer_owner_approved=false`、`release_installer_wired=false`、
`release_rollback_executor_wired=false`、`release_android13_arm64_verified=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W05`。Req IDs：
`S2-REL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-094`、`ISSUE-052`。

## P9-W05b Production Release Metadata Probe Driver/HAL Boundary

W05b 只使用应用层 `PackageManager.getPackageInfo(flags=0)`、`checkSignatures`、debug Activity、DUMP permission 和 ADB shell。它不读取
certificate/signature bytes，不访问 keystore/Room/Vendor service/CarProperty/device node/network/NPU，不修改系统镜像、SELinux 或厂商 SDK。
Dry-run adapter 不执行 package install/uninstall/rollback，因此没有新增 Driver/HAL 或虚拟化开发量。

未来 OTA/MDM、system-package policy、signer rotation 或 rollback 若需要 OEM 接口，必须先取得公开 SDK contract、命名 owner 和最小 capability
evidence；不能从 W05b debug observation 推断。当前 `driver_development_triggered=false`、
`virtualization_development_triggered=false`、`release_android_debug_probe_executed=false`、
`production_signer_owner_approved=false`、`release_installer_wired=false`、
`release_rollback_executor_wired=false`、`release_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W05`。Req IDs：`S2-REL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：
`DEV-095`、`ISSUE-052`。

## P9-W06a Driver Safety Admission Driver/HAL Boundary

W06a 是 Android 应用层纯 Java metadata/policy contract。它只消费已有 immutable `SafetyVehicleStateSnapshot` 和调用方提供的
capability/owner digest metadata，不读取 Android Car、VHAL、Vendor Binder、CAN、device node、sysfs、property、NPU 或网络，不调用
Effect adapter，也不改变厂商 SDK、系统镜像或 SELinux。该增量没有 C/JNI/Driver/HAL 开发量。

未来真实 `SafetyVehicleStateProvider`、gear/speed/parking-brake/occupancy/belt/recline readback 和 HVAC/Seat write capability 必须优先来自
目标公开 Android/OEM SDK。只有接口 owner 明确证明现有 SDK 无法满足一个已编号 capability，并提供 type/unit/area/freshness/permission/
error/readback/fault/rollback contract 后，才登记最小 Driver/HAL 缺口；不得由 P9-W06a synthetic fixture 推断接口。

当前 `driver_development_triggered=false`、`virtualization_development_triggered=false`、
`driver_safety_current_owner_policy_approved=false`、`driver_safety_vehicle_state_provider_wired=false`、
`driver_safety_effect_runtime_wired=false`、`driver_safety_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W06`。Req IDs：`S2-UX-002`、`S2-SAF-001`、`S2-EFF-001`、
`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-096`、`ISSUE-029/030`。

## P9-W06b Driver Safety Probe Driver/HAL Boundary

W06b 新增的 main-source projection 是 Android-independent metadata 计算，debug Activity 只接受数字 nonce，ADB adapter 只启动
已经安装的 Activity 并校验固定 count/boolean。三者都不读取 Android Car、VHAL、Vendor Binder、CAN、device node、sysfs、
property、车辆 scalar、NPU 或网络，不调用 Effect adapter，也不修改厂商 SDK、系统镜像或 SELinux。

本包没有 C/JNI/Driver/HAL 开发量。真实 Safety State、gear/speed/parking-brake/occupancy/belt/recline readback 与 HVAC/Seat write
仍必须优先来自目标公开 Android/OEM SDK；只有 owner 证明既有 SDK 无法满足已编号 capability 后，才登记最小 Driver/HAL 缺口。

当前 `driver_development_triggered=false`、`virtualization_development_triggered=false`、
`driver_safety_redacted_projection_defined=true`、`driver_safety_android_debug_probe_executed=false`、
`driver_safety_current_owner_policy_approved=false`、`driver_safety_vehicle_state_provider_wired=false`、
`driver_safety_effect_runtime_wired=false`、`driver_safety_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W06`。Req IDs：`S2-UX-002`、`S2-SAF-001`、`S2-EFF-001`、
`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-097`、`ISSUE-029/030`。

## P9-W07a Release Evidence Envelope Driver/HAL Boundary

W07a 是 Android 应用工程内的 pure-Java metadata contract。它只消费调用方提供的 release/diagnostic digest、bounded identifier 和布尔值，
不读取 Android API、PackageManager、文件、网络、Vehicle/VHAL、Vendor Binder、device node、NPU，也不修改厂商 SDK、系统镜像或
SELinux。本增量没有 C/JNI/Driver/HAL 或虚拟化开发量。

未来 W07b target diagnostics 优先使用应用层 debug Activity、ADB 和目标已公开 Android/OEM SDK。只有命名 owner 证明某个已编号 diagnostic
category 无法由公开接口完成，并提供最小 type/permission/error/redaction/evidence contract 后，才登记 Driver/HAL 缺口；不得从 W07a
synthetic report 推断接口或硬件资格。

当前 `driver_development_triggered=false`、`virtualization_development_triggered=false`、
`release_evidence_envelope_defined=true`、`release_evidence_target_owner_approved=false`、
`release_evidence_runtime_diagnostics_wired=false`、`release_evidence_retest_workflow_wired=false`、
`release_evidence_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W07`。Req IDs：`S2-OBS-001`、`S2-REL-001`、
`DEL-001/004/005`；tracking：`DEV-098`、`ISSUE-052/053`。

## P9-W07b Field Diagnostics Probe Driver/HAL Boundary

W07b 的 main projection 是 pure Java；debug Activity 只使用标准 PackageManager 和应用自身组件声明；host adapter 只使用 ADB shell
`am start`/logcat 对已安装 debug APK 进行 bounded check。三者不读取 VHAL/Vendor Binder/CAN/device node/sysfs/NPU，不修改系统镜像、
SELinux 或厂商 SDK，不执行安装、卸载或 rollback。本增量没有 C/JNI/Driver/HAL 或虚拟化开发量。

未来某类 field diagnostic 若需要 OEM service，必须先由 owner 提供公开 component/property/permission/version/redaction/evidence contract。
W07b 的 debug PackageManager/launch observation 不能用于推断 Vehicle/Driver 接口或量产资格。

当前 `driver_development_triggered=false`、`virtualization_development_triggered=false`、
`field_diagnostics_android_debug_probe_executed=false`、`field_diagnostics_target_category_execution_complete=false`、
`release_evidence_target_report_admitted=false`、`release_evidence_retest_workflow_wired=false`、
`field_diagnostics_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W07`。Req IDs：`S2-OBS-001`、`S2-REL-001`、
`DEL-001/004/005`；tracking：`DEV-099`、`ISSUE-052/053`。

## P9-W07c Release Retest Workflow Driver/HAL Boundary

W07c 是 pure-Java release/issue metadata 状态机，只处理 canonical tag、commit、SHA-256、issue number、enum、cycle 和 boolean。
它不读取 Android API、VHAL/Vendor Binder/CAN/device node/sysfs/NPU，不修改厂商 SDK、系统镜像或 SELinux，也没有 C/JNI/Driver/HAL
或虚拟化开发量。

真实 installer/rollback、车辆侧 diagnostic 与 NPU/Driver 证据仍必须通过目标公开 Android/OEM SDK 和已编号 P8 capability 提供。W07c 的
owner/tester digest admission 不能证明底层接口存在，也不能触发新增 Driver/HAL；只有 owner 证明公开接口缺口后才登记最小实现量。

当前 `driver_development_triggered=false`、`virtualization_development_triggered=false`、
`release_retest_state_machine_defined=true`、`release_retest_replacement_release_published=false`、
`release_evidence_target_report_admitted=false`、`release_evidence_retest_workflow_wired=false`、
`release_retest_github_issue_mutation_wired=false`、`release_retest_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W07`。Req IDs：`S2-OBS-001`、`S2-REL-001`、`DEL-001/004/005`；
tracking：`DEV-100`、`ISSUE-052/053`。

## P4-D4a Simulated Scenario Graph Driver/HAL Boundary

P4-D4a 只在 Android Runtime debug source set 中组合 pure-Java Scenario Compiler 和 control-only AgentGraph。输入是既有 immutable
Context/Capability/Resolution 元数据；输出是 Plan/Graph/pending-node projection。它不读取 Android Car、VHAL、Vendor Binder、CAN、
device node、sysfs、property、NPU 或网络，不调用 Effect adapter，也不修改厂商 SDK、系统镜像或 SELinux。

本增量没有 C/JNI/Driver/HAL 或虚拟化开发量。未来 P4-D4c 接入 simulated adapter 仍只能使用 debug source；真实车辆 Effect 必须等 P8
公开 property/service/permission/owner evidence。JVM supplied outcome 不得用于推断底层接口或硬件资格。

当前 `driver_development_triggered=false`、`virtualization_development_triggered=false`、
`simulated_scenario_graph_defined=true`、`simulated_scenario_android_runtime_wired=false`、
`simulated_scenario_effect_dispatch_enabled=false`、`simulated_scenario_readback_accessed=false`、
`scenario_execution_enabled=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P4-D4a`。Req IDs：`S2-SCN-001`、`S2-GRF-001`、
`S2-EFF-001`、`S2-HMI-003/006`、`APP-004`、`XSC-001/005/006`、`DEL-001/004/005`；
tracking：`DEV-101`、`ISSUE-022/026/030/033`。

## P4-D4b Simulated Scenario Runtime Driver/HAL Boundary

P4-D4b 只组合 debug Java 类：`SimulatedScenarioGraph` 和 `BoundedEventRuntime`。输入仍是既有 immutable Context/Resolution/Capability
对象；输出是 Session/Plan metadata 与 digest-only Event envelope。它不查询 VehicleProperty、Vendor SOA、NPU、PCIe、device node、
sysfs、JNI 或 native driver。

因此本增量不触发 C/C++、HAL 或 Driver 新增开发量，也不改变现有 Driver/HAL 支持矩阵。Android Service/Binder、Client2、
simulated adapter apply 与 readback 都不属于 D4b；D4c 也只能发布 debug Binder，不得借此猜测 OEM API。

当前 `driver_development_triggered=false`、`simulated_scenario_android_service_published=false`、
`simulated_scenario_session_event_binder_published=false`、`simulated_scenario_effect_dispatch_enabled=false`、
`simulated_scenario_readback_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P4-D4b`。Req IDs：`S2-SCN-001`、`S2-GRF-001`、
`S2-EVT-001`、`S2-EFF-001`、`APP-004`、`XSC-001/005/006`、`DEL-001/004/005`；tracking：`DEV-102`。

## P4-D4c Simulated Scenario Binder Driver/HAL Boundary

D4c 新增的 Android Service/AIDL 只读取 APK 内置 scenario assets 并生成 synthetic Context。它不调用 VehicleProperty/Vendor SOA/NPU/PCIe、
不访问 device node/sysfs/JNI，不触发 C/C++、HAL 或 Driver 开发。

Binder 可达性不等于车辆接口可达性。D4d 也只能连接既有 debug simulated adapters；真实 Driver/HAL 仍须 P8 目标 API 缺口证据。
Android 13 安装与签名权限拒绝已验证，但没有读取任何车辆/NPU/Driver/HAL 状态，因此 `hardware_accessed=false` 不变。
当前 `driver_development_triggered=false`、`simulated_scenario_effect_dispatch_enabled=false`、
`simulated_scenario_readback_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P4-D4c`。

## P4-D4d Simulated Effect Composition Driver/HAL Boundary

D4d 调用的四个 adapter 全部位于 Android debug source，并只更新 process-local simulated state/Digital Twin。它们不加载 Vendor SO、不访问
VehicleProperty、device node、sysfs、PCIe/NPU、JNI 或网络，也不启动外部 Media/Navigation Activity。

`simulated_scenario_effect_dispatch_enabled=true` 只表示 debug simulated dispatch；硬件字段必须单独保持
`simulated_scenario_hardware_effect_dispatch_enabled=false`。因此本增量不新增 C/C++、HAL 或 Driver 工作量，也不能关闭 P8 OEM API 缺口。

实体 Android 13 同签名 probe 只经 Android framework Binder 调用了 process-local composition；8 次 dispatch 与 6 次 matched readback
均为模拟计数，未读取设备节点、车辆属性或 Vendor API。

当前 `driver_development_triggered=false`、`simulated_scenario_readback_accessed=true`、
`simulated_scenario_hardware_effect_dispatch_enabled=false`、`hardware_accessed=false`、`production_ready=false`、
`simulated_scenario_android_debug_probe_executed=true`、`simulated_scenario_binder_authorized_call_verified=true`、
`target_hardware_validated=false`、`implementation_stage=P4-D4d`。

## P4-D4e Client2 Simulated Scenario Chain Driver/HAL Boundary

D4e 只通过标准 Android Binder 在 Client2 与 Runtime debug Service 间传输 metadata。Client2 不读取 Android Car API、VehicleProperty、
Vendor Binder/SOA、CAN、device node、sysfs、PCIe/NPU，不启动外部 Media/Navigation Activity，也不修改厂商 SDK、系统镜像或 SELinux。

UI 中的 3/3、5/3、4/2 都是 D4d process-local adapter/readback 计数。实机运行只证明 Android 13 ARM64 上 APK 安装、同 signer Binder、
reducer 和 UI 链路可用；不证明 HVAC、Seat、Media、Navigation 或车辆信号接口存在。因此本增量不触发 C/C++、HAL、Driver 或虚拟化开发。

未来把 D4e 替换为真实执行时，必须按 P8 capability 分别提供公开 property/service/action、permission、area mapping、单位、freshness、
failure/readback、owner、rollback 与 safety evidence；不得直接复用 debug approval 或 simulated target 作为生产接口。

当前 `driver_development_triggered=false`、`virtualization_development_triggered=false`、
`simulated_scenario_client2_wired=true`、`simulated_scenario_android13_arm64_client_verified=true`、
`simulated_scenario_hardware_effect_dispatch_enabled=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P4-D4e`。

## P5 Android 13 ARM64 aggregate probe Driver/HAL boundary

P5 aggregate acceptance 只启动应用私有 debug Activity；Tool/Skill/Memory 实现只使用 Java/Android application API 和 build-owned fixture。
installer 不查询 VHAL、CarProperty、Vendor SOA、CAN、PCIe、NPU、native device node 或系统服务私有接口，也不读取设备 model/serial。

因此本轮不新增 Driver/HAL 开发量，`driver_development_triggered=false`、`virtualization_development_triggered=false`。
真实 Tool/Memory 与 Vehicle/NPU 集成仍必须先取得 P8 capability/property/service/permission/owner/version evidence；只有公开/Vendor API
明确不足且 gap 经评审后，才能新增最小 Driver/HAL 工作包。

当前 `p5_android13_arm64_probe_acceptance_complete=true`、`device_identity_redacted=true`、
`driver_hal_accessed=false`、`hardware_accessed=false`、`production_tool_authority_published=false`、
`production_memory_authority_published=false`、`production_ready=false`、`target_hardware_validated=false`。

## P6 Android 13 ARM64 aggregate probe Driver/HAL boundary

P6 aggregate acceptance 只启动应用私有 debug Activity。Event/Trigger/Consent/Suggestion 使用 pure Java 进程内状态；Context source
只消费 build-owned Runtime health、SIMULATED SignalValue 和注入时间，不读取 Android Car、VehicleProperty、Vendor Binder/SOA、CAN、
device node、sysfs、PCIe/NPU、JNI 或网络。

因此本轮不新增 C/C++、Driver/HAL 或虚拟化开发量，`driver_development_triggered=false`、
`virtualization_development_triggered=false`。真实 Context source 和跨 SOC Event transport 必须先取得 P8 service/property/permission/
area/unit/freshness/owner/version evidence；只有公开/Vendor API 明确不足且缺口经评审后，才新增最小 Driver/HAL 工作包。

当前 `p6_android13_arm64_probe_acceptance_complete=true`、`device_identity_redacted=true`、
`production_event_middleware_published=false`、`production_context_source_registry_published=false`、
`driver_hal_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。tracking：`DEV-107`、`ISSUE-031/046`。

## P7 Android 13 ARM64 aggregate probe Driver/HAL boundary

P7 aggregate acceptance 只启动应用私有 debug Activity。Registry/Router/evaluator/admission 使用 pure Java metadata；LocalModelProvider
使用 debug-only injected in-process engine，不加载 Vendor NPU SO，不访问 PCIe、device node、ioctl、sysfs、Android Car、网络或 JNI。

因此本轮不新增 C/C++、Driver/HAL 或虚拟化开发量，`driver_development_triggered=false`、
`virtualization_development_triggered=false`。真实 NPU provider 必须先取得 P8 vendor ABI/API、model format、memory/stream、cancel、
thermal/resource、fault/rollback、owner/version evidence；只有公开/Vendor API 明确不足并评审后才新增最小 Driver/HAL 工作包。

当前 `p7_android13_arm64_probe_acceptance_complete=true`、`device_identity_redacted=true`、
`production_inference_enabled=false`、`network_accessed=false`、`npu_accessed=false`、`driver_hal_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`。tracking：`DEV-108`、`ISSUE-024/044`。

## P9 Android 13 ARM64 aggregate debug probe Driver/HAL boundary

P9 aggregate acceptance 只运行应用私有 debug Activity 和三项只读 host adapter。性能/稳定性使用 synthetic contract report；安全/
隐私使用固定 redacted projection；release/field diagnostics 仅查询 build-owned package metadata counts；driver-safety probe 不读取真实
driving scalar、CarProperty、Vendor Service、device node、ioctl、sysfs、PCIe 或 NPU。

因此不新增 C/C++、Driver/HAL 或虚拟化开发量，`driver_development_triggered=false`、
`virtualization_development_triggered=false`。真实 Safety State、HVAC/Seat capability/readback、NPU telemetry、fault injection 和 target
performance 只有在 P8 capability matrix、owner/version/permission/API evidence 完整后才可立项；不得从 debug probe 反推 vendor 接口。

当前 `p9_android13_arm64_probe_acceptance_complete=true`、`driver_safety_android13_arm64_verified=false`、
`driver_safety_vehicle_state_provider_wired=false`、`driver_safety_effect_runtime_wired=false`、`driver_hal_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`。tracking：`DEV-109`、
`ISSUE-029/030/048..053`。

## P9-W03d Binder Identity Evidence Driver/HAL Boundary

W03d 只调用 Android framework Binder、PackageManager 和 Java SHA-256。它不读取 Android Car、VehicleProperty、Vendor Binder/SOA、
CAN、device node、sysfs、ioctl、PCIe/NPU、JNI 或网络。测试中的 UID/package/signer 都属于应用安装与 Binder 调用身份，不是车身信号。

因此本增量不新增 C/C++、Driver/HAL 或虚拟化开发量，`driver_development_triggered=false`、
`virtualization_development_triggered=false`。只有 P8 证明公开/Vendor 应用 API 无法提供所需 identity/security primitive 且 owner/ABI/evidence
评审通过后，才允许建立最小平台缺口；当前没有该缺口。

当前 `security_identity_device_probe_verified=true`、`security_production_signer_verified=false`、
`driver_hal_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。tracking：`DEV-111`、`ISSUE-050`。

## P9-W03e Callback Replay Driver/HAL Boundary

本增量只涉及 Android app Binder、PackageManager-derived owner fingerprint、SDK callback admission 与 debug test resource overlay。它不读取
VehicleProperty、CAN、device node、sysfs、Vendor NPU、TEE 或 Driver/HAL 状态，因此 `driver_development_triggered=false`。跨 UID owner isolation
由 Android Binder identity 和现有 capability policy 完成，不需要新增内核或厂商接口。

若未来 production signer/attestation owner 要求硬件背书，必须先取得 OEM SDK/permission/ABI/evidence；不得从 W03e 推导或新增驱动。
当前 `security_task_callback_replay_android_verified=true`、`security_debug_test_principal_release_excluded=true`、
`driver_hal_accessed=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`。
tracking：`DEV-112`、`ISSUE-050`。

## P9-W03f Parser Robustness Driver/HAL Boundary

本增量只在 host JVM test source set 调用既有 Java parser/validator。Jazzer dependency 使用独立 test-only Gradle configuration；运行器不连接
ADB、网络、VehicleProperty、CAN、device node、sysfs、Vendor NPU、TEE 或 Driver/HAL，因此不新增 C/C++、Driver/HAL 或虚拟化开发量。

Android Binder/Parcel/native target campaign 只有在安全 owner 冻结 surface、预算、sanitizer、evidence 和 OEM/Vendor 权限后才能新增，当前不得
猜测厂商接口。`driver_development_triggered=false`、`virtualization_development_triggered=false`、
`security_parser_robustness_host_campaign_verified=true`、`security_coverage_guided_fuzz_complete=false`、
`driver_hal_accessed=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`。
tracking：`DEV-113`、`ISSUE-050`。

## P9-W03g Suspended Security Evidence Driver/HAL Boundary

W03f 执行面已删除；W03g 只保留静态 JSON evidence interface，不包含 Java runtime、AIDL、JNI、C/C++、Android component、network transport、
Driver/HAL 或虚拟化代码。不会因该挂起需求新增厂商 SDK、property、device node 或驱动接口。

当前 `security_requirement_suspended=true`、`security_test_implementation_present=false`、
`security_test_execution_enabled=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false`、
`driver_hal_accessed=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`。
tracking：`DEV-114`、`ISSUE-050`。

## P5-R1 Runtime Composition Driver/HAL Boundary

P5-R1 全部位于 Android Java debug source set，只组合 build-owned metadata Tool、compiled-in Skill admission、metadata budget
和 process-local digest Working Memory。它不调用 JNI/C ABI、Vendor SDK、VehicleProperty、CAN、device node、sysfs、NPU、DMA、
IOMMU 或 Driver/HAL，因此不新增 C/C++/驱动开发量。

真实 Vehicle/Model/NPU 接入仍必须等待 P8 capability evidence 和 OEM/Vendor owner 接口；不得从 debug composition 推导接口。
`driver_development_triggered=false`、`driver_hal_accessed=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P5-R1`。tracking：`DEV-117`。

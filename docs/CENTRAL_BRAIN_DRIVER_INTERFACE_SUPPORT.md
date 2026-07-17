# 驱动层接口支持矩阵

版本：2.7

日期：2026-07-17

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
replay、Service rebind、active resubscribe、cancel 和 close；调用链没有进入 JNI/C ABI、Vehicle/VHAL、
Vendor NPU、PCIe、DMA/IOMMU、SharedMemory、device node、ioctl/sysfs、CAN/DBC 或 Safety Runtime，报告
`hardware_accessed=false`。

当前 `session_runtime_service_published=true` 和 `event_callback_service_published=true` 只表示应用层
Binder 可用，不表示 Driver/HAL 或 production Event broker。`session_runtime_persistence_wired=false`、
`session_runtime_process_death_rehydration=false`、`scenario_execution_enabled=false`；Effect/approval
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

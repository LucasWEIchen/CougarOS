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
`implementation_stage=P5-W06`。Req IDs：`S2-TOL-001`、`S2-SAF-001`、`XSC-001/005/006`、`KH-003/006/007`、
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
`implementation_stage=P5-W06`。Req IDs：`S2-TOL-001`、`S2-SAF-001`、`XSC-001/005/006`、`KH-003/006/007`、
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
`virtualization_development_triggered=false`、`implementation_stage=P5-W06`。Req IDs：`S2-TOL-001`、`S2-SAF-001`、
`FW-U-008`、`XSC-001/005/006`、`KH-003/006/007`、`DEL-004/005`；tracking：`DEV-067`、`ISSUE-040`。

# Central Brain P9 驾驶分心与车辆安全准入设计

版本：1.0
日期：2026-07-18
状态：`SOFTWARE_CONTRACT_DEFINED / OEM_OWNER_AND_TARGET_EVIDENCE_OPEN`

## 1. 目的与需求映射

P9-W06a 将分散在 UI、Scenario、Governance 和模拟 adapter 中的驾驶态判断收敛为一个纯 Java、
确定性、失败关闭的准入合同。它映射 `S2-UX-002`、`S2-SAF-001`、`S2-EFF-001`、
`S2-OBS-001`、`DEL-001/004/005`，并继续受架构图基线约束。

本增量只回答“该 UI 或动作是否可以进入下一治理阶段”，不执行 Effect，不修改车辆，不产生批准，
也不把 UI 确认当作硬联锁。真实 Safety State、OEM 策略和 capability activation 仍由 P8 目标集成提供。

## 2. 工程文件

| 文件 | 责任 |
| --- | --- |
| `central-brain/contracts/central_brain_android_p9_driver_safety_admission.json` | 机器可读策略、Req ID 和真实性边界 |
| `DriverSafetyAdmissionContract.java` | action catalog、UX 投影、owner/capability 准入和 decision digest |
| `DriverSafetyAdmissionContractTest.java` | stale/untrusted/moving/owner/capability/hard-interlock 回归 |
| `tools/check_central_brain_android_driver_safety_admission.sh` | JSON/Java/test/docs/禁止接线同步门禁 |

## 3. 输入接口

```java
Decision evaluate(AdmissionRequest request);
```

`AdmissionRequest` 精确包含：

- `actionId`：只能来自 12 项 build-owned catalog，caller 不能声明 risk；
- `SafetyVehicleStateSnapshot`：Runtime-owned immutable snapshot；
- `observedAtElapsedRealtimeMs`：调用方注入的单调时钟读数；
- `PolicyProfile`：profile/schema/catalog digest 和三个 owner role approval；
- `CapabilityEvidence`：车辆 capability ID、production availability、authorization、readback 和 activation digest。

状态必须在 500 ms 内，capture time 不得晚于 observe time，且 source 必须为
`PLATFORM_TRUSTED_ADAPTER + hardwareBacked=true`。当前 `RUNTIME_OWNED_STUB` 永远不能通过生产准入。

## 4. 固定 Action Catalog

| Action | 类别 | Moving | Capability | 准入输出 |
| --- | --- | --- | --- | --- |
| `vehicle.state.read` | READ_ONLY_UI | 允许 | 无 | UI-only |
| `scene.intent.submit` | LOW_RISK_UI | 允许 | 无 | UI-only |
| `session.cancel` | LOW_RISK_UI | 允许 | 无 | UI-only |
| `ui.long_text.display` | DRIVER_DISTRACTION_UI | 禁止 | 无 | UI-only |
| `ui.parameter.edit` | DRIVER_DISTRACTION_UI | 禁止 | 无 | UI-only |
| `driver.display.video.play` | DRIVER_DISTRACTION_UI | 禁止 | 无 | approval required |
| `cabin.temperature.set` | COMFORT_EFFECT | 允许 | HVAC target temperature | policy-only |
| `vehicle.seat.driver.heating.set` | COMFORT_EFFECT | 允许 | Seat heating | policy-only |
| `vehicle.seat.driver.ventilation.set` | COMFORT_EFFECT | 允许 | Seat ventilation | policy-only |
| `vehicle.seat.driver.recline.set` | SAFETY_CRITICAL_EFFECT | 硬禁止 | Seat recline | approval required |
| `vehicle.diagnostics.write` | ENGINEERING_WRITE | 禁止 | 无 | approval required |
| `system.ota.install` | OTA | 禁止 | 无 | approval required |

`ALLOW_POLICY_ONLY` 仅表示合同前置条件满足，不是 Effect authorization。所有 decision 的
`isEffectDispatchAuthorized()` 和 `isHardwareOperationExecuted()` 固定返回 false。

## 5. UX Profile 投影

| Profile | 触发条件 | UI 边界 |
| --- | --- | --- |
| `PARKED_FULL` | fresh、production-trusted、Safety NORMAL、PARKED | 可进入完整驻车 UX，仍需后续策略 |
| `MOVING_RESTRICTED` | fresh、production-trusted、Safety NORMAL、MOVING | 禁止长文本、参数编辑、视频、诊断、OTA 和驾驶席 recline |
| `UNKNOWN_RESTRICTED` | missing/stale/future/untrusted/UNKNOWN motion | 只保留低风险输入、读取和取消，不授予 Effect |
| `FAULT_RESTRICTED` | Safety 非 NORMAL | 只保留错误恢复，不授予受控动作 |

产品 UX 中的 `IDLE` 需要可靠的 gear/speed/parking-brake 联合语义。当前黑盒目标没有该 production
mapping，因此 P9-W06a 不伪造 IDLE；在 P8 owner evidence 可用前按 PARKED 或 UNKNOWN 处理。

## 6. Owner Policy

准入要求以下三个唯一角色同时批准同一 `PROFILE_ID + schemaVersion + catalogDigest`：

1. `FUNCTIONAL_SAFETY`；
2. `DRIVER_DISTRACTION_HMI`；
3. `VEHICLE_INTEGRATION`。

每个 approval reference 只以 64 位十六进制 digest 进入合同，三个 digest 必须唯一。当前仓库的
`currentDraftPolicy()` 不包含任何 owner approval，所以生产 UI 限制、HVAC/Seat 动作、诊断和 OTA
都会失败关闭。JVM 中的完整 approval 仅是 synthetic contract fixture，不是 OEM 签署。

## 7. Capability Gate

车辆 Effect 必须同时满足：

1. capability ID 与 action rule 精确一致；
2. `productionAvailable=true`；
3. `productionAuthorized=true`；
4. 需要 readback 的 HVAC/Seat capability 必须 `readbackAvailable=true`；
5. activation evidence digest 合法。

当前 P2 capability catalog 的 production available/authorized 均为 false，P8-W01 也仍因 vendor
property/service/permission/owner 输入缺失而外部阻塞。P9-W06a 不改变这些值。

## 8. 判定顺序

```text
Action allowlist
 -> state presence/time/freshness
 -> production-trusted source
 -> Safety NORMAL
 -> known motion
 -> moving hard interlock
 -> driver availability
 -> exact three-role owner policy
 -> capability availability/authorization/readback/activation
 -> UI-only / policy-only / approval-required
```

第一个失败项产生稳定 `DecisionCode` 和 SHA-256 `decisionDigest`。digest 绑定 catalog、action rule、
UX profile、state metadata、policy metadata 和 capability metadata，但不包含用户文本、车辆 scalar、
设备身份或原始日志。

## 9. 当前边界与后续

```text
driver_safety_admission_defined=true
driver_safety_action_rule_count=12
driver_safety_owner_role_count=3
driver_safety_state_maximum_age_ms=500
driver_safety_moving_hard_interlock_verified=true
driver_safety_current_owner_policy_approved=false
driver_safety_vehicle_state_provider_wired=false
driver_safety_effect_runtime_wired=false
driver_safety_android13_arm64_verified=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

P9-W06b 已增加 DUMP-protected debug-only redacted probe 和只读 target evidence adapter。该软件入口当前
未在目标设备执行，真实 owner 签署、driver-distraction acceptance matrix、车辆 signal producer、座椅硬联锁
和 Effect activation 继续由 `ISSUE-029/030` 及 P8 外部工作包跟踪。

## 10. P9-W06b 脱敏 Android 探针

W06b 以 `DriverSafetyAuditProjection.evaluateCurrentRepository()` 重新计算 W06a build-owned catalog 的固定元数据，
不接受 Runtime、车辆、owner 或 capability 输入。投影精确输出 27 个有序 key，仅包含计数和布尔值：12 个 action、
4 个 UX profile、3 个 owner role、500 ms 最大状态年龄、0 个当前 owner approval、6 个 moving blocked action、
4 个 vehicle Effect action、4 个 approval-required action 和 0 个 production-authorized capability。

`DriverSafetyAuditProbeActivity` 只存在于 debug source set，要求 `android.permission.DUMP`，并且只接受 1..24 位数字
nonce。它不读取 Android Car、VHAL、Vendor Binder、车辆 scalar、设备身份、owner approval reference、原始日志或
业务 payload；main/release manifest 不暴露该入口。投影也未接入 Runtime/Governance Service。

`tools/probe_central_brain_android_driver_safety.sh` 仅对已经安装的 debug Runtime 执行只读 probe。它要求 Android 13
API 33、ARM64 和在线 transport，但不 build、install、uninstall，不读取 vehicle state，也不保存或打印原始 logcat。
成功执行只证明当前 APK 内的软件合同可运行；`driver_safety_android_contract_probe_android13_arm64_verified=true`
不得提升为 `driver_safety_android13_arm64_verified=true`，后者仍要求真实 Safety source、OEM owner 和目标策略证据。

```text
driver_safety_redacted_projection_defined=true
driver_safety_audit_key_count=27
driver_safety_android_debug_probe_available=true
driver_safety_android_debug_probe_executed=false
driver_safety_target_adapter_defined=true
driver_safety_current_owner_policy_approved=false
driver_safety_vehicle_state_provider_wired=false
driver_safety_effect_runtime_wired=false
driver_safety_android13_arm64_verified=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

Req IDs：`S2-UX-002`、`S2-SAF-001`、`S2-EFF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：
`DEV-097`、`ISSUE-029/030`。

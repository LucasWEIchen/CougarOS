# 虚拟化与 Safety 接口约束

版本：1.0

日期：2026-07-16

## 范围声明

本仓库不开发 Hypervisor、VM 生命周期、ASIL/QM 隔离机制、vDevice、共享内存驱动、doorbell、
中断或 hypercall。本文只固定 Android AIOS Runtime 与目标平台既有 Safety/跨域能力之间的合同。

Req ID：`HV-001`、`HV-002`、`HV-003`、`FW-S-005`、`NV-G-005`、`NV-F-009`、
`KH-007`、`DEL-004`。

## 外部责任边界

| Req ID | 外部模块 | 本仓库职责 | 外部 owner 职责 |
| --- | --- | --- | --- |
| `HV-001` | Hypervisor | 记录 domain/transport/启动依赖 | 实现调度、隔离和 VM 生命周期 |
| `HV-002` | ASIL/QM | 定义服务风险和 fallback 语义 | 认证隔离强度和故障覆盖 |
| `HV-003` | 跨 VM channel | 定义 typed envelope 和有界 payload | 提供 virtio/shared-memory/vendor IPC |
| `KH-007` | Safety Runtime | 消费不可伪造的 safety snapshot | 提供可信状态、fault/interlock 和恢复 |

## Safety State

Android Runtime 的 `SafetyVehicleStateProvider` 当前没有真实车辆来源。目标 adapter 到位后至少提供：

- gear、speed、occupancy、seat belt、door/seat state；
- source、capture time、freshness、quality、revision 和 digest；
- `NORMAL`、`DEGRADED`、`DIAGNOSTIC_READONLY`、`INTERLOCKED` 或 `UNAVAILABLE`；
- status 的可信 owner 和错误恢复语义。

未知、过期、低质量或来源未认证的状态必须限制车辆写操作。驾驶员确认不能覆盖 hard interlock。

## 跨域 Envelope

跨 VM 通信必须保留 Uni Info Bus/SOA 语义，不得把底层 channel 直接暴露给 App：

```text
trace_id
source_domain / target_domain
caller_principal_digest / capability
schema_id / operation_id / payload_ref
safety_state_revision / policy_decision
deadline_elapsed_ms / idempotency_key
result_status / fault_code / audit_digest
```

约束：

1. `payload_ref` 必须有 size、lifetime、ownership、cache/coherency 和 revoke 规则。
2. 共享内存不能绕过 schema、Policy、Lifecycle、Audit 或 effect idempotency。
3. 远端域不可用时只能返回明确失败或 readonly fallback，禁止不受控写操作。
4. deadline 使用 elapsed realtime；跨域 wall clock 只能用于展示和审计关联。
5. 远端 reset/restart 后未知 effect 必须 query/reconcile，不得盲目重放。

## Android 集成边界

| 维度 | Android Runtime 合同 | 激活前证据 |
| --- | --- | --- |
| 身份 | Binder caller + current signer + capability | package/signer/SELinux/system owner |
| Safety | trusted snapshot provider | source API、freshness、fault injection、interlock |
| Transport | vendor AIDL/NDK/native service adapter | interface version/hash、death、timeout、size |
| Shared buffer | AHardwareBuffer/SharedMemory/native descriptor | ownership、IOMMU、cache、bounds、revocation |
| Recovery | effect status/reconcile/compensate | remote restart、duplicate、timeout、rollback matrix |
| Audit | digest metadata only | target log owner、retention、privacy、trusted clock |

当前普通 APK 不扫描系统 service 或 device node 来猜测这些能力。系统能力必须由 OEM/Vendor
发布合同和目标 owner 明确提供。

## 降级策略

| 场景 | 允许的 fallback | 禁止行为 |
| --- | --- | --- |
| Safety source unavailable | readonly last-known state，标注 stale；或失败 | 继续执行车辆写操作 |
| 车辆 adapter death | effect 进入 unknown，query/reconcile | 自动重复 dispatch |
| NPU domain unavailable | 策略明确允许的其他 provider；否则失败 | 静默使用未治理模型服务 |
| 跨域 channel unavailable | bounded retry/timeout/失败 | 在 App 内绕过 Governance |

## 验证边界

当前只可验证文档、Android fail-closed gate 和 no-activation 状态：

```bash
bash tools/check_central_brain_virtualization_docs.sh
bash tools/check_central_brain_android_runtime_evolution.sh
```

不可验证 Hypervisor 启动、ASIL/QM 认证、真实跨 VM 共享内存、Safety Runtime fault coverage 或
Driver/HAL 联动。`virtualization_development_triggered=false`、`target_hardware_validated=false`、
`production_ready=false` 必须保持。

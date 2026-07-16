# NPU Runtime Adapter 接口约束

版本：1.0

日期：2026-07-16

## 范围声明

本文固定 Android AIOS Runtime 到外置 PCIe NPU 或 SoC NPU 的模型运行时边界。保留大模型底座、
Provider 生命周期、JNI/C ABI 和 Driver/HAL 集成要求；已退役的 Python/Ollama gateway 不属于该边界。

Req ID：`HW-002`、`NV-F-001`、`NV-F-011`、`NV-F-012`、`NV-G-004`、`NV-G-005`、`NV-G-006`、`NV-G-007`、
`KH-003`、`KH-006`、`KH-007`、`DEL-001`、`DEL-003`、`DEL-004`、`DEL-005`。

## 当前 Android 实现

| 组件 | 路径 | 当前能力 | 激活状态 |
| --- | --- | --- | --- |
| Model contract | `runtime-service/.../model/ModelProvider.java` | descriptor/health/warmup/infer/stream/cancel/metrics/fault/close | contract available |
| Provider profile | `ModelProviderProfiles.java` | `deterministic.stub`、`vendor.npu.empty` | Vendor unavailable |
| Scheduler | `InferenceResourceScheduler.java` | priority/deadline/quota/provider slot/cancel directive | production unwired |
| Test provider | `DeterministicStubModelProvider.java` | deterministic bytes、stream/cancel/fault | test/debug only |
| Test router | `TestOnlyModelRouter.java` | Scheduler/Provider contract composition | test/debug only |
| Readiness | `ModelRuntimeReadinessSnapshot.java` | log/dumpsys/Diagnostics blockers | read-only |
| Native boundary | `native-runtime` | C ABI V1、JNI、process lifecycle/capacity | no vendor dispatch |

Android R5B2 Test-Only Model Router 只证明调度和 Provider 合同可组合，不证明生产推理。
Android R5C1 model-runtime readiness 只暴露 activation blocker，不探测或访问 NPU。

## 分层调用

```text
Agent/Scenario Runtime
  -> policy-aware Model Router
  -> InferenceResourceScheduler
  -> ModelProvider
  -> Vendor Java/AIDL/NDK adapter
  -> JNI/C provider ABI when required by published SDK
  -> Vendor NPU runtime
  -> Driver/HAL/PCIe/IOMMU/Firmware
```

App、Client2 和模型输出不得直接调用 Vendor SDK、native handle、device node 或 reset。

## ModelProvider 合同

Vendor provider 必须映射以下操作：

| 操作 | 输入 | 输出 | 约束 |
| --- | --- | --- | --- |
| descriptor | provider/version/model/capability | immutable descriptor | hardware/production claim 必须有证据 |
| health | none | lifecycle/health/detail/slot | 不得用常量 HEALTHY 代替硬件状态 |
| warmup | model/profile/deadline | async result | 可取消、有界、失败结构化 |
| infer | request/digest/buffers/deadline | async chunks/result | input/output 有界，单终态 |
| stream | request + consumer | ordered chunks | backpressure、chunk <= 64 KiB |
| cancel | request ID/reason | acknowledgement | 幂等，不把 ack 等同于停止完成 |
| metrics | bounded window | queue/latency/error/resource | 不包含原始输入输出 |
| fault | code/detail/recoverability | state transition | fault 后禁止新请求或明确 degraded |
| close | provider instance | terminal lifecycle | 等待/取消 in-flight，释放顺序明确 |

Provider descriptor 至少包含 assurance、runtime version、model set、supported operations、max slots、
buffer types、stream limits、cancel semantics、health source 和 evidence reference。

## Vendor C ABI 扩展边界

现有 `central_brain_native.h` 是 Runtime lifecycle ABI，不是完整 NPU inference ABI。只有公开 Vendor SDK
要求 native 适配时，才新增独立版本化 provider vtable。必须满足：

- 所有 public struct 以 `struct_size`、`abi_version` 开头；
- 固定宽度整数、显式 enum、caller-owned output 和有界 buffer；
- provider handle/lease 所有权、线程安全和 destroy 顺序明确；
- JNI 使用 `RegisterNatives`，不缓存 `JNIEnv*`，不把 Java/Binder authority 传入 C；
- adapter 不直接持久化 raw prompt、token、模型输出或车辆 payload；
- ABI 不稳定、SDK 缺失或版本不匹配时返回 unavailable。

## NPU 最低抽象

| 抽象 API | 目的 | 失败关闭要求 |
| --- | --- | --- |
| `NpuDevice.discover()` | 枚举受支持设备/版本/能力 | 只读；不得猜测 device node |
| `NpuDevice.getStatus()` | health/thermal/power/queue/fault | fault/interlock 不得返回 ready |
| `NpuModel.load(model_spec)` | 签名、版本、shape、memory | 验证失败拒绝加载 |
| `NpuModel.unload(model_id)` | 生命周期回收 | 等待/取消规则明确并审计 |
| `NpuSession.create(model_id, qos, safety_state)` | 建立受治理 session | Policy/slot/deadline 准入 |
| `NpuSession.infer(input_buffers, output_spec, timeout_ms)` | 有界推理 | async、cancel、single terminal |
| `NpuSession.cancel(request_id)` | 取消请求 | 幂等，状态可查询 |
| `NpuDevice.getMetrics()` | 资源与性能 | 不泄露 payload |
| `NpuDevice.reset(reason)` | 受控恢复 | 仅平台 owner；App 不可调用 |

## 状态机

`UNAVAILABLE -> INITIALIZING -> READY -> DEGRADED -> FAULT_ISOLATED -> CLOSED`

任何状态都可因 Policy/Safety 进入 `INTERLOCKED` 视图。`READY` 只能由真实 runtime/device health、
版本和权限证据产生；empty provider 永远不能进入 `READY`。

| 错误码 | 条件 | 调用方处理 |
| --- | --- | --- |
| `NPU_DEVICE_UNAVAILABLE` | SDK/runtime/device 不存在 | 返回 unavailable，不伪造结果 |
| `NPU_PERMISSION_DENIED` | Binder/SELinux/vendor ACL 不满足 | 停止并记录安全审计 |
| `NPU_POLICY_DENIED` | privacy/safety/resource policy 拒绝 | 不调用 provider |
| `NPU_MODEL_REJECTED` | 签名/版本/shape/format 不满足 | 拒绝 load/infer |
| `NPU_RESOURCE_EXHAUSTED` | memory/slot/queue 超限 | 有界等待或失败 |
| `NPU_TIMEOUT` | queue/infer deadline | cancel/query/reconcile |
| `NPU_FAULT_ISOLATED` | PCIe/IOMMU/firmware/runtime fault | 隔离并禁止新任务 |
| `NPU_RESULT_INVALID` | schema/size/digest/output guard 失败 | 丢弃结果，不执行 Effect |
| `NPU_BACKEND_FALLBACK_REQUIRED` | 当前 provider 不可用 | 仅由 Model Router policy 选择其他已批准 provider |

## Buffer 与数据安全

真实接入前必须确认 AHardwareBuffer/SharedMemory/DMA-BUF 类型、alignment、shape、stride、cache
coherency、IOMMU domain、map/unmap、lease、zeroization 和进程死亡回收。file descriptor 不进入 AIDL
除非接口显式定义 ownership；raw prompt/output 不写普通 log 或 GitHub evidence。

## Driver/HAL 激活检查

| Gate | 必须输入 | 未满足结果 |
| --- | --- | --- |
| Owner | OEM/Vendor/Android/Driver/Safety owner | blocked |
| API/ABI | SDK 版本、service/AIDL/hash、native header/library | blocked |
| Permission | package/signer/SELinux/vendor ACL | blocked |
| Buffer | memory/coherency/IOMMU/ownership | blocked |
| Model | artifact signature/version/rollback/storage | blocked |
| Runtime | lifecycle/death/cancel/timeout/status | blocked |
| Fault | PCIe reset、runtime death、thermal、firmware、unknown result | blocked |
| Evidence | target smoke/performance/long-run/privacy/rollback | blocked |

当前 `DRV-GAP-001` 保持开放。不得实现推测性 ioctl、DMA、sysfs、PCIe reset 或 vendor symbol 调用。

## 当前状态

- Android 13 物理设备只通过应用层 Runtime/Demo/Client2 验收，未访问 NPU。
- `vendor.npu.empty` 为 `UNAVAILABLE`、0 slot、不可配置、不可路由。
- deterministic provider 和 test router 仅存在于 test/debug 合同路径。
- Native Runtime 只验证 C ABI/JNI lifecycle，不加载 Vendor NPU SDK。
- 不存在 Python/Ollama gateway fallback。
- `hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
  `driver_development_triggered=false`、`virtualization_development_triggered=false`。

## 验证

```bash
bash tools/check_central_brain_npu_interface.sh
bash tools/check_central_brain_android_model_provider_contract.sh
bash tools/check_central_brain_android_inference_scheduler.sh
bash tools/check_central_brain_android_model_runtime_readiness.sh
bash tools/test_central_brain_android_native_runtime.sh
```

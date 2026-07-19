# Central Brain Android 工程

`central-brain/` 当前只承载 Android 13 实际工程、Android 验收合同和 Android hybrid 交付配置。
早期 Python gateway、Linux 样例和旧 Android Console 已退役；本目录下不得出现 Python Runtime。

Req ID：`APP-004`、`XSC-001`、`XSC-004..006`、`NV-F-001/011/012`、
`NV-G-003/005/006/007`、`NV-P-002`、`KH-003/006`、`DEL-001/003/004/005`。

根 [README](../README.md) 是 GitHub 首页的架构和开发状态入口。所有本目录正式源码、合同和
交付配置都必须由 Git 跟踪，并在完成增量的同一轮 commit/push；模块或状态变化必须同步首页的
Mermaid 架构图、已开发/未开发表和近期记录。`github_source_of_truth=true`、
`github_sync_required=true`。

## 目录

| 路径 | 职责 |
| --- | --- |
| `android-runtime/central-brain-sdk` | Java SDK、typed task/Governance/Session/Plan/Event/Effect AIDL、Binder client |
| `android-runtime/runtime-service` | Runtime/Governance/Diagnostics、Room、Model/Event/Memory/Skill/Effect |
| `android-runtime/native-runtime` | C ABI V1、JNI、Native Runtime lifecycle |
| `android-runtime/demo-hmi` | 维护与验收 HMI |
| `android-runtime/policy-probe` | testOnly capability 负向探针 |
| `contracts/central_brain_android_b3_blackbox_acceptance.json` | 黑盒环境验收合同 |
| `contracts/central_brain_android_r7c_acceptance.json` | Binder/Client2/恢复验收合同 |
| `contracts/central_brain_github_remote_testing.json` | 内网硬件远程测试合同 |
| `delivery/android-hybrid` | APK/AAR 交付 profile、inactive slots、目标输入模板 |

## 调用关系

```text
Client2 / Demo
  -> central-brain-sdk
  -> typed Runtime/Governance/Diagnostics Binder
  -> identity + capability + policy
  -> durable task/effect/approval/audit
  -> ModelProvider / EffectAdapter activation gates
  -> Native C ABI or target adapter only after owner evidence
```

当前 Binder task path 仍使用有界 deterministic 行为完成应用层验收；生产 Scheduler、Model Router、
Effect dispatch、车辆服务和 Vendor NPU 不得从该行为推断为已接入。

Stage 2 `P1-W01` Session、`P1-W02` Plan/Node、`P1-W03` Event/callback、`P1-W04` Effect/Approval
V1 合同，`P1-W05` SDK facade、`P1-W06` Room v4 和 `P1-W07` Runtime Contract v2 聚合门禁均已完成。
Session/Event app-layer Binder 与进程死亡恢复已发布。P1-W05 facade
在显式 reconnect 时先对旧 Event Binder 对称注销 callback，再解绑双 Binder；Android 13/API 33 ARM64
已通过连续 6 次健康重连、replay 去重、cancel 与 process-death recovery，固定标记
`healthy_reconnect_callback_cleanup_verified=true`。`plan_runtime_published=false`、
`effect_runtime_service_published=false`、`approval_response_service_published=false`、
`undo_service_published=false`、`scenario_execution_enabled=false`，不能把 Session/Event publication
描述为完整 Graph/Effect 或车辆控制链。

P9-W03d 已新增仅存在于 debug/androidTest source set 的 typed Binder identity probe。Runtime 在 transaction
内通过 `Binder.getCallingUid()` 和 PackageManager current signer SHA-256 验证调用方，SDK instrumentation 在
Android 13/API 33 ARM64 上完成 UID/package/signer spoof 负例。该接口不返回 raw identity，release 不包含 probe；
`security_identity_device_probe_verified=true`，但 production signer、coverage fuzz、Runtime wiring 与目标资格仍为 false。

计划中的 Client2 HVAC/Seat 手动控件和“我冷了/我累了”场景必须走同一条 SDK -> Session ->
Governance -> Durable Effect -> readback 链路。Android debug/test 可使用持续标记为 `SIMULATED` 的
Digital Twin；release/production 不允许在 target adapter unavailable 时隐式回退仿真。当前
`cockpit_demo_control_loop_implemented=false`，详见
[`CENTRAL_BRAIN_COCKPIT_HMI_CONTROL_LOOP_PLAN.md`](../docs/CENTRAL_BRAIN_COCKPIT_HMI_CONTROL_LOOP_PLAN.md)。

## 模型与硬件边界

- `ModelProvider.java` 是模型底座的正式 Java contract。
- `ModelProviderProfiles.java` 的 `vendor.npu.empty` 保持 `UNAVAILABLE`、0 slot、不可路由。
- `InferenceResourceScheduler.java` 只管理受信 priority/deadline/quota/provider slot。
- `native-runtime` 固定 C/JNI 接入边界，但当前不加载 vendor SDK、不访问 device node。
- `EffectDeliveryActivationGate` 和 `EmptyEffectMaterialSource` 保证未授权车辆副作用失败关闭。
- Driver/HAL 只在目标公开能力不足且 ABI/owner/evidence 明确后增加。

## 构建

```bash
bash tools/build_central_brain_android_runtime.sh
bash tools/test_central_brain_android_native_runtime.sh
bash tools/check_central_brain_android_runtime_evolution.sh
```

物理设备安装和完整交付见
`docs/CENTRAL_BRAIN_ANDROID13_HYBRID_INSTALLATION_AND_USAGE.md`。

## 退役约束

`python_prototype_runtime_maintained=false`。Python 只允许存在于仓库外层的确定性构建工具中；
不得在本目录恢复 HTTP gateway、Ollama 模型代理、Linux daemon/CLI 或车辆 mock 服务。

验证：

```bash
bash tools/check_central_brain_python_prototype_retirement.sh
```

全局状态保持 `production_ready=false`、`target_hardware_validated=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。

## P9-W03e callback replay security

`central-brain-sdk` 的 `TaskCallbackReplayGuard` 现在把回调绑定到 Runtime 返回的 task ID，只接收严格递增 sequence，丢弃 duplicate/stale
update，并保证 completion/failure 终态只交付一次。Android debug capability overlay 仅为 `com.centralbrain.sdk.test` 提供任务测试权限；main/release
policy 不包含该主体。API 33 ARM64 已用 `com.centralbrain.demo` 与 `com.centralbrain.sdk.test` 两个不同 owner UID 验证同 owner 重放、冲突静默、
终态重放和跨 UID owner 隔离。`security_task_callback_replay_android_verified=true`；coverage fuzz、production signer 与目标资格仍为 false。

## P9-W03f bounded parser robustness campaign (retired)

该短预算 host campaign 曾在 2026-07-19 形成历史证据。按用户明确决策，其引擎依赖、Gradle task、Java target/test、seed、runner 和原 machine
contract 已从当前仓库删除，不再构成当前工程能力，也不再提供执行命令。

## P9-W03g external security evidence interface

当前只保留 `central_brain_android_p9_security_evidence_interface.json`：它定义外部 owner 可提交的八个脱敏 metadata/digest/reference 字段，禁止
raw identity、signing material、credential、raw input/log 和 user/model/memory/vehicle payload。仓库没有 executor、Android/native component、
network transport 或自动执行路径，也不申请可信访问权限。

当前 `security_external_evidence_interface_defined=true`、`security_requirement_suspended=true`、
`security_test_implementation_present=false`、`security_test_execution_enabled=false`、
`security_external_evidence_admitted=false`、`production_ready=false`、`target_hardware_validated=false`。

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
| `android-runtime/central-brain-sdk` | Java SDK、typed AIDL、Binder client |
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

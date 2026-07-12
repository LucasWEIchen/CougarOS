# Central Brain Android 13 Hybrid 工程安装与使用指南

版本：1.0

日期：2026-07-12

状态：B4 hybrid software handoff verified on API 33 emulator

## 1. 交付范围

本交付面向只有已刷机 Android 13 座舱域控制器、ADB 安装能力和公开 Android API 的
集成团队。不要求厂商 AOSP/BSP/SDK 源码，不修改 system/vendor/boot 分区、SELinux 或
已编译系统组件。

| Artifact | 用途 | 是否安装到设备 |
| --- | --- | --- |
| `native-runtime-debug.aar` | C ABI V1、JNI 和 arm64/x86_64 `.so` | 否，供 Runtime/未来 adapter 集成 |
| `central-brain-sdk-debug.aar` | Java/AIDL Binder 客户端 SDK | 否，供座舱 App 编译集成 |
| `runtime-service-debug.apk` | Runtime、Diagnostic、Governance、Room、Native owner | 是，必须 |
| `demo-hmi-debug.apk` | 维护型 Binder/Room/Governance 验收 UI | 是，默认 |
| `client2-central-brain.debug.apk` | 原座舱 UI 上的 12 场景 Binder 浮层 | 可选，需 RenderService/签名许可 |

当前 APK 是 debug signer 的测试交付，不是量产签名包。真实 NPU/VHAL/车控/安全硬件均
为空接口，`native_vendor_npu_provider_available=false`、
`native_runtime_dispatch_enabled=false`、`hardware_accessed=false`。

## 2. 主机准备

需要 Linux/WSL 主机具备：

- JDK 17；
- Android SDK platform-tools 与 build-tools（`adb`、`aapt`、`apksigner`）；
- Python 3；
- Git；
- 一台已允许 ADB 调试的 Android 13/API 33 设备或验收模拟器。

在本仓库环境中：

```bash
cd /home/normad400/appDev
source env.sh
adb devices -l
```

只允许一个 online device，或在后续命令中始终指定 `--serial <serial>`。

## 3. 从源码构建

必须使用受控入口，避免 Runtime、Demo、Client2 因不同 `ANDROID_USER_HOME` 使用不同
debug keystore：

```bash
bash tools/build_client2_central_brain_demo.sh
```

该命令会先执行标准 Runtime 构建，再生成并签名 Client2 Binder demo。单独手工调用
Gradle 前必须显式统一 `ANDROID_USER_HOME`/keystore；否则 signer gate 会拒绝交付。

## 4. 生成并校验交付包

```bash
bash tools/package_central_brain_android_hybrid_delivery.sh
```

输出位于：

```text
builds/central-brain-android-hybrid-delivery/
├── central-brain-android13-hybrid/
├── central-brain-android13-hybrid.tar.gz
└── central-brain-android13-hybrid.tar.gz.sha256
```

独立校验解包目录：

```bash
python3 -B tools/central_brain_android_hybrid_delivery.py verify \
  --bundle-dir builds/central-brain-android-hybrid-delivery/central-brain-android13-hybrid
```

必须看到 `hybrid_delivery_bundle_verified=true`、`artifact_count=5`、
`native_artifact_count=2`、`signer_cohort_verified=true`。`SHA256SUMS` 覆盖 bundle 内每个
文件；外层 `.tar.gz.sha256` 必须通过受信任渠道核对，不能只依赖包内自述。

## 5. 填写目标输入

复制 bundle 中 `contracts/target-inputs.example.json` 到受控工作目录并填写实际设备、
owner、签名、后台策略、Client2/RenderService 和 rollback 决策。不得把模拟器值填写为
物理控制器证据。

若只在一次性测试设备使用 debug signer，可以暂时保留 template，但必须显式提供
`--allow-debug-signing`；该选项不代表 production signer 获批。

## 6. 安装前 dry-run

维护型 Runtime+Demo（默认，不含 Client2）：

```bash
bash tools/install_central_brain_android_hybrid_delivery.sh \
  --bundle-dir <bundle-dir> \
  --target-inputs <completed-target-inputs.json> \
  --serial <serial>
```

包含 Client2 的 dry-run：

```bash
bash tools/install_central_brain_android_hybrid_delivery.sh \
  --bundle-dir <bundle-dir> \
  --target-inputs <completed-target-inputs.json> \
  --serial <serial> \
  --include-client2
```

dry-run 会校验 API 33、arm64-v8a/x86_64 兼容性、bundle checksum、APK package/signer、
现有已安装包 signer 和普通 `/data/app` 边界。它不会安装或卸载任何包。

遇到 `SIGNER_MIGRATION_REQUIRED` 时立即停止，由目标签名/升级 owner 决定迁移；本工具
不会自动卸载旧包。遇到 ABI 或 API 不匹配时也不得绕过。

## 7. 执行测试安装

仅 Runtime+Demo：

```bash
bash tools/install_central_brain_android_hybrid_delivery.sh \
  --bundle-dir <bundle-dir> \
  --target-inputs <completed-target-inputs.json> \
  --serial <serial> \
  --execute \
  --allow-debug-signing
```

Runtime+Demo+Client2：

```bash
bash tools/install_central_brain_android_hybrid_delivery.sh \
  --bundle-dir <bundle-dir> \
  --target-inputs <completed-target-inputs.json> \
  --serial <serial> \
  --execute \
  --allow-debug-signing \
  --include-client2
```

安装顺序固定 Runtime -> Demo -> Client2。Client2 只有在目标 owner 已确认重签 APK 不会
破坏 RenderService/vendor allowlist 时才可安装。

## 8. 启动和使用

### 8.1 维护型 Demo HMI

```bash
adb -s <serial> shell am start -W -n com.centralbrain.demo/.DemoActivity
```

Demo 打开后会自动执行，无需点击。正常结果包括：

- `Typed Binder: connected v1`；
- `Typed Binder: completed`；
- `Replay: completed` 与 concurrent replay 完成；
- `Cancel: confirmed`；
- `Governance: verified v1`。

这些结果验证 Binder/callback/cancel/idempotency/Room/Governance 软件路径，不代表真实
模型推理或车控执行。

### 8.2 Client2 座舱浮层（可选）

```bash
adb -s <serial> shell am start -W \
  -n com.tuanjie.urasclient2/.MainActivity
```

右侧半透明浮层提供 12 个场景：`care.cold`（我冷了）、`care.fatigue`（我累了）、
`task.home`、`skill.nap`、`state.vehicle`、`memory.preference`、`skills.catalog`、
`governance.audit`、`security.denied`、`security.privacy`、`runtime.npu`、
`system.overview`。点击按钮后，面板通过 typed Binder 提交任务并在下方文本区显示进度和
结果；当前 APK 无 INTERNET/HTTP fallback。

Runtime 当前返回受控软件结果。`runtime.npu` 不会调用真实 NPU，任何界面文字都不能作为
硬件激活证据。

## 9. 诊断

Runtime 进程与 native readiness：

```bash
adb -s <serial> shell dumpsys activity service \
  com.centralbrain.runtime/.CentralBrainRuntimeService | \
  grep -E 'native_runtime|production_activation|target_hardware|hardware_accessed'
```

应看到：

```text
native_runtime_process_ready=true
native_runtime_abi_version=1
native_runtime_active_slots=0
native_vendor_npu_provider_available=false
native_runtime_dispatch_enabled=false
target_hardware_validated=false
hardware_accessed=false
```

查看关键日志：

```bash
adb -s <serial> logcat -d \
  -s CentralBrainRuntime:I CentralBrainNative:I CbClient2Binder:I '*:S'
```

完整 API 33 黑盒验收（需要源码检出，不是仅 bundle）：

```bash
bash tools/test_central_brain_android_blackbox_acceptance.sh \
  --skip-build --serial <serial>
```

## 10. 升级、回滚和卸载边界

升级前必须重新执行 dry-run 并核对 signer/version/hash。安全回滚是由 rollback owner 提供
上一受信版本 APK，按 Client2 -> Demo -> Runtime 依赖逆序重新安装；不得在不知道数据
兼容性和签名迁移策略时自动卸载。

本工具故意没有自动 uninstall/rollback。实验设备若需要人工清理，必须先导出验收证据，
再由授权 owner 执行既定 MDM/ADB 流程。生产设备必须遵循厂商 OTA/MDM/签名策略。

## 11. 接入真实厂商 adapter

1. 获取公开 NPU/VHAL/vendor service contract、用户态 ABI、权限和生命周期文档。
2. 在现有 Model Provider/Effect Adapter 边界新增 adapter；不要让 UI/Binder 绕过 Governance。
3. 只有公开 Java API 不足时才在 Native Runtime provider slot 后增加 C/C++ bridge。
4. 明确 buffer ownership、取消、timeout、process death、错误码、并发和版本协商。
5. 新增 arm64 目标测试、故障注入、性能/热/休眠恢复、安全和 signer/SELinux 证据。
6. 在证据通过前保持 Vendor NPU/VHAL provider unavailable，不修改 hardware flag。

## 12. 当前未完成的目标项

- 物理 Android 13 控制器 B3 evidence；
- production signer、升级、rollback、MDM/后台策略；
- Client2/RenderService 真机 trust；
- Vendor NPU、VHAL、车辆总线、Safety Runtime contract；
- production Effect/Model/Event/Memory/Skill-Governance activation；
- 性能、热、长稳、休眠唤醒、功能安全和整车验收。

因此本交付状态是 `hybrid_software_handoff_ready=true`，不是 production 或 physical-hardware
完成。七项 blocker 继续保留在 delivery profile 和 ISSUE-027 中。

## 13. 内网目标的 GitHub 远程测试

当维护者不能直接访问目标 ADB 时，使用
`CENTRAL_BRAIN_GITHUB_REMOTE_HARDWARE_TESTING.md` 的 B5 流程。Bundle 已携带
`tools/run_central_brain_android_remote_acceptance.sh`，用于在目标侧执行版本校验、dry-run、
可选安装和脱敏证据生成。测试人员通过私有 GitHub Issue 回传 `github-safe/` 内容；原始
ADB 标识、fingerprint、target-input 和未审查日志保留在内网。

该流程解决异步版本与问题传递，不提供外网到内网设备的控制通道，也不会使
`github_issue_intake_active`、`physical_controller_evidence_available` 或
`target_hardware_validated` 自动变为 true。

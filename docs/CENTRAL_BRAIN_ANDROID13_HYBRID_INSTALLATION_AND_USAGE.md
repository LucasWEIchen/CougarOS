# Central Brain Android 13 Hybrid 工程安装与使用指南

版本：1.0

日期：2026-07-14

状态：B4 hybrid software handoff verified；Runtime/Demo 已通过物理 API 33 ARM64 应用层验收

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

当 USB 与驱动由外层 Windows 11 管理时，推荐继续在 WSL 执行全部脚本，但把 `ADB` 指向
Windows platform-tools：

```bash
export ADB=/mnt/e/platform-tools/adb.exe
"$ADB" devices -l
```

该模式已在物理 Android 13 控制器验证。Central Brain 脚本会归一化 Windows ADB 的 CRLF
输出，并把调用方选择的 `ADB` 贯穿 preflight、signer guard、安装和验收。不要同时启动另一套
Linux ADB server；若改回 Linux ADB，先确保 USB 已通过 `usbipd-win` 转交 WSL。

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

遇到 `SIGNER_MIGRATION_REQUIRED` 时立即停止，由目标签名/升级 owner 决定迁移；hybrid installer
不会自动卸载旧包。遇到 ABI 或 API 不匹配时也不得绕过。

在允许清除原 Client2 及其应用数据的测试目标上，可由授权 owner 显式执行：

```bash
ADB=/mnt/e/platform-tools/adb.exe \
bash tools/install_client2_central_brain_demo.sh \
  --serial <serial> \
  --replace-conflicting-client2
```

该命令先尝试同包更新；只有 Android 明确返回 signer mismatch 且提供了上述开关时，才卸载
`com.tuanjie.urasclient2` 并安装 Runtime 同签 debug APK。其他安装错误不会触发卸载。完成后必须
重新执行包含 Client2 的 dry-run 和 Binder/UI 验收。

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

安装顺序固定 Runtime -> Demo -> Client2。Client2 只有在目标 owner 已确认同签更新或显式清除
原包的数据影响，并确认测试 APK 的 RenderService 路径后才可安装。

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
`system.overview`。点击按钮后，Java `CockpitControlCoordinator` 通过 typed Session/Event Binder 打开
owner-scoped Session，所有 snapshot/event/replay 先进入 immutable `CockpitHmiState` 与唯一 reducer，再由 render
projection 更新文本区。Activity recreate 或 Client2 进程重启时，面板用 app-private、schema-versioned、text-free
checkpoint 恢复原 Session/cursor/sequence；它不会持久化用户输入、模型输出或显示文本。当前 APK 无
INTERNET/HTTP fallback。P4-W02 只完成 HMI state/lifecycle/reconnect，Runtime 尚未执行 scenario/Graph/Effect，
P4-W03 四阶段意图界面和 HVAC/Seat 演示闭环仍未交付。

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
兼容性和签名迁移策略时执行替换。

hybrid installer 故意没有自动 uninstall/rollback。专用 Client2 installer 只提供显式
`--replace-conflicting-client2` 测试迁移，调用即表示 owner 接受原应用数据被清除；生产设备仍必须
遵循厂商 OTA/MDM/签名策略。

## 11. 接入真实厂商 adapter

1. 获取公开 NPU/VHAL/vendor service contract、用户态 ABI、权限和生命周期文档。
2. 在现有 Model Provider/Effect Adapter 边界新增 adapter；不要让 UI/Binder 绕过 Governance。
3. 只有公开 Java API 不足时才在 Native Runtime provider slot 后增加 C/C++ bridge。
4. 明确 buffer ownership、取消、timeout、process death、错误码、并发和版本协商。
5. 新增 arm64 目标测试、故障注入、性能/热/休眠恢复、安全和 signer/SELinux 证据。
6. 在证据通过前保持 Vendor NPU/VHAL provider unavailable，不修改 hardware flag。

## 12. 当前未完成的目标项

- Runtime/Demo 的物理 Android 13 B3 应用层 evidence 已于 2026-07-14 通过，详见
  `CENTRAL_BRAIN_ANDROID13_PHYSICAL_TARGET_TEST_REPORT.md`；
- production signer、升级、rollback、MDM/后台策略；
- Client2 debug 包已在用户明确授权的同包 signer 迁移后通过真机 Binder/UI/RenderService 应用层
  验收；production signer、OTA/MDM 升级、rollback 和量产 RenderService trust 仍未完成；
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

当前使用 Private `LucasWEIchen/CougarOS` 的
`android13-hwtest-v0.5.0-rc.2` Release。下载归档和 `.sha256` 到同一目录后执行
`sha256sum -c central-brain-android13-hybrid.tar.gz.sha256`；校验文件只记录 basename，不能使用
没有 Release 资产的撤回 RC1 标签。新 Issue 由 15 分钟维护自动化通过 `gh` CLI 轮询。

该流程解决异步版本与问题传递，不提供外网到内网设备的控制通道，也不会使
`physical_controller_evidence_available` 或 `target_hardware_validated` 自动变为 true。远端 Issue
intake 已激活为 `github_issue_intake_active=true`，但 tester access 和 branch protection 仍需外部输入。

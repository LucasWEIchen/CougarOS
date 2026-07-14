# Central Brain Android 13 物理目标测试报告

版本：1.2

日期：2026-07-14

状态：Runtime/Demo/Client2 物理设备应用层验收通过；整机硬件与量产验收未完成

## 1. 范围与需求

本报告记录黑盒 Android 13 座舱域控制器上的首轮直接 ADB 测试。覆盖 Req ID：
`APP-004`、`XSC-001`、`XSC-004`、`XSC-005`、`XSC-006`、`NV-F-001`、
`NV-F-011`、`NV-F-012`、`NV-G-003`、`NV-G-005`、`NV-G-006`、
`NV-G-007`、`NV-P-002`、`KH-003`、`KH-006`、`DEL-001`、`DEL-003`、
`DEL-004`、`DEL-005`。

本轮只验证普通 Android 应用、Binder、Room、Java/C/JNI 生命周期和维护型 HMI。不探测
私有 vendor service，不扫描 device node，不调用 NPU/VHAL/车辆总线，不执行 root、remount、
fastboot、SELinux 修改或 system/vendor 分区写入。

## 2. 调试拓扑

```text
WSL bash/test scripts
    -> /mnt/e/platform-tools/adb.exe
    -> Windows ADB server and USB driver
    -> USB
    -> Android 13 cockpit controller
```

设备使用非秘密别名 `local-cockpit-a13-01`。原始 serial、fingerprint、APK signer digest 和
完整 logcat 只保存在本机临时证据目录，不进入 Git、不上传 GitHub。

## 3. 已确认环境

| 项目 | 结果 |
| --- | --- |
| Android | 13 / API 33 |
| SoC 平台 | UNISOC |
| Android 设备类型 | `automotive` |
| Primary ABI | `arm64-v8a` |
| 64-bit ABI | `arm64-v8a` |
| Build type | `userdebug` |
| SELinux | `Enforcing` |
| Verified Boot | `green` |
| 显示 | 1920 x 1080，160 dpi |
| ADB 身份 | 普通 `shell`，不使用 root |

## 4. 测试结果

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| Windows ADB 从 WSL 调用 | PASS | ADB server、授权和 shell 可用 |
| B3 只读 preflight | PASS | `evidence_scope=api33-device-blackbox-preflight` |
| API/ABI/Automotive 门禁 | PASS | API 33、ARM64、Automotive 均确认 |
| B4 maintenance dry-run | PASS | Runtime/Demo 身份与 signer 门禁通过 |
| Runtime -> Demo 安装顺序 | PASS | 两个 APK 均安装在普通 `/data/app` |
| Signature permission | PASS | Demo 获得 Runtime/Governance 签名权限 |
| Typed Binder/callback/cancel | PASS | Demo UI 显示连接、完成和取消结果 |
| Room/Governance/HMI 回归 | PASS | B3 自动验收通过 |
| Native Runtime C ABI V1 | PASS | ARM64 load、lifecycle、capacity、dumpsys 和 Diagnostic parity 通过 |
| Runtime 进程恢复 | PASS | force-stop/recreate 后 native snapshot 恢复 |
| 恢复后 Demo Binder/Governance UI | PASS | 有界显式重连后 UI 恢复 connected/verified，非陈旧 disconnected |
| Signer mismatch 负向门禁 | PASS | 异签名 APK 在首次安装命令前被拒绝 |
| Crash/ANR buffer | PASS | 测试结束后没有 Central Brain crash 或 ANR |
| Client2 signer migration | PASS | 经用户明确授权，卸载普通 `/data/app` 原包后安装 Runtime 同签 debug Client2 |
| Client2 Binder/UI | PASS | 真实按钮、可信调用身份、异步完成回调和 UI 回复通过 |
| Client2/Runtime recovery | PASS | Runtime 缺失/死亡/重启、single-flight、Client2 重启和 Binder race 回归通过 |

首次 dry-run 对 signer mismatch 的失败关闭是预期安全结果。用户随后明确批准清除原 Client2
及其应用数据；迁移按 `uninstall com.tuanjie.urasclient2`、安装 Runtime 同签 debug APK、重新执行
Binder/UI 验收的顺序完成。仓库工具不会默认删除包，只有显式
`--replace-conflicting-client2` 且安装错误确认为 signer mismatch 时才执行该迁移。

## 5. 本轮修复

### HWADB-001 Windows ADB 设备枚举 CRLF

`adb.exe devices` 使用 CRLF。原脚本把第二列解析为 `device\r`，导致在线设备数为零。
所有 Central Brain 设备枚举路径现先执行 `tr -d '\r'`，再解析状态列。

### HWADB-002 Windows ADB get-state CRLF

`adb.exe get-state` 返回 `device\r\n`。原精确比较误判设备离线。所有 Central Brain
`get-state` 门禁现先移除 `\r`。

### HWADB-003 signer guard 忽略调用方 ADB

黑盒 signer guard 原先无条件选择 WSL Linux ADB，导致验收编排中途切换 ADB server。
现在遵循调用方传入的 `ADB`，没有传入时才回退到 Android SDK ADB。

`tools/check_central_brain_windows_adb_compatibility.sh` 同时验证 LF/CRLF 解析、所有相关脚本的
归一化规则和 signer guard 的 ADB override。

### HMI-001 Runtime 恢复后连接状态陈旧

首轮 Native Runtime process-recovery 结束后，SDK 已报告 Binder death，但 Demo 没有调用公开
`reconnect()`，UI 持续显示 Runtime/Governance `disconnected`。同时 B3 汇总沿用了安装阶段 UI 结果，
没有读取 process-recovery 后的 UI，却输出 `binder_room_hmi_regression_verified=true`。

Demo 现按 Activity 生命周期执行 500 ms 间隔、最多 10 次的有界 Runtime/Governance 显式重连；
连接成功或 Activity 销毁时取消 pending retry，重连后重新校验协议 version/hash 并刷新状态。B3
验收现必须读取恢复后的真实 UI tree，确认 connected/verified 且不存在 disconnected，才可输出
`post_recovery_hmi_rebind_verified=true` 和 HMI regression PASS。

### SIGN-001 Client2 同包 signer 迁移

目标机原 Client2 与 Central Brain debug signer cohort 不一致，Android 正确拒绝同包覆盖。缺少厂商
私钥时不能生成可覆盖原包的伪同签 APK。本轮按用户明确授权移除普通 `/data/app` 原包及应用数据，
随后安装与 Runtime 同签的 debug Client2。`install_debug_apk.sh` 和 Binder 验收脚本新增显式
`--replace-conflicting-client2`；默认仍输出 `SIGNER_MIGRATION_REQUIRED` 且不修改设备。

迁移后 API 33 ARM64 目标上已确认 signature permission、Runtime current-signer capability、
`care.cold` 真实按钮、typed Binder completion、确定性 UI 回复和原车模/半透明面板同时渲染。
完整恢复矩阵同时输出 `api33_end_to_end_acceptance_complete=true` 和
`r7_application_integration_complete=true`。
该结果只解决当前测试设备的 debug 同包安装，不解决生产私钥、OTA/MDM 升级或量产 signer 审批。

## 6. 当前结论

```text
physical_controller_application_evidence_available=true
runtime_demo_physical_acceptance_passed=true
post_recovery_hmi_rebind_verified=true
client2_physical_acceptance_passed=true
production_ready=false
target_hardware_validated=false
native_vendor_npu_provider_available=false
native_runtime_dispatch_enabled=false
hardware_accessed=false
driver_development_triggered=false
virtualization_development_triggered=false
```

这里的 `physical_controller_application_evidence_available=true` 只表示物理控制器上的 Android
应用层证据已经存在，不表示真实 NPU、VHAL、车辆总线、Safety Runtime、性能、热、休眠唤醒、
生产签名/升级策略或整车功能安全已经通过。

## 7. 后续测试顺序

1. 由目标 owner 给出生产 Client2/Runtime signer、OTA/MDM 升级和回滚策略。
2. 执行后台/休眠唤醒、长稳、存储升级和 MDM 策略测试。
3. 获取公开 Vendor NPU/VHAL SDK 或 service contract 后，再评审 `DRV-GAP-001` 和 adapter 工作量。
4. 每一类硬件能力单独提供目标 smoke、故障、性能和回滚证据；不得由本次应用层 PASS 推断。

# Central Brain Android 13 物理目标测试报告

版本：1.0

日期：2026-07-14

状态：Runtime/Demo 物理设备应用层验收通过；Client2 签名迁移阻塞；整机硬件与量产验收未完成

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
| Signer mismatch 负向门禁 | PASS | 异签名 APK 在首次安装命令前被拒绝 |
| Crash/ANR buffer | PASS | 测试结束后没有 Central Brain crash 或 ANR |
| Client2 include dry-run | BLOCKED | 目标机现有 Client2 signer 与 debug Client2 signer 不同 |

Client2 阻塞是预期的安全结果。工具输出 `SIGNER_MIGRATION_REQUIRED` 后停止，未安装、未卸载、
未覆盖目标机现有 Client2。不得通过自动卸载绕过该门禁。

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

## 6. 当前结论

```text
physical_controller_application_evidence_available=true
runtime_demo_physical_acceptance_passed=true
client2_physical_acceptance_passed=false
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
量产签名或整车功能安全已经通过。

## 7. 后续测试顺序

1. 由目标 owner 给出 Client2 原始/测试/量产 signer 与 RenderService allowlist 迁移方案。
2. 在不卸载厂商 Client2 的前提下决定同签升级、厂商测试签名包或独立可维护 Demo 包路径。
3. 执行后台/休眠唤醒、长稳、存储升级和 MDM 策略测试。
4. 获取公开 Vendor NPU/VHAL SDK 或 service contract 后，再评审 `DRV-GAP-001` 和 adapter 工作量。
5. 每一类硬件能力单独提供目标 smoke、故障、性能和回滚证据；不得由本次应用层 PASS 推断。

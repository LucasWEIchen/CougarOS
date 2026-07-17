# Central Brain Android 13 物理目标测试报告

版本：1.3

日期：2026-07-15

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
| Client2 Binder/UI | PASS | 真实按钮、可信 capability、Session snapshot/event/replay 和 UI projection 通过 |
| Client2 导航菜单 | PASS | 启动隐藏、导航首次显示/二次隐藏、面板外关闭、再次打开均通过 |
| Client2/Runtime recovery | PASS | Runtime 缺失/死亡/重启、Session reconnect/replay/去重、stream replacement、Client2 重启和 Binder race 通过 |

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

### UI-001 Client2 浮窗改为导航菜单

Client2 底部导航由 Tuanjie/RenderService 绘制，UI 树中没有可直接绑定的原生按钮。隔离 patch
在根 `FrameLayout` 增加透明、带可访问性描述的触摸目标，按当前 1920x1080 布局覆盖导航图标；
右侧面板本身的半透明浅灰样式、宽度、圆角、按钮和结果区均未改变。

真机 UIAutomator/ADB 验收确认 Activity 启动后面板不可见，首次导航点击显示，第二次点击隐藏，
重新显示后点击面板外区域隐藏，再次打开后 `care.cold` typed Binder/UI reply 正常。R7C 回归还
确认 Client2 进程重启后菜单可以重新打开。该触摸映射不修改 RenderService 或 Unity/Tuanjie
资产；不同分辨率、density 或厂商导航布局仍须单独验证，见 `DEV-017`/`ISSUE-019`。

## 6. 当前结论

```text
physical_controller_application_evidence_available=true
runtime_demo_physical_acceptance_passed=true
post_recovery_hmi_rebind_verified=true
client2_physical_acceptance_passed=true
client2_navigation_menu_acceptance_passed=true
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

## 8. 2026-07-17 P4-W01 Session/Event bridge evidence

在同一 Android 13/API 33 ARM64 USB 设备上，重建并安装同签名 Runtime/Client2 后完成：

1. `care.cold` UI alias 映射为 `scene.comfort.cold.v1`；冻结 Session V1 admission 通过；
2. Client2 收到 CREATED snapshot、唯一 sequence 1 `ScenarioRequested` 和 replay complete；
3. legacy Smali 文本区显示 snapshot summary，旧 descriptor 保持可调用；
4. 新兼容请求关闭并替换旧 stream，每个 Session 各有一条 sequence 1 event；
5. Runtime debug process-death 后原 Session 自动重连，Room snapshot/cursor replay 完成，已送达 event 未重复；
6. reconnect replay 重新投影兼容摘要，不产生伪 FAILED/COMPLETED；
7. Client2 process restart 后 Binder 重绑、底部导航菜单重开和新 Session 正常；
8. Runtime disabled 显示受控 transport failure，reenable 后可重试。

证据标志：

```text
client2_session_event_primary_api=true
client2_session_snapshot_verified=true
client2_session_event_sequence_verified=true
client2_session_replay_complete=true
client2_legacy_stream_replacement_verified=true
client2_session_reconnect_replay_verified=true
client2_session_duplicate_event_suppressed=true
runtime_service_death_terminal_emitted=false
runtime_service_death_recovered_without_terminal=true
client2_process_restart_rebind_completed=true
client2_navigation_menu_reopen_verified=true
session_event_transport_used=true
http_transport_used=false
service_dispatch_triggered=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该证据证明 Android 应用层 Session/Event bridge 与恢复行为，不证明 scenario/Graph/Effect 执行、HVAC/Seat 控制、
真实车辆信号、NPU、Driver/HAL 或量产资格。

## 9. 2026-07-17 P4-W02 immutable HMI/recreate evidence

同一 Android 13/API 33 ARM64 USB 设备通过：

1. maintained Java coordinator 直接消费 typed snapshot/event/replay，legacy text callback 非权威；
2. 连续场景请求由 coordinator close/replace，每个新 Session 只有一条 sequence-1 event；
3. Runtime process death 后原 connection reconnect/replay，HMI state 不产生 terminal；
4. panel hide 后 force-stop Client2，重启从 private text-free checkpoint resume existing Session；
5. process restart 后 panel 仍隐藏，点底部导航后显示 replayed snapshot summary；
6. checkpoint 不保存 user/model/display text，未访问车辆/NPU/Driver-HAL。

```text
cockpit_hmi_state_reducer_implemented=true
cockpit_hmi_lifecycle_owner_java=true
client2_hmi_session_replacement_verified=true
client2_hmi_checkpoint_resume_verified=true
client2_hmi_hidden_state_recreation_verified=true
client2_hmi_checkpoint_text_persisted=false
legacy_text_callback_authoritative=false
scenario_execution_enabled=false
service_dispatch_triggered=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该证据是当前 Client2 debug APK 的应用层恢复验收。SharedPreferences 的 production encryption/backup/user owner 仍由
`DEV-052/ISSUE-035` 跟踪，四阶段 shell/HVAC/Seat/Runtime 执行闭环未完成。

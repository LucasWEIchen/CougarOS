# Central Brain Android 13 物理目标测试报告

版本：1.5

日期：2026-07-24

状态：Runtime/Demo/Client2/RenderService 测试板与生产板应用层验收通过

## P4-R6 Unity-native HVAC/Seat 增量

2026-07-24 在非秘密别名 `testboard` 的 Android 13 ARM64 物理设备上成对安装维护型
Client2/RenderService APK。真实 OpenClaw Cold 场景完成后，Unity 原生驾驶席和乘员席温区
均由 26.5°C 切换到 28.0°C，Android 温度 overlay 不存在；Fatigue 座椅靠背由 15 度向
30 度展开。全程未访问 Vehicle/VHAL/CAN/Driver-HAL。

生产板恢复 ADB 后，基于 source commit `5da6deb8` 成对安装同一最终 APK。设备端文件与构建
产物 SHA-256 一致：RenderService 为
`5c19754ff246d55ebd323c44f91e6ab40908bfc6c75812193d8ab30bc20e4079`，Client2 为
`2776d598c3614ef868e3392f8a11ad60fad6a3f32c4aa22a6f7fdbceeb9e125f`。初始双区 26.5°C；
Cold 经目标以太 OpenClaw protocol v3 在 11132 ms 完成并更新原生双区 28.0°C；Fatigue
在 7207 ms 完成，靠背从 15 度向 30 度展开。随后强制停止 Client2/RenderService 并冷启动，
三个 Central Brain 进程均恢复，Unity 双区回到原生 26.5°C；测试结束无 crash/ANR。

本报告确认 `testboard_android13_arm64_verified=true`、
`production_board_final_package_retest=true`，同时保持 `production_ready=false`、
`target_hardware_validated=false`。详设与外部边界见
[CENTRAL_BRAIN_CLIENT2_UNITY_NATIVE_HVAC_SEAT_PATCH.md](CENTRAL_BRAIN_CLIENT2_UNITY_NATIVE_HVAC_SEAT_PATCH.md)
与 `ISSUE-060`。

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

## 10. 2026-07-17 P4-W03 intent-first four-stage shell evidence

同一 Android 13/API 33 ARM64 USB 设备通过：

1. signed Client2/Runtime signer parity 和 secondary SDK dex 检查；
2. panel exact bounds `(1264,160)-(1888,1048)`、原 render region full-screen、导航显隐和外部点击隐藏；
3. Intent/Plan/Execution/Result 四阶段可选择，四项自然场景为唯一 primary input；
4. cold intent 提交后进入 Plan，typed Session snapshot/event/replay 正常；
5. HVAC device drawer 打开/关闭，control surface 仍为 placeholder；
6. Runtime unavailable/retry、Runtime death replay、Client2 restart/resume、hidden state restore 全部通过；
7. UI 明确显示 Graph/Effect/readback unavailable，未访问车辆/NPU/Driver-HAL。

```text
cockpit_hmi_four_stage_shell_verified=true
cockpit_hmi_safe_frame_1920x1080_verified=true
cockpit_hmi_device_drawer_verified=true
cockpit_hvac_surface_implemented=false
cockpit_seat_surface_implemented=false
scenario_execution_enabled=false
service_dispatch_triggered=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

本节只记录脱敏应用层证据。设备序列号、raw UI XML/logcat、用户/模型文本和车辆 payload 位于本地未跟踪日志，
不进入 GitHub。固定分辨率差异由 `DEV-053` 跟踪；下一硬件增量为 P4-W04 HVAC control surface。

## 11. 2026-07-17 P4-W04 HVAC control surface evidence

同一 Android 13/API 33 ARM64 USB 设备通过：

1. signed Runtime/Client2 安装、signature permission、secondary SDK dex 和 1920x1080 safe frame；
2. HVAC drawer power、zone、temperature/fan stepper、AUTO、A/C、SYNC、airflow、WARM/COOL/CLEAR controls 可见；
3. 三次快速升温输入由 300 ms debounce 合并为一个 `manual.hvac` Session，desired 从 22.5 C 到 24.0 C；
4. bridge canonical 映射为 `scene.manual.hvac.adjust.v1`，只记录参数存在性，不记录 HVAC target payload；
5. Session admission 投影为 REQUESTED，reported/source/quality 保持 UNAVAILABLE/NO_EVIDENCE，VERIFIED 为 false；
6. 累计 Runtime unavailable/retry、Runtime death replay、Client2 restart/resume、hidden state restore 继续通过；
7. service/Effect/Adapter/hardware dispatch 均未触发，Seat 仍为 P4-W05 placeholder。
8. 重复测试发现并修复 Session 替换竞态：新 bind 先建立，再 cancel/close 旧 Session；最终完整 recovery matrix 通过，
   active durable Session 不再因显式场景切换累积。验收每轮清理 Runtime test data，历史测试记录不影响容量结论。

```text
cockpit_hvac_surface_implemented=true
cockpit_hvac_controls_verified=true
cockpit_hvac_debounce_verified=true
cockpit_hvac_manual_session_admission_verified=true
cockpit_hvac_desired_reported_separation_verified=true
cockpit_hvac_reported_readback_available=false
cockpit_hvac_verified_before_readback=false
hvac_manual_typed_parameter_field=false
client2_hmi_replacement_bind_first_verified=true
client2_hmi_replaced_session_cancel_verified=true
cockpit_seat_surface_implemented=false
scenario_execution_enabled=false
production_effect_dispatch_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

UI 截图人工复核确认 HVAC drawer 完全位于 `(1264,160)-(1888,1048)`，原车模背景可见，controls 在 drawer 内滚动，
无越界或不连贯遮挡；临时截图已删除。设备身份、raw UI tree/logcat 和目标参数只保留在本地未跟踪 evidence。
V1 typed parameter 缺口由 `DEV-054` 跟踪；下一硬件增量为 P4-W05 Seat control surface。

## 12. 2026-07-17 P4-W05 Seat control surface evidence

同一 Android 13/API 33 ARM64 USB 设备通过：

1. signed Runtime/Client2 安装、signature permission、secondary SDK dex 和 1920x1080 safe frame；
2. Seat drawer 四座区、heat/vent 0-3、massage、recline 0-60 degree、UPRIGHT/COMFORT/REST controls 可见；
3. heat 后立即 vent 由 300 ms debounce 合并为一个 `manual.seat` Session，最终 desired 为 heat 0/vent 1，互斥成立；
4. bridge canonical 映射为 `scene.manual.seat.adjust.v1`，只记录参数存在性，不记录 Seat target payload；
5. `UNKNOWN_RESTRICTED` 下主驾 recline 请求保持 desired 0 degree、decision 为 `DENIED_UNKNOWN_CONTEXT`，没有新 Session；
6. Session admission 只投影 REQUESTED，reported/source/quality 保持 UNAVAILABLE/NO_EVIDENCE，VERIFIED 为 false；
7. Runtime unavailable/retry、Runtime death replay、Client2 restart/resume、duplicate suppression、hidden state restore 和
   Session replacement recovery matrix 继续通过；
8. service/Effect/Adapter/hardware dispatch 均未触发。

```text
cockpit_seat_surface_implemented=true
cockpit_seat_controls_verified=true
cockpit_seat_heat_vent_mutex_verified=true
cockpit_seat_unknown_restricted_fail_closed=true
cockpit_seat_manual_session_admission_verified=true
cockpit_seat_desired_reported_separation_verified=true
cockpit_seat_reported_readback_available=false
cockpit_seat_verified_before_readback=false
seat_manual_typed_parameter_field=false
scenario_execution_enabled=false
production_effect_dispatch_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

UI 截图人工复核确认 Seat drawer 完全位于 `(1264,160)-(1888,1048)`，半透明材质保留原车模可见，所有 controls、
Safety Context 和 reported evidence 文本均在画布内。临时截图位于本地 `/tmp` 且不进入 Git；设备身份、raw UI tree、
logcat 和参数 payload 仍只保留在本地未跟踪 evidence。V1 typed parameter/approval 缺口由 `DEV-055` 跟踪；下一硬件
增量为 P4-W06 Plan/effect execution timeline。

## 13. 2026-07-18 P4-W06 observable execution timeline evidence

同一 Android 13/API 33 ARM64 USB 设备通过：

1. signed Runtime/Client2 安装、signature permission、secondary SDK dex、Session/Event transport 和 recovery 回归；
2. Execution surface 在 1920x1080 safe frame 内显示 Intent/Context/Plan/Policy/Graph/Effect/Readback 七阶段；
3. accepted Session 显示 Intent/Policy SESSION ACCEPTED，回放的 `ScenarioRequested` 不降级 admission；
4. 当前 snapshot `activePlanRevision=0`，因此 Plan NOT PUBLISHED；无 action/effect event 时 Graph NOT WIRED、Effect
   NOT DISPATCHED、Readback UNAVAILABLE；
5. Media STOP、Navigation CANCEL 独立显示 UNAVAILABLE，最新 `ScenarioRequested` 以脱敏 sequence/type/status/target/
   source/result 显示；
6. HVAC/Seat debounce、互斥、未知 Context position block、desired/reported separation 继续通过；
7. service/Effect/Adapter/hardware dispatch、Driver/HAL 和 virtualization 均未触发。

```text
cockpit_execution_timeline_verified=true
cockpit_execution_plan_not_published_verified=true
cockpit_execution_graph_not_wired_verified=true
cockpit_execution_effect_not_dispatched_verified=true
cockpit_execution_readback_unavailable_verified=true
cockpit_execution_media_navigation_projection_verified=true
cockpit_execution_typed_event_trace_verified=true
scenario_execution_enabled=false
production_effect_dispatch_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

人工截图复核确认七阶段、Media/Nav 和 trace 均位于 `(1264,160)-(1888,1048)` 半透明浮窗内，原车模背景可见，无越界
或不连贯遮挡。截图仅保存在本地 `/tmp`，不进入 Git；设备身份、raw UI tree/logcat 保留在本地未跟踪 evidence。
Runtime execution event publication 偏差由 `DEV-056` 跟踪；下一硬件增量为 P4-W08 Driving restriction renderer。

## 14. 2026-07-18 P4-W07 approval/recovery UX evidence

同一 Android 13/API 33 ARM64 USB 设备通过：

1. signed Runtime/Client2 安装、signature permission、secondary SDK dex、Session/Event transport 和完整 recovery 回归；
2. Execution surface 在 1920x1080 safe frame 内显示 Approval status/reason/target/expiry、Outcome evidence 和 Compensation；
3. 当前 Runtime 未发布 `ApprovalPrompt`、retry metadata 或 `UndoHandle`，所以 Approval/Reason/Target/Expiry、Outcome、
   Compensation 分别准确显示 UNAVAILABLE/NO EVIDENCE/UNAVAILABLE；
4. approve/reject/retry/undo 四个命令 visible 且 `enabled=false`，没有 Binder command、Graph/Effect/Adapter/hardware dispatch；
5. outside tap 隐藏 overlay，再次从底部导航打开后 Session/recovery projection 保持；隐藏动作不取消 Session；
6. host future-event coverage 验证 validated capability target 继承、VERIFIED+FAILED -> PARTIALLY_COMPLETED、fresh compensation
   -> COMPENSATED；这些 host 事件不是实体 Runtime execution evidence；
7. HVAC/Seat、七阶段 timeline、Runtime unavailable/death、Client2 restart/checkpoint、duplicate suppression 和 Session
   replacement recovery matrix 继续通过。

```text
cockpit_recovery_state_reducer_owned=true
cockpit_approval_details_fail_closed_verified=true
cockpit_partial_outcome_projection_verified=true
cockpit_compensation_projection_verified=true
cockpit_recovery_commands_disabled_verified=true
cockpit_recovery_outside_dismiss_preserved=true
cockpit_approval_response_service_published=false
cockpit_retry_service_published=false
cockpit_undo_service_published=false
scenario_execution_enabled=false
production_effect_dispatch_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

UI 树和人工截图复核确认恢复区及四个 disabled controls 位于 `(1264,160)-(1888,1048)` 内部 ScrollView，无越界或背景
分屏。截图只保留本地临时 evidence，不进入 Git；设备身份、raw UI tree/logcat 不发布。契约承载偏差由 `DEV-057`
跟踪；下一硬件增量为 P4-W08 Driving restriction renderer。

## 15. 2026-07-18 P4-W08 driving restriction renderer evidence

同一 Android 13/API 33 ARM64 USB 设备通过 signed APK happy path 和完整 recovery matrix：

1. Client2 默认没有 trusted global driving Context，Header 显示 UNKNOWN/受限，restriction banner 明确按行驶态限制；
2. Intent/Context/Plan/Execution/Result 长详情和 typed trace 在受限模式隐藏，场景 reply 限制为单行摘要；
3. HVAC/Seat 抽屉仍可打开并显示 desired/reported 缺口，但全部参数按钮 disabled；本轮不创建 manual HVAC/Seat Session；
4. `skill.nap` 高风险按钮 disabled；`care.cold` 等非高风险自然场景仍可通过 typed Session/Event 建立受治理 Session；
5. Runtime 不可用后重试、Runtime process death、Session replay/duplicate suppression、Client2 process restart/checkpoint、
   outside dismiss/reopen 全部通过，恢复过程没有放宽限制；
6. 未注入伪 PARKED、未调用 Adapter/Effect/Vehicle/VHAL/NPU/Driver-HAL；Runtime policy authority 保持独立；
7. 1920x1080 截图复核确认浮窗位于 `(1264,160)-(1888,1048)`，半透明车模背景可见，无越界或控件重叠。

```text
cockpit_driving_ux_policy_verified=true
cockpit_unknown_driving_restricted_verified=true
cockpit_restricted_long_text_hidden_verified=true
cockpit_restricted_parameter_editing_disabled_verified=true
cockpit_high_risk_controls_disabled_verified=true
cockpit_runtime_policy_authority_independent=true
cockpit_hvac_controls_restricted_verified=true
cockpit_hvac_manual_session_admission_retested=false
cockpit_seat_controls_restricted_verified=true
cockpit_seat_manual_session_admission_retested=false
scenario_execution_enabled=false
production_effect_dispatch_enabled=false
hardware_accessed=false
driver_development_triggered=false
virtualization_development_triggered=false
production_ready=false
target_hardware_validated=false
```

截图、raw UI tree、原始日志和设备身份只留在本地未跟踪 evidence，不进入 Git。该 P4-W08 记录只覆盖默认受限路径；
P4-W09 已通过 signature/capability protected engineer drawer 补充 PARKED/MOVING/UNKNOWN 呈现矩阵，证据见下一节。
production Context/Safety authority 仍由 P8 关闭，偏差由 `DEV-058/059` 跟踪。

## 16. 2026-07-18 P4-W09 engineer simulation drawer evidence

同一 Android 13/API 33 ARM64 USB 设备完成 debug Runtime 与 maintained Client2 signed APK 验收：

1. 工程入口在 Binder 连接前为 `gone`；同签名 permission、`debug.simulation.control` capability 和 AIDL V1/hash
   校验后显示并报告 CONNECTED；
2. PARKED、MOVING、UNKNOWN 逐项命令均收到严格递增 Controller revision。PARKED 恢复完整呈现，MOVING/UNKNOWN
   恢复受限呈现；reset 回到 unavailable/restricted；
3. driver occupancy 与 belt 的 boolean 切换只使用 canonical path/area，并在 UI 显示 Controller 确认值；
4. HVAC/Seat 固定 adapter 下的 NONE、DELAY、TIMEOUT、RETRYABLE_FAILURE、TERMINAL_FAILURE、READBACK_MISMATCH
   fault matrix 均可达，失败不会被渲染为车辆已执行；
5. 1920x1080 截图复核确认工程抽屉位于 `(1264,160)-(1888,1048)` 半透明浮窗内，无越界、分屏或控件重叠；
6. Runtime release APK 中 debug Controller Service 不存在，production policy 无 debug capability；
7. 测试期间未访问 Vehicle/VHAL、Android Car、NPU、Driver/HAL，未触发 Scenario Graph、Effect 或 Adapter dispatch。

```text
cockpit_engineer_simulation_drawer_verified=true
cockpit_engineer_signature_permission_granted=true
cockpit_engineer_capability_allowed=true
cockpit_engineer_driving_state_matrix_verified=true
cockpit_engineer_occupancy_belt_verified=true
cockpit_engineer_fault_matrix_verified=true
cockpit_engineer_context_revision_monotonic_verified=true
cockpit_engineer_reset_fail_closed_verified=true
cockpit_engineer_runtime_release_service_absent=true
cockpit_engineer_effect_authorization_source=false
cockpit_engineer_production_available=false
vehicle_signal_provider_wired=false
production_effect_dispatch_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

设备身份、raw UI tree、logcat 和截图只保留在本地未跟踪 evidence，不进入 Git。该证据关闭 `DEV-058` 的“受保护 debug
PARKED/MOVING/UNKNOWN 可测试性”子条件，但不关闭 production trusted Context 缺口；本地 projection 边界由 `DEV-059`
跟踪。下一实体工作包为 P4-W10 Scenario/manual-control synchronization。

## 17. 2026-07-18 P4-W10 scenario/manual control synchronization evidence

同一 Android 13/API 33 ARM64 USB 设备完成 Runtime/Client2 signed APK 与 UIAutomator 验收：

1. 受保护 debug Controller 仅将测试 Context 切换为 PARKED、OCCUPIED、UNBELTED；每次切换均等待 Runtime 确认，
   该 Context 不提供 Effect authority；
2. `care.cold`、`care.fatigue`、`skill.nap` 分别规范化为 canonical scenario，设备详情显示 HVAC
   `CATALOG REQUIRED`、Seat `CATALOG OPTIONAL`、Seat `CATALOG REQUIRED`；
3. 三个自然场景的 HVAC/Seat 详情使用同一 Session lifecycle、Plan revision 和 Event sequence；Runtime 当前没有发布 Plan，
   因此详情明确显示 `NOT PUBLISHED`，没有从 catalog role 推断 Plan；
4. HVAC 温度增加与 Seat heat 增加均通过既有 `ScenarioClient` 创建 manual governed Session，设备角色显示
   `MANUAL TARGET`，生命周期显示 `SESSION_ACCEPTED`；
5. alias/canonical mismatch host 回归失败关闭为 `CB_HMI_SCENARIO_MISMATCH`，实体正向路径没有绕过 Session/Event；
6. 未调用 Scenario Graph、Effect、Adapter、Vehicle/VHAL、NPU 或 Driver/HAL，reported/readback 继续不可用；
7. 原始设备身份、UI tree 和 logcat 只保留在本地未跟踪 evidence，不进入 GitHub。

```text
cockpit_scenario_natural_cold_sync_verified=true
cockpit_scenario_natural_fatigue_sync_verified=true
cockpit_scenario_natural_rest_sync_verified=true
cockpit_scenario_manual_hvac_sync_verified=true
cockpit_scenario_manual_seat_sync_verified=true
cockpit_scenario_device_session_synchronized=true
cockpit_scenario_plan_publication_inferred=false
cockpit_scenario_effect_dispatch_enabled=false
cockpit_scenario_readback_available=false
cockpit_scenario_debug_context_effect_authority=false
scenario_execution_enabled=false
production_effect_dispatch_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该证据验证 `S2-SCN-001` 的 Client2 HMI 状态同步边界，不代表 Runtime Scene Resolver/Plan Compiler/Graph/Effect
已接入。catalog role 与 Runtime authority 的偏差由 `DEV-060` 跟踪；下一实体工作包为 P4-W11
Accessibility/display matrix。

## 18. 2026-07-18 P4-W11 accessibility/display matrix evidence

同一 Android 13/API 33 ARM64 USB 设备完成 signed Runtime/Client2 APK 与 UIAutomator 显示矩阵验收：

1. `1280x720@107dpi`、`1920x1080@160dpi`、`2560x1440@213dpi` 三个严格 allowlisted 横屏 profile
   均显示面板，按同一 `624dp x 888dp` 安全框缩放且没有超出物理显示边界；
2. `1920x1080@160dpi` 保持既有 `(1264,160)-(1888,1048)` 安全框；紧凑与大屏 profile 使用确定性
   density/bounds，不按任意比例猜测布局；
3. 1920x1080 下 `font_scale=1.3` 时四阶段、场景按钮和最长中文标签保持在面板内，无可点击控件相互重叠；
4. 所有当前可见 Button 的实体 bounds 均不少于 48dp，运行时 accessibility tree 中 content description 非空；
5. 当前 Intent tab 暴露 `selected=true`，Plan tab 暴露 `selected=false`；选中/禁用状态不只依赖颜色；
6. 未列出的 `1366x768@114dpi` 被识别为 `DISPLAY_MATRIX_MISMATCH`，底部 AIOS trigger disabled，不能打开面板；
7. 测试通过 EXIT trap 恢复原始 size/density/font-scale/rotation；设备身份、UI tree 和日志仅保留本地未跟踪 evidence；
8. 显示策略不提供 Runtime Policy、Safety 或 Effect authority，未访问 Vehicle/VHAL、NPU、Driver/HAL。

```text
cockpit_display_matrix_android13_arm64_verified=true
cockpit_display_compact_1280_720_verified=true
cockpit_display_standard_1920_1080_verified=true
cockpit_display_large_2560_1440_verified=true
cockpit_display_large_text_1_3_verified=true
cockpit_touch_target_min_dp=48
cockpit_accessibility_content_description_verified=true
cockpit_accessibility_state_not_color_only=true
cockpit_long_chinese_non_overlap_verified=true
cockpit_display_unsupported_fail_closed=true
cockpit_display_effect_authorization_source=false
scenario_execution_enabled=false
production_effect_dispatch_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该证据关闭 P4-W11 的 application-layer display/accessibility DoD，但不构成 OEM 多屏/竖屏、任意 density/font scale、
TalkBack 人工认证、驾驶分心或量产 HMI 资格。限制由 `DEV-061` 跟踪；下一实体工作包为 P4-W12 Android device
acceptance/fault/recovery aggregate。

## 19. 2026-07-18 P4-W12 aggregate Android application acceptance evidence

同一 Android 13/API 33 ARM64 USB 设备从单一 aggregate runner 完成 P4 应用层全量重放：

1. recovery fresh 执行 navigation show/hide、outside dismiss、Session replacement、Runtime/Client2 process death、
   replay/dedup、checkpoint resume 与 hidden-state recreation；每项从本轮子报告读取精确 marker；
2. engineer debug-only suite 重放 UNKNOWN/MOVING/PARKED、occupancy/belt、HVAC/Seat fault 和 reset fail-closed；
3. scenario suite 重放 cold/fatigue/rest 和 manual HVAC/Seat admission。P4-W11 的 48dp 控件使 request evidence 位于
   ScrollView 下方，测试已改为按实际 ScrollView bounds 有界滚动后验证，不再假定节点首屏可见；
4. display/accessibility suite 重放三个严格 profile、1.30 fontScale、48dp、content/state semantics 和 unsupported profile；
   size/density/font/rotation 在退出时恢复；
5. 所有 UIAutomator dump/cat 使用 8 秒单次超时和有限重试，避免设备工具偶发阻塞形成无限等待；
6. 每个子套件前后检查 Client2/Runtime crash buffer，最终重新启动 Client2、获取非空 UI tree 并确认导航 trigger；
7. release source/APK 门禁确认 Runtime 无 debug simulation Service/adapter。项目仍没有可宣称 production 的独立
   Client2 release artifact；
8. 子报告仅使用固定非秘密 `device_alias`，不记录 raw serial；UI tree、crash buffer 和原始日志留在本地忽略目录。

```text
p4_w12_application_acceptance_complete=true
p4_android13_arm64_aggregate_verified=true
p4_navigation_show_hide_verified=true
p4_natural_scenario_sync_verified=true
p4_manual_hvac_seat_admission_verified=true
p4_moving_unknown_fail_closed_verified=true
p4_runtime_client_process_recovery_verified=true
p4_ui_tree_verified=true
p4_crash_buffer_clean=true
runtime_release_simulation_surface_absent=true
p4_plan_effect_projection_host_verified=true
p4_automatic_plan_runtime_published=false
p4_production_effect_dispatch_enabled=false
p4_approval_response_service_published=false
p4_undo_service_published=false
p4_vehicle_readback_available=false
client2_production_release_artifact_available=false
hmi_d4_demo_control_loop_complete=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该证据关闭 P4-W12 application aggregate acceptance，不关闭 HMI-D4。Plan/Effect/Media/Nav/approval/partial/mismatch/undo
只有 host typed projection 或实体 fail-closed 呈现；当前 Runtime 未发布自动 Plan/Effect、approval response、undo 或车辆
readback。限制由 `DEV-062` 与 `ISSUE-022/026/030/033` 跟踪；下一软件工作包为 P5-W01 Tool manifest/schema。

## 20. 2026-07-18 P5-W01 Tool manifest probe pending evidence

P5-W01 完成 JVM contract test 与 debug/release Java compile 后尝试在同一 Windows 11 USB 路径执行新
`ToolManifestProbeActivity`。本轮 preflight 不输出设备 serial/model，只统计 transport 状态：

```text
windows_com7_present=true
windows_android_adb_interface_present=false
adb_device_count=0
adb_unauthorized_count=0
adb_offline_count=0
```

COM7 只证明串口设备存在，不能作为 Android Debug Bridge transport。重启 Windows ADB server 后仍无 Android ADB
interface，因此未安装新 APK、未启动 probe，也未读取任何设备 payload。当前必须保持：

```text
tool_manifest_contract_defined=true
tool_manifest_host_jvm_verified=true
tool_manifest_debug_release_compile_verified=true
tool_manifest_android13_arm64_verified=false
tool_registry_published=false
tool_execution_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

复测条件是目标设备重新暴露 Android ADB interface 并完成 USB debugging authorization。复测时运行统一 installer，
要求 `tool_manifest_probe_complete=true`、schema/digest/negative markers 全部为 true，同时 Registry/Executor/Effect/vehicle/
NPU/hardware markers 保持 false。该阻塞由 `ISSUE-036` 跟踪，不回退 P5-W01 已完成的软件合同。

## 21. 2026-07-18 P5-W02 Tool Registry/Resolver probe pending evidence

P5-W02 完成 JVM test、debug/release compile 和 APK build 后再次检查 Windows 11 USB 路径。设备管理器本轮已出现 Android
ADB Interface，但 Windows platform-tools 在 ADB server restart 前后都没有 transport：

```text
windows_com7_present=true
windows_android_adb_interface_present=true
adb_server_restarted=true
adb_device_count=0
adb_unauthorized_count=0
adb_offline_count=0
```

因此未安装本轮 APK、未启动 `ToolRegistryProbeActivity`，也未读取 serial/model/fingerprint/log/UI/payload。PnP interface
存在不等于 ADB session online；可能仍需设备端 USB debugging mode/authorization、USB function 切换或重新插拔。当前证据：

```text
tool_registry_contract_defined=true
tool_resolver_contract_defined=true
tool_health_dynamic_snapshot_defined=true
tool_registry_host_jvm_verified=true
tool_registry_debug_release_build_verified=true
tool_registry_android13_arm64_verified=false
tool_registry_published=false
tool_resolver_published=false
tool_registry_runtime_wired=false
tool_execution_enabled=false
production_tool_registered=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

复测时必须通过统一 installer 的 `tool_registry_probe_complete=true`、conflict/highest-version/state-separation/no-fallback/
health markers，并继续要求 publication/execution/hardware 为 false。该 transport 阻塞不回退 P5-W02 pure-Java 软件合同，
production publisher/composition 风险由 `DEV-064`、`ISSUE-037` 跟踪。

## 22. 2026-07-18 P5 Tool/Skill/Memory aggregate Android acceptance evidence

ADB transport 恢复后，统一 installer 在 Android API 33 / ARM64 上顺序执行 P5-W01..W10 debug probes，并完成 Runtime/Demo 全安装回归。
提交证据仅保留布尔/计数 marker，不包含 serial、model、fingerprint、签名材料、raw log、用户/模型文本、memory/token 或车辆 payload。

```text
device_transport_selected=true
device_identity_redacted=true
android_api=33
device_abi=arm64-v8a
p5_probe_module_count=10
p5_android13_arm64_probe_acceptance_complete=true
tool_manifest_android13_arm64_verified=true
tool_registry_android13_arm64_verified=true
tool_rule_solver_android13_arm64_verified=true
tool_executor_android13_arm64_verified=true
skill_package_verifier_android13_arm64_verified=true
working_memory_android13_arm64_verified=true
profile_memory_android13_arm64_verified=true
episodic_memory_android13_arm64_verified=true
context_budget_android13_arm64_verified=true
memory_consent_android13_arm64_verified=true
android_runtime_full_install_regression_passed=true
production_tool_authority_published=false
production_memory_authority_published=false
production_runtime_wired=false
driver_hal_accessed=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该证据仅确认 build-owned debug fixture 在目标 Android ABI/API 上的 contract 行为，不构成 production Tool/Memory authority、Vehicle/NPU/
Driver-HAL 或目标硬件 qualification。tracking：`DEV-106`、`ISSUE-036..045`。

## 23. 2026-07-18 P6 Event/Proactive/Context aggregate Android acceptance evidence

统一 installer 在 Android API 33 / ARM64 上顺序执行 P6-W01..W06 debug probes，并完成 Runtime/Demo 全安装回归。证据仅保留
boolean/count marker，不包含 serial、model、fingerprint、raw log、Context scalar、用户/模型文本、授权材料或车辆 payload。

```text
device_transport_selected=true
device_identity_redacted=true
android_api=33
device_abi=arm64-v8a
p6_probe_module_count=6
p6_android13_arm64_probe_acceptance_complete=true
event_broker_android13_arm64_verified=true
event_qos_android13_arm64_verified=true
trigger_engine_android13_arm64_verified=true
proactive_consent_android13_arm64_verified=true
context_source_android13_arm64_verified=true
active_suggestion_android13_arm64_verified=true
android_runtime_full_install_regression_passed=true
production_event_middleware_published=false
production_trigger_runtime_wired=false
production_proactive_authority_published=false
production_context_source_registry_published=false
production_active_suggestion_source_wired=false
production_runtime_wired=false
driver_hal_accessed=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该证据只确认 build-owned debug fixtures 在目标 Android ABI/API 上按合同执行，不构成 production Event/Trigger/Consent/Context/
Suggestion authority、Vehicle/NPU/Driver-HAL 或目标硬件 qualification。tracking：`DEV-107`、`ISSUE-031/046`。

## 24. 2026-07-18 P7 Model/Router/Evaluation aggregate Android acceptance evidence

统一 installer 在 Android API 33 / ARM64 上顺序执行 P7-W01..W07 debug probes，并完成 Runtime/Demo 全安装回归。证据仅保留
boolean/count marker，不包含 serial、model、fingerprint、prompt、model output、evaluation content、raw log 或车辆 payload。

```text
device_transport_selected=true
device_identity_redacted=true
android_api=33
device_abi=arm64-v8a
p7_probe_module_count=7
p7_android13_arm64_probe_acceptance_complete=true
model_contract_v2_android13_arm64_verified=true
model_provider_registry_android13_arm64_verified=true
model_policy_router_android13_arm64_verified=true
local_model_provider_android13_arm64_verified=true
structured_model_output_android13_arm64_verified=true
scenario_evaluation_android13_arm64_verified=true
model_resource_admission_android13_arm64_verified=true
android_runtime_full_install_regression_passed=true
production_model_provider_published=false
production_model_router_wired=false
production_inference_enabled=false
production_model_output_runtime_wired=false
production_evaluation_authority_published=false
production_resource_snapshot_provider_wired=false
production_runtime_wired=false
provider_invoked=false
model_invoked=false
network_accessed=false
npu_accessed=false
driver_hal_accessed=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该证据只确认 build-owned debug fixtures 在目标 Android ABI/API 上按合同执行，不构成 production model/quality/resource authority、
network/NPU/Vehicle/Driver-HAL 或目标硬件 qualification。tracking：`DEV-108`、`ISSUE-024/044`。

## 25. 2026-07-18 P9 hardening/release aggregate Android probe evidence

当前源码统一 installer 在一台 identity-redacted Android API 33 / `arm64-v8a` 目标上执行 P9-W01/W02/W03c/W04c/W05b/W06b/W07b。
七项 nonce-bound debug probe 与完整 Runtime/Demo 安装回归通过。只保留下列允许的 boolean/count marker：

```text
p9_android13_arm64_probe_acceptance_complete=true
p9_probe_module_count=7
device_identity_redacted=true
performance_budget_contract_probe_android13_arm64_verified=true
stability_matrix_contract_probe_android13_arm64_verified=true
security_boundary_probe_android13_arm64_verified=true
privacy_redaction_probe_android13_arm64_verified=true
release_metadata_probe_android13_arm64_verified=true
driver_safety_android_contract_probe_android13_arm64_verified=true
field_diagnostics_probe_android13_arm64_verified=true
performance_budget_target_measurement_complete=false
stability_target_72h_complete=false
security_coverage_guided_fuzz_complete=false
privacy_owner_policy_approved=false
production_signer_owner_approved=false
driver_safety_android13_arm64_verified=false
field_diagnostics_target_category_execution_complete=false
release_evidence_target_report_admitted=false
driver_hal_accessed=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该证据不包含 raw log、设备/包身份、证书/signer、target input 或用户/模型/记忆/车辆 payload；不构成 target performance、72h、
coverage fuzz、owner policy、production release/rollback、OEM safety 或目标硬件 qualification。tracking：`DEV-109`、
`ISSUE-029/030/048..053`。

## 26. 2026-07-18 P8-W01 redacted public target inventory

只读 collector 在一台 identity-redacted Android API 33 目标上执行成功。原始 feature/service/command 文件保留在仓库外权限受限
evidence 目录；仓库和报告不包含 serial、fingerprint、设备型号、原始 service 名、车辆值或业务 payload。内部证据引用：
`internal:p8-capability-20260718`。

```text
target_public_inventory_collected=true
target_public_inventory_identity_redacted=true
target_public_inventory_privacy_confirmed=true
target_public_inventory_api33_verified=true
automotive_feature_advertised=true
package_feature_count=69
visible_binder_service_count=274
visible_car_service_match_count=2
visible_command_service_count=265
service_list_collected=true
command_list_collected=true
target_capability_matrix_complete=false
public_car_property_list_available=false
vendor_service_contract_available=false
permission_signature_policy_available=false
target_capability_discovery_external_blocked=true
vehicle_property_mapping_configured=false
production_adapter_registered=false
vendor_npu_provider_available=false
driver_development_triggered=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

公开 surface 可见性不能填充 property/service/area/type/access/permission/owner/version/readback/fault/rollback 矩阵。本证据只关闭
P8-W01 公开 inventory 采集子项；P8-W01 capability mapping 与 P8-W02..W06 继续由 OEM/owner 输入外部阻塞。tracking：
`DEV-085/110`、`ISSUE-047`。

## 27. 2026-07-19 P1-W05 SDK facade healthy reconnect lifecycle

在同一台 identity-redacted Android 13 / API 33 / `arm64-v8a` 目标上安装当前 Runtime debug APK 与 SDK
instrumentation APK。测试创建一个 active session 后连续执行 6 次健康 `reconnect()`；每轮均完成双
Binder connection、authoritative replay 与 callback 重建，随后只产生一次 cancel terminal event，并继续
完成 Room v4 migration probe 和 Runtime process-death recovery。连续次数超过 endpoint 每 session 最多
4 callback 的限制，可检出旧 callback 未注销导致的配额泄漏。

```text
android_api=33
sdk_facade_v2_available=true
active_session_reconnect_resubscribe_verified=true
healthy_reconnect_callback_cleanup_verified=true
callback_replay_deduplicated=true
session_runtime_process_death_rehydration=true
scenario_execution_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

测试临时 SDK instrumentation 包在脚本退出时卸载；报告不保存 serial、fingerprint、设备型号、原始日志、
用户/模型文本或车辆 payload。该证据仅关闭 P1-W05 callback lifecycle 缺口，不构成 Scenario/Effect、
Vehicle/VHAL、NPU、Driver/HAL 或 production Event broker 验收。Req IDs：`S2-SES-001`、`S2-EVT-001`、
`APP-004`、`XSC-006`、`NV-G-003/004`、`DEL-003/004/005`。

## 28. 2026-07-19 P9-W03d Binder identity/current-signer evidence

在 identity-redacted Android 13 / API 33 / `arm64-v8a` 目标上安装 Runtime debug APK 与 SDK instrumentation APK。两个 package
运行在不同 UID；测试通过 signature-protected typed Binder 调用 Runtime，并以 Runtime UID/package 与伪 signer digest 验证调用方输入不能
替代 Binder/PackageManager identity。SDK 端独立计算自身 installed current signer SHA-256，Service 只返回 boolean。

```text
android_api=33
android_abi=arm64-v8a
device_identity_redacted=true
security_identity_device_probe_verified=true
security_distinct_app_uids_verified=true
security_binder_calling_uid_spoof_android_verified=true
security_package_signature_cryptographically_verified=true
security_same_signer_debug_binding_verified=true
security_production_signer_verified=false
security_coverage_guided_fuzz_complete=false
security_runtime_wired=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

设备证据不保存 raw UID、package、signer digest、certificate bytes、serial、fingerprint、设备型号或原始日志。该结果仅证明 installed debug APK
的 Binder caller UID 与 current signer acquisition，不构成 production signer/owner、证书链、release admission、coverage fuzz、Vehicle/NPU/
Driver-HAL 或 target qualification。Req IDs：`S2-SAF-001`、`S2-TOL-001`、`S2-OBS-001`、`DEL-001/004/005`；
tracking：`DEV-111`、`ISSUE-050`。

## 26. 2026-07-19 P9-W03e callback replay security evidence

目标：Android API 33、`arm64-v8a`。Runtime debug/release、Demo debug/androidTest、SDK JVM/androidTest 构建通过。设备上使用三个不同 app UID
（Runtime、Demo owner-A、SDK test owner-B），但报告不保存或显示 UID。owner-A 先完成 shared-key task；owner-B 同键请求得到独立 task。

owner-B 的 exact active replay 返回同 task，重复 sequence 被 SDK guard 丢弃且两个 callback 各一个终态；conflicting payload 在 callback attach
前拒绝且 callback 零调用；post-terminal replay 返回同 task 并只交付一次终态。main/release capability policy 未包含 SDK test principal。

通过标记：`security_task_callback_replay_android_verified=true`、`security_callback_sequence_replay_suppressed=true`、
`security_callback_terminal_replay_unique=true`、`security_idempotency_conflict_callback_silent=true`、
`security_cross_uid_callback_owner_isolation_verified=true`、`security_distinct_callback_owner_uids_verified=true`、
`security_debug_test_principal_release_excluded=true`。`security_coverage_guided_fuzz_complete=false`、
`security_production_signer_verified=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`。
Req IDs：`S2-SAF-001`、`S2-TOL-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-112`、`ISSUE-050`。

## 27. 2026-07-19 P9-W03f host parser robustness and Android compatibility evidence

W03f campaign 本身是 host JVM 工程证据，不在目标设备执行。正式默认 20 秒预算固定 Jazzer 0.30.0 和 6 个 synthetic seed，覆盖 checkpoint、
scenario manifest、Tool input 三类 production Java parser。正式运行结果：executed units 1,152,237、edge coverage 1,145、checkpoint calls
441,158、scenario calls 428,249、Tool calls 282,829、crash artifacts 0、`raw_input_logged=false`；不保存 raw engine log 或 generated corpus。

同一增量继续执行既有 API 33 ARM64 callback replay/Runtime compatibility probe，证明 APK 构建和既有目标安全边界未回归；该设备 probe 不被
表述为 W03f coverage campaign。`security_parser_robustness_host_campaign_verified=true`、
`security_coverage_guided_fuzz_complete=false`、`security_production_signer_verified=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`。Req IDs：`S2-SAF-001`、`S2-TOL-001`、`S2-OBS-001`、
`DEL-001/004/005`；tracking：`DEV-113`、`ISSUE-050`。

## 28. 2026-07-19 P9-W03g executable campaign retirement verification

该项不在设备执行 security campaign。验证目标是证明删除执行面后 Android application 未回归：Gradle dependency/task/source/resource absence
checker 通过；Android debug build 149 tasks、Runtime/Demo release build 167 tasks 通过。Runtime/Demo APK 在 API 33 `arm64-v8a` 成功安装，Demo cold launch `Status: ok`，Runtime/Demo
两个进程均驻留。W03f 历史标量不再代表当前 `main` 可执行能力。

完整长序列 installer 在两个复验中均因 WSL Linux ADB daemon 于约两分钟后退出而中断：一次为 5037 connection refused，一次重启时报告
Windows/WSL 5037 address conflict。中断前 APK 安装和前置 application probes 已通过；该结果不记为完整 installer acceptance，也不归因为 APK。
本增量的目标声明仅限上述短时 API/ABI/install/launch/process compatibility。后续长序列优先使用稳定的 Windows ADB transport 单独复验。

当前只保留外部 evidence interface；`security_external_evidence_interface_defined=true`、`security_requirement_suspended=true`、
`security_test_implementation_present=false`、`security_test_execution_enabled=false`、
`security_external_evidence_admitted=false`、`security_coverage_guided_fuzz_complete=false`、
`security_production_signer_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。Req IDs：`S2-SAF-001`、`S2-TOL-001`、`S2-OBS-001`、`DEL-001/004/005`；
tracking：`DEV-114`、`ISSUE-050`。

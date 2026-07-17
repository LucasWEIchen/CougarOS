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

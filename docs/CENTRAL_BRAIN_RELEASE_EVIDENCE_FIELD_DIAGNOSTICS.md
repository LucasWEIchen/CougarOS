# Central Brain Release Evidence And Field Diagnostics

版本：1.2
日期：2026-07-18
状态：`P9-W07a/W07b/W07c SOFTWARE_DEVELOPED / TARGET_RETEST_EXTERNAL_BLOCKED`

## 1. 目的与需求映射

P9-W07 将发布身份、现场诊断、GitHub issue/retest 流程收敛为可审计交付链。W07a 定义 metadata-only envelope，W07b 提供
debug-only collector/adapter，W07c 定义 replacement release/retest admission；共同对应 `S2-OBS-001`、`S2-REL-001`、
`DEL-001/004/005`。它们不自动上传证据、修改 Issue 或替代 W05 production release admission。

## 2. 模块边界

```text
release identity metadata
  + ordered diagnostic facts
        -> ReleaseEvidenceEnvelope.Report
            -> canonical SHA-256 report digest
                -> ReleaseEvidenceEnvelope.evaluate
                    -> GitHub policy result
                    -> target-owner review eligibility
```

Java 实现位于 `runtime-service` main source，但不被 Runtime/Governance Service 引用。它只依赖 JDK collection、
SHA-256 和 immutable value object；不得读取 Android API、文件、网络、PackageManager、车辆接口、NPU 或 Driver/HAL。

## 3. Release identity

`Identity` 固定接收：

- canonical release tag：`android13-hwtest-vX.Y.Z-rc.N`；
- 40 位 source commit、64 位 archive SHA-256 和 release-set digest；
- 有界 delivery ID、非秘密 device alias、内部 evidence reference；
- 可选 target-owner approval digest；
- signer cohort observed、privacy confirmed、raw/derived identity included、automatic upload enabled 四个布尔标志。

字段只允许严格格式 metadata。不得接收 APK/certificate/signing material、serial/fingerprint、内部路径、用户/模型文本、车辆值、
target input、raw log 或任意 payload。

## 4. Diagnostic fact catalog

报告必须按以下顺序包含且仅包含八类事实：

1. `release.bundle`
2. `installer.dry_run`
3. `installer.execute`
4. `demo.launch`
5. `client2.launch`
6. `runtime.service`
7. `diagnostics.service`
8. `manual.scenario_matrix`

状态固定为 `PASS/FAIL/BLOCKED/NOT_RUN`。`NOT_RUN` 必须使用 `result_code=-1` 且没有 digest；`PASS` 必须为
`result_code=0`；`FAIL/BLOCKED` 必须为 1..255。所有已执行事实只携带 64 位 detail digest，不携带原始内容。

## 5. Evaluation semantics

GitHub-safe 要求 `privacy_confirmed=true`、`raw_or_derived_device_identity_included=false` 且
`automatic_upload_enabled=false`，否则返回 `GITHUB_POLICY_REJECTED`。Host synthetic 报告只返回
`HOST_SOFTWARE_ONLY`。

Target 报告只有在存在 target-owner approval digest 且八类事实全部已执行时，才可返回
`TARGET_OWNER_REVIEW_ELIGIBLE`。该结果只是“可进入 owner 评审”，始终保持 `production_ready=false` 和
`target_hardware_validated=false`；PASS/FAIL/BLOCKED 的组合由后续 owner/retest policy 判定，W07a 不擅自决定验收。

## 6. Stable digest

`Report` 将 profile、schema、mode、全部 identity metadata 和八类有序事实 canonicalize 后计算 SHA-256。字段、顺序、
状态、result code 或 detail digest 变化都会改变 report digest；相同输入必须产生相同摘要。摘要用于绑定证据引用，不证明证据真实性。

## 7. 后续工作包

- `P9-W07b`：已交付 debug-only Android field diagnostics probe 与 no-install host adapter，只输出本合同允许的 metadata；
- `P9-W07c`：replacement release、GitHub issue triage、retest 状态机与 owner-controlled evidence admission；
- 外部依赖：目标 transport、命名 release/diagnostics/test owner、受控 evidence、production signer/installer/rollback authority。

当前：`release_evidence_envelope_defined=true`、`release_evidence_diagnostic_category_count=8`、
`release_evidence_report_digest_defined=true`、`release_evidence_target_owner_approved=false`、
`release_evidence_target_report_admitted=false`、`release_evidence_runtime_diagnostics_wired=false`、
`release_evidence_retest_workflow_wired=false`、`release_evidence_automatic_upload_enabled=false`、
`release_evidence_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W07`。tracking：`DEV-098`、`ISSUE-053`。

## 8. P9-W07b Android field diagnostics

`FieldDiagnosticsProjection` 接收三包 aggregate count、Demo/Client2 launchability 和 Runtime/Diagnostics Service declaration booleans，
输出精确 31 个 count/boolean key。Projection 不包含包名、路径、serial/fingerprint、certificate/signature、target input、raw log、
用户/模型/memory/token/vehicle payload；不执行 Activity、Service、installer、rollback 或 upload。

`FieldDiagnosticsProbeActivity` 仅存在 debug source set，要求 DUMP、NoDisplay、noHistory，并只接受 1..24 位数字 nonce。它使用标准
PackageManager 查询三包 version/signer relation、两个 launcher intent 和两个本应用 Service declaration，但只记录 aggregate projection。

`probe_central_brain_android_field_diagnostics.sh` 要求已安装 debug Runtime、Android 13 API 33 和 ARM64。它不 build/install/uninstall/
rollback/upload；实际执行 release bundle preflight、Demo/Client2 launch、Runtime probe 和 Diagnostics Binder probe 五类 category，另外三类
`installer.dry_run`、`installer.execute`、`manual.scenario_matrix` 明确输出 `NOT_RUN/-1/no digest`。执行事实只输出 category、status、
result code 和 SHA-256 detail digest；内部 start/logcat 内容不持久化、不回显。

W07b 软件可用不代表目标已执行。当前 ADB transport 不合格，因此 repository claim 保持
`field_diagnostics_projection_defined=true`、`field_diagnostics_audit_key_count=31`、
`field_diagnostics_android_debug_probe_available=true`、`field_diagnostics_android_debug_probe_executed=false`、
`field_diagnostics_target_adapter_defined=true`、`field_diagnostics_target_category_execution_complete=false`、
`release_evidence_target_report_admitted=false`、`release_evidence_runtime_diagnostics_wired=false`、
`release_evidence_retest_workflow_wired=false`、`release_evidence_automatic_upload_enabled=false`、
`field_diagnostics_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W07`。tracking：`DEV-099`、`ISSUE-052/053`。

## 9. P9-W07c replacement release and issue/retest workflow

`ReleaseRetestWorkflow` 延续既有 GitHub 远程测试状态目录，精确实现 5 个状态与 5 条 actor-bound 转换。Maintainer 只能推进
triage -> reproduced -> fix-ready，并在绑定命名 replacement release 后进入 retest；Target tester 只能把 retest 推进到 verified，
或在完整非 PASS 报告后退回 fix-ready。

Replacement release 必须严格高于当前 release tag，并使用不同的 source commit、archive SHA-256 和 release-set digest，同时携带 release
owner approval digest。Verified admission 要求 W07a TARGET report 身份完全匹配 replacement release、GitHub privacy gate 通过、八类全部
执行且全部 PASS，观察到 signer cohort，并同时存在四个互不相同的 target owner、release owner、diagnostics owner 和 tester verification digest。

完整 FAIL/BLOCKED 报告不会丢失 Issue：snapshot 保存 report digest 并回到 fix-ready；下一轮必须使用更高 replacement release。Snapshot
digest 绑定 issue number、state、原始/替代 release、cycle 与 last report，不接受 Issue title/body、raw evidence 或 payload。

软件 decision 中的 admitted/close-eligible 只验证算法。Repository 当前没有真实 owner/target evidence，因此不发布 release、不修改 GitHub
Issue、不自动关闭、不安装/rollback，也不提升硬件或 production 状态。W07a/W07b/W07c 仓库软件项已完成，外部复测仍保持 open。

当前 `release_retest_state_machine_defined=true`、`release_retest_issue_state_count=5`、
`release_retest_transition_count=5`、`release_retest_replacement_release_published=false`、
`release_evidence_target_report_admitted=false`、`release_evidence_retest_workflow_wired=false`、
`release_retest_github_issue_mutation_wired=false`、`release_retest_automatic_issue_close_allowed=false`、
`release_evidence_automatic_upload_enabled=false`、`release_retest_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W07`。tracking：`DEV-100`、`ISSUE-052/053`。

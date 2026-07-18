# Central Brain Release Evidence And Field Diagnostics

版本：1.0
日期：2026-07-18
状态：`P9-W07a SOFTWARE_CONTRACT_DEFINED / TARGET_EVIDENCE_PENDING`

## 1. 目的与需求映射

P9-W07 将发布身份、现场诊断、GitHub issue/retest 流程收敛为可审计交付链。W07a 仅实现 metadata-only
`ReleaseEvidenceEnvelope`，对应 `S2-OBS-001`、`S2-REL-001`、`DEL-001/004/005`。它不采集目标数据、不执行
ADB、不上传证据，也不替代 W05 production release admission。

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

- `P9-W07b`：debug-only Android field diagnostics probe 与 no-install host adapter，只输出本合同允许的 metadata；
- `P9-W07c`：replacement release、GitHub issue triage、retest 状态机与 owner-controlled evidence admission；
- 外部依赖：目标 transport、命名 release/diagnostics/test owner、受控 evidence、production signer/installer/rollback authority。

当前：`release_evidence_envelope_defined=true`、`release_evidence_diagnostic_category_count=8`、
`release_evidence_report_digest_defined=true`、`release_evidence_target_owner_approved=false`、
`release_evidence_target_report_admitted=false`、`release_evidence_runtime_diagnostics_wired=false`、
`release_evidence_retest_workflow_wired=false`、`release_evidence_automatic_upload_enabled=false`、
`release_evidence_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W07`。tracking：`DEV-098`、`ISSUE-053`。

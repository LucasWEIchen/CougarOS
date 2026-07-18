# Central Brain P9-W04 Privacy and Data Lifecycle

Status: `W04C_SOFTWARE_VERIFIED / TARGET_PROBE_PENDING / OWNER_POLICY_INPUT_OPEN`

W04a baseline: `W04A_INVENTORY_VERIFIED / POLICY_GAPS_OPEN`

W04b baseline: `W04B_ADMISSION_DEFINED / OWNER_POLICY_INPUT_OPEN`

Req IDs: `S2-MEM-001`, `S2-SAF-001`, `S2-OBS-001`, `DEL-001/004/005`.

## 1. W04 decomposition

P9-W04 is split into three independently reviewable increments:

1. `P9-W04a`: freeze the current Android data-surface inventory and expose missing lifecycle policy.
2. `P9-W04b`: enforce retention/delete/export policy against existing bounded stores and durable repositories.
3. `P9-W04c`: verify redaction/audit projection through a protected debug-only Android probe.

W04a does not add a store, Binder endpoint, Room table, data export service or production authority. It is a metadata
contract used to prevent existing storage and transient boundaries from disappearing behind broad architecture labels.

## 2. Current data surfaces

| Surface | Storage | Content boundary | Retention/delete | Export | W04a state |
| --- | --- | --- | --- | --- | --- |
| `durable.session_event` | Room | fixed owner/session/scenario/event metadata | terminal capacity + cascade | forbidden | partial runtime |
| `durable.plan_graph` | Room | graph/task/checkpoint digests and fixed control state | session cascade | forbidden | partial runtime |
| `durable.effect_recovery` | Room | effect/outbox/readback/compensation digests | owner policy missing; no public delete | forbidden | policy gap |
| `durable.approval` | Room | fixed approval/risk/reason metadata | expiry bounded | forbidden | partial runtime |
| `durable.audit` | Room | event/outcome/detail digest metadata | owner policy missing; no public delete | forbidden | policy gap |
| `durable.event_cursor` | Room | owner/topic/cursor/overflow metadata | cancelled capacity bounded | forbidden | partial runtime |
| `memory.working` | process local | bounded opaque content | TTL + session terminal cleanup | forbidden | contract-test only |
| `memory.profile` | process local | sealed typed preference | maximum 30 days + authorized delete | authorized and bounded | contract-test only |
| `memory.episodic` | process local | fixed scenario/result enum summary | maximum 30 days + authorized erase | forbidden | contract-test only |
| `events.broker` | process local | typed digest metadata | bounded capacity eviction | forbidden | contract-test only |
| `tools.execution_audit` | process local | invocation/outcome digest metadata | 128-entry capacity bound | forbidden | contract-test only |
| `model.inference_boundary` | transient | bounded request/output content | call-only; not stored | forbidden | transient enforced |

The machine-readable source of truth is
`central-brain/contracts/central_brain_android_p9_privacy_data_inventory.json`. Every row names the Java classes that
own the current boundary. The repository checker fails if a source disappears, the JSON and Java tuples differ, a
content-carrying surface allows content logging, or another surface gains external export.

## 3. Classification rules

1. `DURABLE_ROOM` means process-death persistence in `central_brain_runtime.db`; it does not mean the OEM approved its
   retention period or erase semantics.
2. `PROCESS_LOCAL` means bounded in-memory behavior verified by contract tests. It is not production durable Memory.
3. `TRANSIENT_ONLY` must use `NOT_STORED`; no delete API is needed because the contract must not retain the content.
4. A `POLICY_GAP` must use `OWNER_POLICY_MISSING`. W04a has exactly two: durable Effect recovery and durable Audit.
5. `AUTHORIZED_BOUNDED` export is allowed only for `memory.profile`, with explicit consent and separate delete/export
   authorization. Session listing, event replay and diagnostics are not privacy exports.
6. Any surface that accepts content must set `CONTENT_FORBIDDEN`. W04a identifies Working Memory, Profile Memory and
   the transient model boundary; none is production Runtime wired.

## 4. Existing guarantees reused by W04a

- Session persistence does not store utterances; durable event payload canonical text remains empty on the published
  Session path.
- Room task/graph/effect/audit records retain fixed fields and digests rather than arbitrary model or vehicle payload.
- Working Memory clears bounded retained bytes on expiry/remove/terminal cleanup but remains contract-test-only.
- Profile Memory requires explicit consent, separate delete/export authority, a sealed payload owner gate and 30-day
  maximum retention; its current XOR owner is not production encryption.
- Episodic Memory stores fixed scenario/result summaries and provides authorized owner/episode erase without accepting
  user/model text or continuous vehicle signals.
- Model output may be handled transiently by the bounded parser, but raw output persistence and content logging remain
  forbidden.

## 5. Open policy gaps

`durable.effect_recovery` and `durable.audit` have valid recovery/audit purposes, but the repository does not yet carry
an owner-approved retention schedule, terminal deletion rule, legal hold rule, bounded privacy export rule or explicit
erase authority. W04a records this as a blocking gap rather than inventing a duration.

W04b must define a policy version/digest, classification binding, per-surface retention ceiling, delete/erase
authorization, exact replay/conflict behavior and redacted evidence. A policy may narrow existing technical retention;
it must not delete active safety recovery state or silently export content.

## 6. Security and privacy boundaries

The W04a implementation is main-source metadata only. It does not import Android/Room/file/network APIs, inspect the
database, read logs, accept payloads, call a model, access a vehicle/NPU, or wire Runtime/Governance Services. The
inventory itself contains class paths and fixed policy enums only.

Current claims: `privacy_data_inventory_complete=true`, `privacy_data_surface_count=12`,
`privacy_durable_surface_count=6`, `privacy_process_local_surface_count=5`,
`privacy_transient_surface_count=1`, `privacy_policy_gap_count=2`,
`privacy_authorized_export_surface_count=1`, `privacy_raw_user_text_persisted=false`,
`privacy_raw_model_output_persisted=false`, `privacy_raw_vehicle_payload_persisted=false`,
`privacy_location_persisted=false`, `privacy_audit_content_logged=false`,
`privacy_owner_policy_approved=false`, `privacy_production_lifecycle_complete=false`,
`privacy_runtime_lifecycle_wiring_complete=false`, `privacy_android13_arm64_verified=false`,
`hardware_accessed=false`, `production_ready=false`, `target_hardware_validated=false`,
`implementation_stage=P9-W04`.

## 7. P9-W04b owner policy admission

`PrivacyLifecyclePolicyAdmission` 只处理 metadata，不接收内容、设备标识或原始车辆数据。输入由 policy identity/version、W04a inventory
digest、12 个精确 surface rule 和三项 owner approval evidence 组成。approval 只允许保存 policy body、inventory 和审批引用的 SHA-256，
不得把审批文件、签名材料或人员信息写入合同。

当前 `cougaros-privacy-draft/0.1.0-draft` 对 10 个已有 surface 使用 `INVENTORY_BOUND`；
`durable.effect_recovery` 与 `durable.audit` 保持 `OWNER_INPUT_REQUIRED` 且 retention ceiling 未设置。因此当前草案必须同时返回
`SURFACE_POLICY_UNRESOLVED`、`RETENTION_CEILING_INVALID` 和 `OWNER_APPROVAL_MISSING`，不能激活。

完整候选策略必须满足：

1. surface 顺序、数量和 inventory digest 精确一致；
2. Effect recovery 必须有正数 retention ceiling 和 `ACTIVE_EFFECT_AND_COMPENSATION` guard；
3. Audit 必须有正数 retention ceiling 和 `LEGAL_AND_SAFETY` guard；
4. Privacy、Functional Safety、Compliance 三类 owner 必须各提供一项绑定相同 policy body/inventory 的 digest evidence；
5. 任一 role/reference 缺失或重复、policy/inventory digest 不一致均 fail closed。

## 8. Delete / erase / export preflight

准入成功也只产生 preflight metadata，不授予 repository mutation 或 Runtime authority。Effect recovery 在 active Effect 或 pending
compensation 大于零时禁止删除；Audit 在 legal/safety hold 大于零时禁止删除。外部 export 仍只允许 `memory.profile`，且同时要求
authorization digest 与 consent receipt digest。所有 decision 固定 `dataWasMutated=false`、`dataWasExported=false`。

JVM 的 owner-approved policy 仅为 synthetic contract fixture，其 retention 数值和 approval digest 不代表产品策略、目标设备证据或 owner
批准。仓库真实状态保持：

```text
privacy_policy_admission_defined=true
privacy_current_policy_admitted=false
privacy_owner_policy_approved=false
privacy_repository_mutation_wired=false
privacy_runtime_lifecycle_wiring_complete=false
privacy_android13_arm64_verified=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
implementation_stage=P9-W04
```

## 9. P9-W04c redacted audit projection

`PrivacyRedactionAuditProjection` 复验 W04a inventory 与 W04b current draft，只投影 21 个固定 key：两个 SHA-256、四个计数和
15 个 boolean。输出不包含 surface ID、source class、owner approval reference、authorization/consent digest、设备身份或任何原始
user/model/vehicle/location 内容。JVM 逐键验证 allowlist、正则字符集、精确计数及 no-authority false claim。

## 10. Android debug probe boundary

`PrivacyRedactionAuditProbeActivity` 只存在于 debug source，使用 `android.permission.DUMP`、`NoDisplay`、`noHistory`。唯一输入是
1..24 位数字 correlation nonce；其他 Intent extras/data/clip 均不读取。Activity 调用固定 projection 后写入 tag `CbPrivacyProbe`，不打开
Room、文件、网络、车辆、NPU 或 Vendor API；异常只记录异常类型，不记录 stack/payload。

installer 只匹配 nonce、digest 形状、计数和固定 boolean。release source/manifest 不含 probe。当前 ADB 无 transport，故仅能声明软件入口
available，不能声明 executed/Android verified：

```text
privacy_redacted_audit_projection_defined=true
privacy_android_debug_probe_available=true
privacy_android_debug_probe_executed=false
privacy_android13_arm64_verified=false
privacy_owner_policy_approved=false
privacy_repository_mutation_wired=false
privacy_runtime_lifecycle_wiring_complete=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
implementation_stage=P9-W04
```

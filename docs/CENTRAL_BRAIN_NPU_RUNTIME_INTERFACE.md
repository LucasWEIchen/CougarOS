# NPU Runtime Adapter 接口约束

版本：0.1
日期：2026-07-08

## 范围声明

本文覆盖外置 PCIe NPU 从 Uni Info Bus/SOA 到 Model Runtime Adapter、Driver/HAL 的接口约束，映射 Req ID：HW-002、NV-F-011、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。

本轮不开发 NPU driver、HAL、DMA、IOMMU、Safety Runtime 或虚拟化层；只固定 Android/Linux 座舱域工程师后续集成时必须满足的 contract、状态机、错误码和验证入口。`GET /hardware/interfaces` 中的 `npu-runtime` 是当前 Python 原型的 NPU 空接口预留，`GET /hardware/interfaces/activation-checklist` 是激活前 owner、ABI、Driver/HAL gap、Safety/Policy、smoke evidence 和 rollback/fault 门禁，`GET /hardware/interfaces/owner-decision-status` 是 target owner、Android ABI owner、Linux ABI owner、Driver/HAL gap owner、Safety/Policy owner、target smoke evidence owner 和 rollback/fault semantics owner 的未决状态汇总，`POST /hardware/interfaces/owner-decision-evidence` 是 no-store owner/smoke/ABI/Driver-HAL/Safety gate evidence reference intake，`GET /hardware/interfaces/owner-decision-evidence/status` 是 no-store evidence status rollup，`GET /hardware/interfaces/owner-decision-evidence/retention-checklist` 是 no-store retention/closure checklist，`GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist` 是 no-store replacement trigger checklist，`GET /hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist` 是 no-store selected-adapter readiness checklist，`GET /hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup` 是 no-store adapter-load blocker rollup，`POST /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run` 是 no-store adapter-load approval dry-run rejection，`GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status` 是 no-store dry-run status/last-result view；真实硬件、vendor SDK、driver ABI 或目标 SoC 明确后，才按缺口新增最小开发量。

## 分层边界

| 层级 | Android 主开发路径 | Linux 同步交付路径 | 不允许绕过的边界 | Req ID |
| --- | --- | --- | --- | --- |
| App / AI SDK | AI SDK client 调用 Uni Info Bus/SOA，不直连 vendor SDK | CLI/SDK sample 调用 Uni Info Bus/SOA | App 不直接访问 `/dev/*`、HAL、vendor lib | XSC-001, XSC-002, XSC-003 |
| Model Runtime Adapter | Binder/native service 或 vendor SDK JNI/NDK bridge | runtime daemon 或 vendor runtime library wrapper | 所有推理经 Policy、Lifecycle、Audit | NV-F-011, NV-G-005, NV-G-006, NV-G-007 |
| Driver/HAL | NPU HAL/AIDL/NDK bridge、vendor service | `/dev/*`、ioctl、sysfs、DMA-BUF、vendor lib | 只暴露受控 capability，不暴露原始 unsafe 操作给 App | KH-003, KH-006 |
| Safety Runtime | 平台 safety service/HAL 状态输入 | daemon/sysfs/socket/vendor lib 状态输入 | fault/interlock 必须返回受控错误或 readonly fallback | KH-007, FW-S-005 |
| Hardware | 外置 PCIe NPU 卡或 SoC NPU | 外置 PCIe NPU 卡或 SoC NPU | 硬件差异由 adapter 屏蔽 | HW-002 |

## 最低 API 抽象

NPU 后端必须至少提供以下抽象，供 Model Runtime Adapter 映射到 Android Binder/native service 或 Linux daemon。

| API | 输入 | 输出 | 策略要求 | Req ID |
| --- | --- | --- | --- | --- |
| `NpuDevice.discover()` | vendor filter、capability filter | device list、driver version、firmware version、health | 只读，可在 degraded 下执行 | HW-002, KH-003 |
| `NpuDevice.getStatus()` | device id | health、thermal、power、queue depth、fault code | fault/interlocked 时不得返回 ok | KH-003, KH-007 |
| `NpuModel.load(model_spec)` | model id、version、signature、memory hint | model handle、compiled artifact id | 必须校验签名和 allowed safety state | NV-F-011, KH-006 |
| `NpuModel.unload(model_id)` | model handle | unload state | 生命周期必须审计 | NV-F-011, NV-G-007 |
| `NpuSession.create(model_id, qos, safety_state)` | model handle、QoS、safety state | session id、effective backend | 必须经过 Policy 和 Lifecycle | NV-F-011, NV-G-005, NV-G-006 |
| `NpuSession.infer(input_buffers, output_spec, timeout_ms)` | buffer refs、shape、format、timeout | request id、result refs、metrics | 超时、fault、policy deny 必须结构化返回 | NV-F-011, KH-003 |
| `NpuSession.cancel(request_id)` | request id、reason | cancel state | 必须可审计 | NV-F-011, NV-G-007 |
| `NpuDevice.getMetrics()` | device id、window | utilization、latency、error counters | 只读，可用于治理 QoS | NV-G-004, KH-003 |
| `NpuDevice.reset(reason)` | device id、reason、operator token | reset state | 仅 diagnostic/admin；默认不暴露给 App | KH-003, KH-007 |

## 统一请求 Envelope

Model Runtime Adapter 接收的推理请求必须保留 Uni Info Bus/SOA 的治理字段，不允许被 binding 或 vendor SDK 抹掉。

```json
{
  "trace_id": "uuid",
  "caller": {
    "app_id": "com.centralbrain.console",
    "role": "debug_console"
  },
  "permission_context": {
    "permissions": ["ai.infer"],
    "safety_state": "normal",
    "vehicle_state": "parked",
    "privacy_level": "local_only"
  },
  "qos": {
    "priority": "normal",
    "timeout_ms": 2000,
    "deadline_ms": 0
  },
  "payload": {
    "model": "central-intent-v0",
    "input_buffers": [],
    "output_spec": {"format": "json"}
  }
}
```

## 状态机与错误码

| 状态 | 含义 | 可执行操作 | Req ID |
| --- | --- | --- | --- |
| `unavailable` | driver/HAL/vendor runtime 不存在 | discover/status | KH-003, DEL-005 |
| `ready` | device、runtime、policy 均可用 | load/create/infer/metrics | NV-F-011 |
| `degraded` | 可读状态或低风险 fallback | discover/status/metrics，必要时 CPU/cloud fallback | FW-S-005, KH-007 |
| `fault_isolated` | NPU fault 已隔离 | status/metrics/reset，不允许新推理 | KH-007 |
| `interlocked` | Safety State 禁止执行 | status/audit，不允许 load/infer/reset | FW-S-005, KH-007 |

| 错误码 | 触发条件 | 调用方处理 |
| --- | --- | --- |
| `NPU_DEVICE_UNAVAILABLE` | driver、HAL、vendor runtime 或设备节点不可用 | 返回 structured error，不自动直连 mock 伪装量产 |
| `NPU_POLICY_DENIED` | 权限、安全状态或车辆状态不满足 | 经 Policy/Audit 记录，App 显示受控失败 |
| `NPU_MODEL_REJECTED` | 模型签名、版本、shape 或 safety state 不满足 | 拒绝加载或推理 |
| `NPU_TIMEOUT` | 排队或推理超时 | cancel request 并记录 metrics |
| `NPU_FAULT_ISOLATED` | 设备 fault、IOMMU/DMA 错误、Safety Runtime fault | 进入 fault_isolated，禁止新任务 |
| `NPU_BACKEND_FALLBACK_REQUIRED` | NPU 不可用但策略允许 CPU/cloud fallback | fallback 仍必须经 Model Runtime Adapter 和 Policy |

## Driver/HAL 集成检查点

真实硬件集成前，座舱域工程师至少需要补齐以下信息：

| 检查项 | Android 侧 | Linux 侧 | 缺口记录 |
| --- | --- | --- | --- |
| 设备发现 | HAL/AIDL service name、SELinux domain、vendor SDK version | PCI vendor/device id、`/dev/*` node、udev rule、driver module | DRV-GAP-001 |
| Buffer | HardwareBuffer/AHardwareBuffer、shared memory policy | DMA-BUF、memfd、IOMMU group、cache coherency | DRV-GAP-001 |
| 模型生命周期 | model signature、storage path、rollback policy | model repository、signature verifier、runtime cache | DRV-GAP-001 |
| 错误恢复 | HAL death recipient、watchdog、Safety State 输入 | reset ioctl/sysfs、watchdog、fault syslog | DRV-GAP-001 |
| 权限 | app op、Binder identity、SELinux | service user/group、socket permissions、capabilities | DRV-GAP-001 |

## 当前项目状态

- `GET /npu/status` 仍是 mock runtime 状态，不表示真实 driver/HAL 可用。
- `GET /hardware/interfaces` 的 `npu-runtime` 只暴露 reserved methods 和 Android/Linux 目标路径，`hardware_accessed=false`，不表示 HAL、vendor SDK、DMA/IOMMU 或 Safety Runtime 可用。
- `GET /hardware/interfaces/activation-checklist` 只暴露 `HW-ACT-001..008` 激活前门禁，`activation_allowed=false`、`owner_decision_complete=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`，不表示 PCIe NPU、HAL、vendor SDK、DMA/IOMMU 或 Safety Runtime 已可用。
- `GET /hardware/interfaces/owner-decision-status` 只暴露 `HW-ODS-001..008` owner 决策状态，`all_required_owners_assigned=false`、`target_hardware_smoke_attached=false`、`rollback_fault_semantics_confirmed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`，不表示 PCIe NPU、HAL、vendor SDK、DMA/IOMMU 或 Safety Runtime 已可用。
- `POST /hardware/interfaces/owner-decision-evidence` 只校验 `HW-ODE-001..008` evidence intake envelope，`owner_decision_evidence_persisted=false`、`review_queue_updated=false`、`owner_assigned=false`、`gate_state_changed=false`、`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`，不表示 evidence store、review workflow、PCIe NPU、HAL、vendor SDK、DMA/IOMMU 或 Safety Runtime 已可用。
- `GET /hardware/interfaces/owner-decision-evidence/status` 只报告 `HW-OES-001..008` no-store status，`evidence_store_active=false`、`review_workflow_active=false`、`persisted_submission_count=0`、`pending_review_count=0`、`owner_assigned=false`、`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`，不表示 evidence store、review workflow、gate closure authority、PCIe NPU、HAL、vendor SDK、DMA/IOMMU 或 Safety Runtime 已可用。
- `GET /hardware/interfaces/owner-decision-evidence/retention-checklist` 只报告 `HW-OER-001..008` retention/closure checklist，`owner_decision_complete=false`、`retention_policy_confirmed=false`、`evidence_uri_rules_confirmed=false`、`delete_workflow_active=false`、`export_workflow_active=false`、`gate_closure_allowed=false`、`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`，不表示 durable evidence store、URI dereference、review workflow、delete/export workflow、gate closure authority、PCIe NPU、HAL、vendor SDK、DMA/IOMMU 或 Safety Runtime 已可用。
- `GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist` 只报告 `HW-OET-001..008` replacement trigger checklist，`replacement_policy_confirmed=false`、`replacement_target_selected=false`、`driver_hal_gap_closure_evidence_confirmed=false`、`rollback_to_empty_interface_plan_confirmed=false`、`replacement_allowed=false`、`adapter_activation_allowed=false`、`gate_closure_allowed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`，不表示 adapter replacement、adapter activation、Driver/HAL gap closure、PCIe NPU、HAL、vendor SDK、DMA/IOMMU 或 Safety Runtime 已可用。
- `GET /hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist` 只报告 `HW-OEA-001..008` selected-adapter readiness checklist，`adapter_candidate_recorded=false`、`adapter_owner_assigned=false`、`adapter_interface_contract_approved=false`、`driver_hal_gap_evidence_attached=false`、`adapter_load_allowed=false`、`adapter_activation_allowed=false`、`hardware_access_allowed=false`、`gate_closure_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`，不表示 adapter selection、adapter load、adapter activation、Driver/HAL gap closure、PCIe NPU、HAL、vendor SDK、DMA/IOMMU 或 Safety Runtime 已可用。
- `GET /hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup` 只报告 `HW-ALB-001..008` adapter-load blocker rollup，`adapter_load_ready=false`、`all_blockers_cleared=false`、`adapter_load_allowed=false`、`adapter_activation_allowed=false`、`hardware_access_allowed=false`、`gate_closure_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`，不表示 adapter load、adapter replacement、adapter activation、Driver/HAL gap closure、PCIe NPU、HAL、vendor SDK、DMA/IOMMU 或 Safety Runtime 已可用。
- `POST /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run` 只报告 `HW-ALD-001..008` adapter-load approval dry-run rejection，`adapter_load_dry_run_state=rejected_blocked_contract_only`、`adapter_load_allowed=false`、`adapter_activation_allowed=false`、`hardware_access_allowed=false`、`gate_closure_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`，不表示 adapter load approval、adapter selection、adapter activation、Driver/HAL gap closure、PCIe NPU、HAL、vendor SDK、DMA/IOMMU 或 Safety Runtime 已可用。
- `GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status` 只报告 `HW-ALS-001..008` adapter-load dry-run no-store status，`last_result_available=false`、`persisted_dry_run_count=0`、`pending_review_count=0`、`review_queue_updated=false`、`evidence_persisted=false`、`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`，不表示 evidence store、review workflow、last-result retention、adapter load approval、PCIe NPU、HAL、vendor SDK、DMA/IOMMU 或 Safety Runtime 已可用。
- `npu-inference` SOA service 已通过 Runtime & Governance precheck，但底层仍调用 mock 推理。
- `Model Runtime Adapter` 已在 `/native/adapters/detail` 中登记 Android/Linux 交付边界。
- 真实 NPU 接入前，DEV-005 保持 Open；新增驱动开发只在硬件、SDK、driver ABI 明确后触发。

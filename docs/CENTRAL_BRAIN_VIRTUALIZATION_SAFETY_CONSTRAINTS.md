# 虚拟化与 Safety 接口约束

版本：0.1
日期：2026-07-04

## 范围声明

本文件覆盖架构图 L5 虚拟化层的接口约束和部署假设，不开发 Hypervisor、ASIL/QM 隔离或跨 VM 共享内存实现。

Req ID：HV-001、HV-002、HV-003、FW-S-005、NV-G-005、NV-F-009、KH-007、DEL-004。

虚拟化层是集成边界，不是本仓库的代码交付范围。当前 Android/Linux 原型只通过 Uni Info Bus、SOA 服务入口、Runtime & Governance、Protocol Binding 和 Native adapter registry 表达上层约束；真实 Hypervisor、Safety Runtime、跨 VM channel 由目标 SoC/平台提供。

## 分层约束

| Req ID | 图中模块 | 本项目处理 | 不做内容 | 下游依赖 |
| --- | --- | --- | --- | --- |
| HV-001 | Hypervisor | 记录 domain、transport、共享内存和启动依赖假设 | 不实现虚拟化调度、VM 生命周期或 vDevice | 目标 SoC Hypervisor、AAOS/Linux domain 配置 |
| HV-002 | ASIL/QM 隔离 | 定义服务到 ASIL/QM domain 的映射和降级策略 | 不实现安全隔离机制或安全认证 | Safety Runtime、ASIL/QM domain policy |
| HV-003 | 跨 VM 共享内存与安全域通信 | 定义跨 VM envelope、fallback 和协议 binding 约束 | 不实现共享内存驱动、doorbell、中断或 hypercall | Hypervisor shared memory、virtio、vendor IPC |
| FW-S-005 | Safety State | 所有 SOA 调用必须带 safety state 并经 Policy/Lifecycle precheck | 不绕过 SOA 直接调用底层服务 | Runtime & Governance policy |
| NV-G-005 | Policy | Policy decision 必须能表达 allow/deny/degraded/readonly | 不在 App 层硬编码安全判定 | Native Security/Policy Adapter |
| NV-F-009 | Security/Policy Adapter | 记录 Android/Linux 到安全域策略的适配边界 | 不实现量产安全策略引擎 | 平台 policy engine、SELinux/LSM |
| KH-007 | Safety Runtime | 记录 Safety Runtime 对 Kernel/HAL 的依赖和错误恢复假设 | 不实现 Safety Runtime | 目标 OS 与芯片安全能力 |

## Safety State 到安全域映射

| 服务类别 | Req ID | 默认 Safety State | 目标安全域假设 | Android 主路径 | Linux 同步路径 | fallback |
| --- | --- | --- | --- | --- | --- | --- |
| 车况读取 / Context / State | FW-U-001, FW-U-002, XSC-002 | normal, degraded, diagnostic_readonly | QM domain read-only | Binder/AIDL 或 REST prototype，经 Policy allow | Unix socket IPC 或 CLI，经 Policy allow | 返回最近可信快照并标注 stale |
| 座舱舒适控制 | FW-U-004, FW-S-005, XSC-003 | normal | QM domain controlled write | SOA invoke + Binder caller identity + Policy | SOA invoke + service user/group + Policy | driving 或 degraded 时拒绝写操作 |
| 诊断读取 | APP-009, FW-S-005 | diagnostic_readonly | QM/ASIL boundary read-only | 受控诊断 service，不直连 VHAL/driver | 诊断 daemon/client 经 IPC binding | 只允许 readonly，写入和标定需独立授权 |
| ADAS/智驾状态读取 | APP-007, NV-F-010 | normal, degraded | ASIL domain producer, QM consumer | App 只读 Safety State 或 ADAS service state | Linux client 只读 protocol binding | ASIL domain unavailable 时返回 unavailable |
| 模型推理 / Agent 任务 | APP-004, NV-F-001, NV-F-011 | normal | QM domain，必要时隔离到 AI domain | AI SDK/AIOS Kernel -> Model Runtime Adapter | Linux CLI/daemon -> Model Runtime Adapter | NPU 不可用时 CPU/cloud fallback 需经 Policy |
| OTA/远控/TBOX | APP-005, NV-F-007 | parked 或 diagnostic_readonly | QM domain with remote trust boundary | TBOX/OTA service 经 Privacy/Policy | Connected daemon 经 Policy 和 audit | 未满足车辆状态或身份时拒绝 |

## 跨 VM 通信 envelope 约束

跨 VM 通信必须保留 Uni Info Bus/SOA 语义，不允许把 Hypervisor channel 暴露成 App 可直接调用的底层接口。

最小 envelope 字段：

```json
{
  "trace_id": "uuid",
  "source_domain": "android-qm",
  "target_domain": "safety-asil",
  "req_ids": ["HV-003", "FW-S-005", "NV-G-005"],
  "semantic_operation": "soa.service.invoke",
  "safety_state": "normal",
  "policy_decision": "allow",
  "payload_ref": {
    "type": "inline-json",
    "handle": null,
    "schema": "central-brain.soa.invoke.v1"
  },
  "deadline_ms": 100,
  "fallback": "readonly-state"
}
```

约束：

- `semantic_operation` 必须映射到 Uni Info Bus、SOA、Policy、Governance 或 Protocol Binding 的已登记操作。
- `source_domain` 和 `target_domain` 必须由目标平台安全域配置提供，当前原型只记录假设。
- 共享内存只允许放 `payload_ref`，不能绕过 schema、Policy、Lifecycle 和 Audit。
- ASIL domain 不可用时，上层只允许 readonly fallback 或明确失败，不允许自动降级为不受控写操作。
- 所有跨域调用必须进入 Audit；当前原型支持可选 JSONL 最近审计恢复，但仍不是量产审计后端，偏差见 DEV-006。

## Android/Linux 集成假设

| 维度 | Android 主开发路径 | Linux 同步路径 | Req ID |
| --- | --- | --- | --- |
| 进程身份 | Binder caller identity、Android permission、SELinux domain | systemd service user/group、Unix socket mode、LSM/SELinux/AppArmor | DEL-001, DEL-002, DEL-004 |
| 安全策略入口 | Runtime & Governance `/policy/evaluate`，后续接 Native policy adapter | 同一 policy contract，Linux daemon 保持 caller metadata | NV-G-005, NV-F-009 |
| 跨 VM transport | 由目标平台提供 hypervisor IPC、virtio、vendor channel 或 shared memory | 由目标 Linux/Hypervisor stack 提供 virtio/shm/vendor IPC | HV-001, HV-003 |
| Safety Runtime | 通过平台 service/HAL 暴露状态，不在 App 内实现 | 通过 daemon、sysfs、socket 或 vendor lib 暴露状态 | KH-007 |
| fallback | Binder/REST/IPC 返回 degraded/readonly/unavailable | CLI/daemon 返回同一语义错误码 | FW-S-005, DEL-004 |

## 验证边界

当前可验证内容：

```bash
bash tools/check_central_brain_virtualization_docs.sh
bash tools/smoke_central_brain_semantic_gateway.sh
bash tools/smoke_central_brain_linux_ipc.sh
```

不可验证内容：

- Hypervisor 启动、VM 生命周期、vDevice 创建。
- ASIL/QM 隔离强度、安全认证或故障注入。
- 跨 VM 共享内存驱动、DMA-BUF、IOMMU、doorbell 和中断。
- 真实 Safety Runtime 与 Driver/HAL 的联动。

以上内容必须在目标 SoC、目标 Hypervisor 和供应商 SDK 明确后作为集成验证，不在当前 Android/Linux mock 原型内开发。

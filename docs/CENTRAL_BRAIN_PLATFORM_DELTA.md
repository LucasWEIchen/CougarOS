# Android/Linux 平台差异说明

版本：0.1
日期：2026-07-04

## 范围

本文件覆盖 DEL-001、DEL-002、DEL-003、DEL-004，以及跨 SoC 组件
XSC-001、XSC-002、XSC-003、XSC-005、XSC-006 的 Android 主开发路径与 Linux 同步交付路径差异。

虚拟化层不开发；相关内容只作为 HV-001..003 的部署假设，详见
`docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md`。驱动层不默认新增开发；
Driver/HAL 缺口仍按 DEL-005、KH-003、KH-006 在
`docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md` 维护。

## 差异矩阵

| 维度 | Android 主开发路径 | Linux 同步交付路径 | 当前交付状态 | Req ID |
| --- | --- | --- | --- | --- |
| 应用入口 | Android Console APK 和后续 AI SDK client；Binder contract 暴露 `planAgentTaskJson`、`executeAgentTaskJson`、Skill/Memory methods | CLI/client，无 UI 最低样例；CLI/IPC 暴露 `agent-plan`、`agent-execute`、`skill-invoke`、`memory-query` | Console + CLI 初版；AI SDK/Agent plan + execute/Skill/Memory contract mock | DEL-001, DEL-002, XSC-001, APP-004, FW-U-006 |
| Uni Info Bus | App/client 调用 `/uib/*`，Binder sample 暴露 `getContextJson`、`getStateJson` | CLI 和 Unix socket IPC operation 映射 `uib.context.get`、`uib.state.get` | active prototype | XSC-002, FW-U-001, FW-U-002 |
| SOA 服务入口 | Binder sample 暴露服务目录和 invoke 方法，当前代理语义网关 | CLI、Unix socket IPC 和 systemd gateway sample | active prototype + Linux unit sample | XSC-003, FW-S-004, FW-S-005 |
| Runtime & Governance | 通过 `/policy/evaluate`、`/governance/precheck`、`/governance/runtime`、`/audit/recent` 验证；Binder contract 暴露 `precheckGovernanceJson` | 同一 contract；Linux CLI/IPC 提供 `governance-precheck`/`governance.precheck`；Linux IPC daemon 保持 policy/governance operation，并对 `soa.service.invoke` 执行本地 precheck | active prototype；`/governance/precheck` 默认不消费 QoS 且不 dispatch 服务 | XSC-005, NV-G-001..007 |
| Protocol Binding | Binder/AIDL service stub sample；REST 只是 prototype binding | Unix socket IPC active sample；gRPC contract skeleton；REST prototype binding | Android stub + Linux active sample | XSC-006, NV-P-002, NV-P-003, NV-P-005 |
| 服务部署 | Debug APK 内置 Binder sample；量产目标为 AAOS system/privileged service 约束 | `central-brain-backend.service` + `central-brain-linux-ipc.service` 样例 | Android system service integration note + Linux systemd sample | DEL-001, DEL-003, DEL-004 |
| 权限模型 | Android app permission、Binder caller identity、signature permission、Runtime & Governance policy | Linux service user/group、Unix socket mode、Runtime & Governance policy | Android 权限/SELinux 假设文档化，未接入真实系统权限 | FW-U-007, FW-S-005, NV-G-005, DEL-004 |
| 日志与审计 | Android logcat + `/audit/recent`；可通过服务配置指定 `CENTRAL_BRAIN_AUDIT_LOG` | journald + `/audit/recent`；可指定 gateway JSONL audit log 路径，IPC precheck 可指定 `CENTRAL_BRAIN_IPC_AUDIT_LOG` | JSONL 持久化样例已可验证，量产仍需轮转/导出/权限加固 | XSC-005, NV-G-007, DEL-002, DEL-004 |
| Driver/HAL | Android HAL/AIDL/NDK/vendor SDK bridge，当前不新增驱动 | Linux device node/ioctl/sysfs/vendor lib，当前不新增驱动 | 仅接口矩阵 | DEL-005, KH-003, KH-006 |
| 虚拟化 | 只记录 Hypervisor/ASIL/QM 接口约束 | 只记录跨 VM 通信假设和 fallback | 非开发范围 | HV-001..003 |

## 虚拟化与 Safety 约束

A7 交付文档 `docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md` 固定以下边界：

- HV-001：Hypervisor 仅作为 domain、transport、共享内存和启动依赖假设，不开发虚拟化功能。
- HV-002：ASIL/QM 隔离只做服务到安全域映射和降级策略说明，不实现隔离机制。
- HV-003：跨 VM 通信必须保留 Uni Info Bus/SOA 语义，不能让 App 直连 Hypervisor channel。
- FW-S-005、NV-G-005、NV-F-009：Safety State、Policy 和 Security/Policy Adapter 是 Android/Linux 共同约束。

## Linux systemd 样例

Linux 部署样例位于 `central-brain/deploy/linux/`：

- `central-brain.env.example`：端口、base URL、Unix socket 路径、gateway audit log 和 IPC precheck audit log。
- `systemd/central-brain-backend.service`：语义网关 backend。
- `systemd/central-brain-linux-ipc.service`：Linux IPC Protocol Binding daemon。

验证命令：

```bash
bash tools/check_central_brain_delivery_docs.sh
bash tools/check_central_brain_virtualization_docs.sh
bash tools/smoke_central_brain_semantic_gateway.sh
bash tools/smoke_central_brain_linux_ipc.sh
```

## Android system/privileged service 集成约束

Android 量产集成说明位于
`docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md`，覆盖 DEL-001、
DEL-003、DEL-004、XSC-001、APP-004、XSC-002、XSC-003、XSC-005、
XSC-006、NV-P-002、NV-P-005、FW-U-007、FW-S-005、NV-G-005。

该文档将当前 debug APK 内置 Binder sample 与目标 AAOS system/privileged
service 区分开：当前 sample 只验证 App -> Binder -> AI SDK/Uni Info Bus/SOA 路径；
目标集成需要平台方提供签名、priv-app 白名单、SELinux domain、service context
和上游 native gateway/system service 形态。Binder caller identity 必须进入
Runtime & Governance Policy 输入，不能替代 Policy 检查。

验证命令：

```bash
bash tools/check_central_brain_android_system_service_docs.sh
```

## 当前限制

- Android Binder sample 和 Linux IPC daemon 允许路径仍代理到同进程 REST prototype gateway；`/governance/precheck` 只是显式 contract，不是共享量产治理 daemon；Linux IPC 已对 SOA 调用增加本地 Runtime & Governance precheck，偏差记录见 DEV-001、DEV-013。
- Android system/privileged service 当前只有集成约束文档，没有 framework patch、priv-app 签名配置或 sepolicy，风险记录见 ISSUE-013。
- Linux systemd unit 是部署样例，不等同量产包管理或安全加固基线。
- 当前审计可选 JSONL 持久化并恢复最近 50 条；仍不是量产审计后端，偏差记录见 DEV-006。
- 当前没有真实 Driver/HAL/NPU/Vehicle bus 接入，偏差记录见 DEV-004、DEV-005、DEV-014。

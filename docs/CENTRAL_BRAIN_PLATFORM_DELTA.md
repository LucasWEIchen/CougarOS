# Android/Linux 平台差异说明

版本：0.1
日期：2026-07-04

## 范围

本文件覆盖 DEL-001、DEL-002、DEL-003、DEL-004，以及跨 SoC 组件
XSC-002、XSC-003、XSC-005、XSC-006 的 Android 主开发路径与 Linux 同步交付路径差异。

虚拟化层不开发；相关内容只作为 HV-001..003 的部署假设，详见
`docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md`。驱动层不默认新增开发；
Driver/HAL 缺口仍按 DEL-005、KH-003、KH-006 在
`docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md` 维护。

## 差异矩阵

| 维度 | Android 主开发路径 | Linux 同步交付路径 | 当前交付状态 | Req ID |
| --- | --- | --- | --- | --- |
| 应用入口 | Android Console APK 和后续 AI SDK client | CLI/client，无 UI 最低样例 | Console + CLI 初版 | DEL-001, DEL-002, XSC-001 |
| Uni Info Bus | App/client 调用 `/uib/*`，Binder sample 暴露 `getContextJson`、`getStateJson` | CLI 和 Unix socket IPC operation 映射 `uib.context.get`、`uib.state.get` | active prototype | XSC-002, FW-U-001, FW-U-002 |
| SOA 服务入口 | Binder sample 暴露服务目录和 invoke 方法，当前代理语义网关 | CLI、Unix socket IPC 和 systemd gateway sample | active prototype + Linux unit sample | XSC-003, FW-S-004, FW-S-005 |
| Runtime & Governance | 通过 `/policy/evaluate`、`/governance/runtime`、`/audit/recent` 验证 | 同一 contract；Linux IPC daemon 保持 policy/governance operation | active prototype | XSC-005, NV-G-001..007 |
| Protocol Binding | Binder/AIDL service stub sample；REST 只是 prototype binding | Unix socket IPC active sample；gRPC contract skeleton；REST prototype binding | Android stub + Linux active sample | XSC-006, NV-P-002, NV-P-003, NV-P-005 |
| 服务部署 | APK/Android service；后续 AAOS/SystemService 集成 | `central-brain-backend.service` + `central-brain-linux-ipc.service` 样例 | Linux systemd sample 初版 | DEL-003, DEL-004 |
| 权限模型 | Android app permission、Binder caller identity、Runtime & Governance policy | Linux service user/group、Unix socket mode、Runtime & Governance policy | 文档化，未接入真实系统权限 | FW-U-007, NV-G-005, DEL-004 |
| 日志与审计 | Android logcat + `/audit/recent` | journald + `/audit/recent` | 审计内存态，日志依赖平台 | NV-G-007, DEL-004 |
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

- `central-brain.env.example`：端口、base URL 和 Unix socket 路径。
- `systemd/central-brain-backend.service`：语义网关 backend。
- `systemd/central-brain-linux-ipc.service`：Linux IPC Protocol Binding daemon。

验证命令：

```bash
bash tools/check_central_brain_delivery_docs.sh
bash tools/check_central_brain_virtualization_docs.sh
bash tools/smoke_central_brain_semantic_gateway.sh
bash tools/smoke_central_brain_linux_ipc.sh
```

## 当前限制

- Android Binder sample 和 Linux IPC daemon 仍代理到同进程 REST prototype gateway，偏差记录见 DEV-001、DEV-013。
- Linux systemd unit 是部署样例，不等同量产包管理或安全加固基线。
- 当前审计为内存态，重启后丢失，偏差记录见 DEV-006。
- 当前没有真实 Driver/HAL/NPU/Vehicle bus 接入，偏差记录见 DEV-004、DEV-005、DEV-014。

# Android System Service Integration Notes

版本：0.1
日期：2026-07-07

## 范围

本文档推进 Android 主开发路径从 debug APK 内置 Binder sample 走向 AAOS
system/privileged service 集成说明，覆盖 DEL-001、DEL-003、DEL-004、
XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、HW-002、NV-P-002、NV-P-005、FW-U-007、
FW-S-005、NV-G-005、KH-003、KH-006、KH-007、DEL-005。

本轮只记录集成约束、部署假设和验证检查项，不新增 Android framework patch、
system server 代码、priv-app 签名配置、SELinux policy、Driver/HAL、Safety Runtime
或虚拟化层开发。

## 当前样例边界

当前 Android Console debug APK 已经绑定
`CentralBrainGatewayBinderService`，并通过 `CentralBrainGatewayClient`
调用 Uni Info Bus State、AI SDK/Agent task plan、Agent execute、Skill invoke
与 Memory query contract mock，并提供 Event subscription lifecycle command、transport readiness、owner decision matrix、activation checklist、callback/watch shape、cursor/replay storage、backpressure/QoS evidence、readiness rollup、activation evidence contract、activation evidence review status contract 和 retention checklist contract、Runtime & Governance precheck、shared
governance backend target contract、migration readiness、deployment plan、Protocol Binding readiness、Android/Linux delivery readiness、Driver/HAL gap backlog 与 hardware empty-interface registry 只读调试入口。该路径覆盖：

| 组件 | 当前交付 | Req ID |
| --- | --- | --- |
| Uni Info Bus client path | `getStateJson`、`getContextJson`、`listEventTopicsJson`、`publishEventJson`、`getRecentEventsJson`、`getEventSubscriptionsJson`、`requestEventSubscriptionJson`、`cancelEventSubscriptionJson`、`getEventSubscriptionTransportReadinessJson`、`getEventSubscriptionDecisionMatrixJson`、`getEventSubscriptionActivationChecklistJson`、`getEventSubscriptionCallbackWatchShapeJson`、`getEventSubscriptionCursorReplayStorageJson`、`getEventSubscriptionBackpressureQosEvidenceJson`、`getEventSubscriptionReadinessRollupJson`、`submitEventSubscriptionActivationEvidenceJson`、`getEventSubscriptionActivationEvidenceStatusJson`、`getEventSubscriptionActivationEvidenceRetentionChecklistJson` | XSC-002, FW-U-001, FW-U-002, FW-U-003, NV-P-006 |
| AI SDK/Agent task path | `getAiSdkCapabilitiesJson`、`planAgentTaskJson`、`executeAgentTaskJson`、`listSkillsJson`、`invokeSkillJson`、`queryMemoryJson` | XSC-001, APP-004, NV-F-001, FW-U-006, FW-U-007 |
| SOA service entry | `listServicesJson`、`getServiceContractsJson`、`invokeServiceJson` | XSC-003, FW-S-004, FW-S-005, NV-G-003 |
| Runtime & Governance | `evaluatePolicyJson`、`precheckGovernanceJson`、`getGovernanceBackendContractJson`、`getGovernanceMigrationCheckJson`、`getGovernanceDeploymentPlanJson`、`getRuntimeGovernanceJson`、`getRecentAuditJson` | XSC-005, XSC-006, FW-U-007, NV-G-001, NV-G-002, NV-G-004, NV-G-005, NV-G-006, NV-G-007 |
| Native/Driver visibility | `getNativeAdaptersDetailJson`、`getDriverHalGapsJson`、`getHardwareInterfacesJson`、`getHardwareInterfaceActivationChecklistJson` | XSC-004, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| Protocol Binding | AIDL + Binder service/client sample + `getBindingReadinessJson` + `getDeliveryReadinessJson` | XSC-006, NV-P-001, NV-P-002, NV-P-003, NV-P-004, NV-P-005, NV-P-006, DEL-001, DEL-002, DEL-003, DEL-004, DEL-005 |

当前 service 仍是普通 APK 内的非导出 service，并继续代理 REST prototype gateway。
它不是量产 Android system service，也不是 Driver/HAL bridge。
`executeAgentTaskJson`、`invokeSkillJson` 和 `queryMemoryJson` 当前只验证
Policy/Safety State、audit 和 contract dispatch 边界，不运行真实 Agent runtime、
Skill sandbox、Memory store、Model Runtime Adapter、Driver/HAL 或虚拟化层。
`precheckGovernanceJson` 只执行 discovery、Policy、Lifecycle 与 QoS 决策检查；
`getGovernanceBackendContractJson` 只返回 Android Binder、Linux IPC 和 Linux gRPC/RPC
未来共用的 shared Runtime & Governance backend target contract；
`getGovernanceMigrationCheckJson` 只返回生产共享治理后端替换 readiness，固定 SOA precheck、Policy/QoS 不复制、runtime/audit 只读诊断和非目标边界，并明确当前不是量产治理后端；
`getGovernanceDeploymentPlanJson` 只返回 Android system/privileged service、Linux daemon 和 true gRPC/RPC 的部署计划 contract，不提交 framework、SELinux 或 native daemon patch；
`getBindingReadinessJson` 只返回 Android Binder、Linux IPC、Linux gRPC/RPC、REST、MQTT、SOME/IP、DDS 的 readiness、阻塞项、验证命令和非目标边界，不实现量产 transport；
`getDeliveryReadinessJson` 只返回 Android debug Console/Binder、Android system service note、Linux CLI/IPC/gRPC、Linux systemd/package profile、Driver/HAL gap backlog、hardware empty-interface registry 和虚拟化约束的交付 readiness、验证 bundle、阻塞项和非目标边界，不实现 Android system service、真实 gRPC runtime、量产包管理或 Driver/HAL；
`getServiceContractsJson` 只返回 SOA service contract、版本、Policy/Safety State、QoS、Lifecycle 和 no-dispatch 边界，不调用 service、Driver/HAL、车辆总线或虚拟化层；
`getEventSubscriptionsJson`、`requestEventSubscriptionJson`、`cancelEventSubscriptionJson`、`getEventSubscriptionTransportReadinessJson`、`getEventSubscriptionDecisionMatrixJson`、`getEventSubscriptionActivationChecklistJson`、`getEventSubscriptionCallbackWatchShapeJson`、`getEventSubscriptionCursorReplayStorageJson`、`getEventSubscriptionBackpressureQosEvidenceJson`、`getEventSubscriptionReadinessRollupJson`、`submitEventSubscriptionActivationEvidenceJson`、`getEventSubscriptionActivationEvidenceStatusJson` 和 `getEventSubscriptionActivationEvidenceRetentionChecklistJson` 只返回 Event subscription lifecycle、cursor/replay storage、backpressure、governance、request/cancel contract-only 命令、callback/watch transport readiness、broker/cursor/backpressure owner decision matrix、activation evidence gates、activation evidence intake、activation evidence review status、retention checklist、callback/watch API shape、cursor schema、ack shape、replay window、retention/cleanup、overflow schema、per-caller throttling、replay rate、ack timeout、Runtime & Governance QoS evidence、readiness blockers 和 Android/Linux binding parity；这些契约不创建 cursor row，不建立 replay index，不持久化 subscription/evidence，不读取 evidence store，不创建 review queue，不更新 review queue，不创建 delete/export workflow，不选择 transport，不分配量产 owner，不激活事件 QoS，不关闭 readiness gate，不注册 callback，不启动 watch stream、broker、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL 或虚拟化层；
`getDriverHalGapsJson` 只读返回 gap backlog，不触发 HAL、device node、vendor SDK、
Safety Runtime 或 Driver/HAL 开发。
`getHardwareInterfacesJson` 只读返回 hardware empty-interface registry，不触发硬件访问、HAL、device node、vendor SDK、shared memory、Safety Runtime、车辆总线或虚拟化开发。
`getHardwareInterfaceActivationChecklistJson` 只读返回 hardware activation checklist，通过 `/hardware/interfaces/activation-checklist` 暴露 owner、Android ABI、Linux ABI、Driver/HAL gap review、Safety/Policy、target smoke evidence、rollback/fault semantics 和 no-hardware-access 门禁；该路径固定 `activation_allowed=false`，不触发硬件访问、HAL、device node、vendor SDK、shared memory、Safety Runtime、车辆总线、Driver/HAL 或虚拟化开发。

## 目标 Android 集成形态

目标 AAOS/Android 交付应按目标镜像能力选择以下一种形态：

| 形态 | 适用条件 | 集成边界 | Req ID |
| --- | --- | --- | --- |
| Privileged app service | 允许把 Central Brain gateway 作为 `priv-app` 随镜像交付 | 复用 Stable AIDL；通过 priv-app permission、签名和 SELinux domain 限制调用 | DEL-001, DEL-003, DEL-004, NV-P-002 |
| Framework system service | 需要纳入 framework service manager 或 vendor service manager | AIDL contract 保持语义层入口；service implementation 进入系统镜像 | DEL-001, DEL-003, XSC-006, NV-P-002 |
| Vendor native gateway bridge | 上游 gateway 在 native/vendor daemon 内 | Java/Kotlin Binder 层只做 client facade，native daemon 承载 runtime binding | XSC-005, XSC-006, NV-P-002 |

无论采用哪种形态，App 层只能看到 Uni Info Bus/SOA 语义接口，不能直接访问
REST prototype gateway、VHAL、ECU、NPU vendor SDK、device node、Hypervisor channel
或跨 VM 共享内存。

## Manifest 与权限约束

量产 manifest 必须满足：

- service 不应对普通第三方应用无限制导出；若需要跨进程绑定，必须使用签名级权限或平台白名单。
- Binder action 必须稳定，当前 action 为
  `com.centralbrain.binding.action.BIND_CENTRAL_BRAIN_GATEWAY`。
- 调用方身份必须进入 Runtime & Governance Policy 输入，不能把 Binder UID 当成唯一授权结果。
- Android cleartext REST 只允许 debug/prototype；量产 service 上游应替换为本地 gateway、native daemon 或受控平台服务。

建议权限草案：

```xml
<permission
    android:name="com.centralbrain.permission.BIND_GATEWAY"
    android:protectionLevel="signature" />

<service
    android:name="com.centralbrain.binding.CentralBrainGatewayBinderService"
    android:exported="true"
    android:permission="com.centralbrain.permission.BIND_GATEWAY">
    <intent-filter>
        <action android:name="com.centralbrain.binding.action.BIND_CENTRAL_BRAIN_GATEWAY" />
    </intent-filter>
</service>
```

debug APK 当前仍使用 `android:exported="false"`，因此只验证 App 内 Binder 路径。

## Binder Identity To Policy Mapping

目标 service 在每次调用 Uni Info Bus/SOA 前应把 Binder 调用身份加入 policy context：

| Binder 输入 | Policy 字段 | Req ID |
| --- | --- | --- |
| calling UID/PID | `caller.android_uid`、`caller.android_pid` | FW-U-007, NV-G-005 |
| package name/signature digest | `caller.app_id`、`caller.signature_digest` | FW-U-007, NV-G-005 |
| user/profile id | `permission_context.android_user` | DEL-004, NV-G-005 |
| foreground/driving restrictions | `permission_context.vehicle_state`、`permission_context.safety_state` | FW-S-005, NV-G-005 |
| AIDL method name | `resource`、`action` | XSC-002, XSC-003, XSC-005 |

Policy 仍由 Runtime & Governance 执行。Binder 身份是输入，不是绕过
`/policy/evaluate`、`/governance/precheck` 或 `/soa/invoke` precheck 的理由。

## SELinux 与部署假设

目标 AAOS 镜像需要由平台集成方补齐：

- service domain、client domain 和 Binder call allow 规则。
- priv-app permission whitelist 或 platform signing 流程。
- gateway/native daemon socket 或 binder service 的 service context。
- audit log 路径、logcat tag、持久化权限和轮转策略。
- 与 Safety Runtime、ASIL/QM domain 的只读/降级/fault fallback 映射。

本仓库只保留接口约束和 sample artifact，不提供目标镜像 sepolicy patch。

## Android 验证检查项

| 检查项 | 命令/证据 | Req ID |
| --- | --- | --- |
| AIDL contract 可生成 Java | `bash tools/check_central_brain_binding_artifacts.sh` | XSC-006, NV-P-002 |
| Console APK 可编译 Binder client/service | `bash tools/build_central_brain_console.sh` | DEL-001 |
| service manifest 存在 Binder action | `tools/check_central_brain_android_system_service_docs.sh` | DEL-003, DEL-004 |
| Console 可触发 Governance precheck、Driver/HAL gaps、Hardware IF 和 HW Gate | `bash tools/check_central_brain_binding_artifacts.sh` | XSC-004, XSC-005, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| 权限/身份/Policy 边界已文档化 | `tools/check_central_brain_android_system_service_docs.sh` | FW-U-007, NV-G-005 |
| 未新增 Driver/HAL/虚拟化开发 | driver support matrix + deviation table | KH-003, KH-006, HV-001..003 |
| Driver/HAL gap backlog 可见 | `getDriverHalGapsJson` + `/native/driver-gaps` | KH-003, KH-006, DEL-005 |
| Hardware empty-interface registry 可见 | `getHardwareInterfacesJson` + `/hardware/interfaces` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| Hardware activation checklist 可见 | `getHardwareInterfaceActivationChecklistJson` + `/hardware/interfaces/activation-checklist` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |

## 开放风险

目标 AAOS 镜像的签名、priv-app 白名单、SELinux domain、service manager 注册方式
尚未确定。该风险登记为 ISSUE-013；在目标镜像明确前，本项目不提交 framework
patch 或 sepolicy patch。

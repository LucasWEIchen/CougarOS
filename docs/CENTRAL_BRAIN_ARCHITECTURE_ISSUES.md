# 中央大脑架构疑点与风险登记表

版本：0.1
日期：2026-07-07

## 使用规则

本文件记录架构图中需要进一步确认、工程上存在歧义、职责边界可能重叠或当前条件无法判断的点。它不是否定图中需求，而是把需要澄清的内容显式管理。

状态定义：
- Open：待确认。
- Proposed：已有建议，待用户/架构方确认。
- Closed：已确认并反映到需求或设计。

## 疑点列表

| ID | 图中位置 | 疑点/风险 | 影响 | 当前建议 | 状态 |
| --- | --- | --- | --- | --- | --- |
| ISSUE-001 | 左侧 UNIOS 大括号 | 图中 UNIOS 似乎覆盖 Framework、Native、Kernel/HAL、虚拟化和硬件，但是否包含应用层不明确 | 影响系统边界、SDK 发布形态和责任划分 | 暂定应用层不属于 UNIOS 内核，只通过 AI SDK/Uni Info Bus 接入 | Open |
| ISSUE-002 | AI SDK 位于应用层且为蓝色 | AI SDK 是 App 内 SDK、系统 SDK，还是平台服务的 client library 不明确 | 影响 Android 端架构和接口位置 | 暂定为平台提供的 client facade/SDK，应用层调用 `planAgentTask` 类任务接口，但实现归平台并经 Uni Info Bus、AIOS Kernel、SOA 与 Runtime & Governance | Proposed |
| ISSUE-003 | Cluster/TBOX 与 Connected Funcware | 应用层和 Native 层都出现 TBOX/OTA/Diag 类能力，职责边界可能重叠 | 影响服务拆分 | 暂定应用层为业务 UI/流程，Native 为设备/协议/状态机 adapter | Proposed |
| ISSUE-004 | 智驾应用/服务 与 ADAS Funcware | NOA/TJA/APA、感知/地图/位置、Perception/Fusion/Scene 的调用边界未细化 | 影响安全域和实时数据通道 | 暂定 App 只读状态/发起请求，ADAS Funcware 保持安全域内闭环 | Proposed |
| ISSUE-005 | SOA 服务入口 与 Uni Info Bus Runtime & Governance | 两层都涉及服务管理，控制面/数据面边界未明确 | 影响 Gateway 实现 | 暂定 SOA 服务入口负责语义服务门面，Runtime & Governance 负责注册、发现、策略、生命周期 | Proposed |
| ISSUE-006 | REST/MQTT 云接口 | 图中 Protocol Binding 有云/工具 API，但隐私路由没有单独画出 | 影响数据外发和合规 | 暂定通过 Security/Policy Adapter + Policy 实现隐私路由 | Proposed |
| ISSUE-007 | Kernel & HAL 层 Libs 出现两次 | 图中右侧有两个 Libs，含义可能分别对应不同域或不同库集合 | 影响底层依赖清单 | 需要用户或原架构方确认 | Open |
| ISSUE-008 | 外置 PCIe NPU | 图中硬件层写 UniSOC Automotive-solution，用户需求增加外置 PCIe NPU 卡，图中未明确外置卡边界 | 影响 Driver/HAL 接口，不影响虚拟化开发范围 | 暂定外置 PCIe NPU 映射到 Drivers:NPU、HAL、Model Runtime Adapter 和硬件扩展；只在当前环境缺口处新增驱动开发 | Proposed |
| ISSUE-009 | 色块所有权 | 生态合作/客户开发/展锐负责/芯片原有是组织责任还是代码所有权不明确 | 影响开发任务分配 | 暂定同时作为责任和实现边界，后续任务都保留 ownership 字段 | Proposed |
| ISSUE-010 | Safety State、Safety Runtime、ASIL/QM 隔离 | 三处安全概念分布在 Framework、Kernel/HAL、虚拟化层，量产映射仍依赖目标 SoC/Hypervisor | 影响安全闭环 | 已在 `CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md` 建立 Safety State -> Safety Runtime -> ASIL/QM domain 初版映射；虚拟化只记录接口约束不开发 | Proposed |
| ISSUE-011 | 黄色小太阳组件 | 图中 AI SDK、Uni Info Bus、SOA 服务入口、AIOS Kernel、Runtime & Governance、Protocol Binding 等带黄色小太阳，用户确认会在多个 SoC 出现 | 影响跨 SoC 可移植设计和平台交付 | 已新增 XSC-001..006，后续按 Android 主线 + Linux 同步交付处理 | Closed |
| ISSUE-012 | 交付对象 | 交付对象明确为 Android/Linux 座舱域软件工程师 | 影响文档、示例、验证脚本和平台差异说明 | 已新增 DEL-001..005 和交付目标文档 | Closed |
| ISSUE-013 | Android system/privileged service 集成 | 目标 AAOS 镜像的签名、priv-app 白名单、SELinux domain、service manager 注册方式和上游 native gateway 形态尚未确定 | 影响 Binder service 从 debug APK sample 迁移到量产 system/privileged service 的边界、权限和部署方式 | 已新增 `CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md` 固定集成约束；目标镜像明确前不提交 framework patch、priv-app 签名配置或 sepolicy patch | Proposed |
| ISSUE-014 | Shared Runtime & Governance backend deployment | 量产共享治理后端的 Android system service owner、Linux 目标发行版/package format、gRPC runtime/credential 来源和 audit export backend 尚未确定 | 影响 `/governance/deployment-plan` 从 contract 进入真实多进程治理后端的部署形态、身份输入和验证策略 | 暂用 `GET /governance/deployment-plan` 固定 Android system/privileged service、Linux daemon、true gRPC/RPC 三类候选形态；目标平台决策明确前不实现生产治理后端、framework/SELinux patch 或真实 gRPC runtime | Proposed |
| ISSUE-015 | Uni Info Bus 其他/扩展语义 | FW-U-008 的动态扩展生命周期、schema 发布/撤销、权限审核、插件沙箱和跨 SoC 版本兼容规则尚未明确 | 影响 `/uib/extensions` 从只读 contract 进入真实 extension runtime 或 schema registry 的边界 | 暂用 `GET /uib/extensions` 固定扩展对象必须保留 Uni Info Bus envelope、Runtime & Governance、Protocol Binding 和 no-dispatch 边界；目标扩展生命周期明确前不实现动态 loader、插件沙箱或 schema registry 服务 | Proposed |
| ISSUE-016 | Kernel/HAL/硬件依赖空接口 | `/hardware/interfaces` 已列出 NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、Shared memory/Safety Runtime 五类空接口，但最终 owner、ABI、权限模型和替换触发条件仍依赖目标 Android/Linux 平台确认 | 影响后续从 empty-interface registry 替换为真实 Driver/HAL/vendor SDK/native adapter 的工作量评估和验证策略 | 暂用 `GET /hardware/interfaces` 固定空接口、reserved methods、Android 主路径、Linux 同步路径和 no-hardware-access 标记；目标硬件、vendor SDK、信号目录或 OS 能力缺口明确前不实现驱动或 HAL | Proposed |
| ISSUE-017 | Vehicle Signal read bridge validation | `/vehicle/signals/validation` 已列出 schema source metadata、Vehicle Signal Adapter owner、platform ABI owner、Android/Linux parity 和 DRV-GAP-002 evidence，但这些证据的最终 owner 与验收格式依赖目标车型、Android/Linux 平台和供应商接口确认 | 影响 `/vehicle/signals/activation` 何时可以从 criteria-only 进入真实 read bridge；如果 owner/evidence 不明确，容易把 mock catalog 误当成 VHAL/SocketCAN/DBC 接入完成 | 暂用 `GET /vehicle/signals/validation` 固定 `VS-VAL-001..006` 门禁和 `read_bridge_activated=false`；目标 schema source、adapter owner、ABI owner、parity evidence 和 DRV-GAP-002 smoke 明确前不实现真实读桥或 Driver/HAL | Proposed |
| ISSUE-018 | Uni Info Bus Event subscription | `/uib/events/subscriptions` 已列出订阅 lifecycle、cursor/replay、filter、backpressure、governance 和 Android/Linux binding parity，但订阅 broker owner、回调/监听机制、持久化、取消语义、SSE/WebSocket/DDS 选择和高频 QoS 仍未定 | 影响 FW-U-003/NV-P-006 从 contract-only 进入真实事件推送；如果 transport 与背压策略未确认，容易把 recent-log mock 误当成量产订阅能力 | 暂用 `GET /uib/events/subscriptions` 固定 `EV-SUB-001..005` 门禁和 `broker_active=false`；目标 transport、broker owner、cursor storage、QoS/backpressure 和 Android/Linux callback/watch 形态明确前不实现真实订阅 broker 或 DDS 数据面 | Proposed |

## 新增疑点模板

| ID | 图中位置 | 疑点/风险 | 影响 | 当前建议 | 状态 |
| --- | --- | --- | --- | --- | --- |
| ISSUE-XXX |  |  |  |  | Open |

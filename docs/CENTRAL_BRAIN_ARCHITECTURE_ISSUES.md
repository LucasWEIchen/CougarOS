# 中央大脑架构疑点与风险登记表

版本：0.1
日期：2026-07-04

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
| ISSUE-002 | AI SDK 位于应用层且为蓝色 | AI SDK 是 App 内 SDK、系统 SDK，还是平台服务的 client library 不明确 | 影响 Android 端架构和接口位置 | 暂定为平台提供的 client SDK，应用层调用但实现归平台 | Proposed |
| ISSUE-003 | Cluster/TBOX 与 Connected Funcware | 应用层和 Native 层都出现 TBOX/OTA/Diag 类能力，职责边界可能重叠 | 影响服务拆分 | 暂定应用层为业务 UI/流程，Native 为设备/协议/状态机 adapter | Proposed |
| ISSUE-004 | 智驾应用/服务 与 ADAS Funcware | NOA/TJA/APA、感知/地图/位置、Perception/Fusion/Scene 的调用边界未细化 | 影响安全域和实时数据通道 | 暂定 App 只读状态/发起请求，ADAS Funcware 保持安全域内闭环 | Proposed |
| ISSUE-005 | SOA 服务入口 与 Uni Info Bus Runtime & Governance | 两层都涉及服务管理，控制面/数据面边界未明确 | 影响 Gateway 实现 | 暂定 SOA 服务入口负责语义服务门面，Runtime & Governance 负责注册、发现、策略、生命周期 | Proposed |
| ISSUE-006 | REST/MQTT 云接口 | 图中 Protocol Binding 有云/工具 API，但隐私路由没有单独画出 | 影响数据外发和合规 | 暂定通过 Security/Policy Adapter + Policy 实现隐私路由 | Proposed |
| ISSUE-007 | Kernel & HAL 层 Libs 出现两次 | 图中右侧有两个 Libs，含义可能分别对应不同域或不同库集合 | 影响底层依赖清单 | 需要用户或原架构方确认 | Open |
| ISSUE-008 | 外置 PCIe NPU | 图中硬件层写 UniSOC Automotive-solution，用户需求增加外置 PCIe NPU 卡，图中未明确外置卡边界 | 影响 Driver/HAL 和虚拟化 | 暂定外置 PCIe NPU 映射到 Drivers:NPU、HAL、Model Runtime Adapter 和硬件扩展 | Proposed |
| ISSUE-009 | 色块所有权 | 生态合作/客户开发/展锐负责/芯片原有是组织责任还是代码所有权不明确 | 影响开发任务分配 | 暂定同时作为责任和实现边界，后续任务都保留 ownership 字段 | Proposed |
| ISSUE-010 | Safety State、Safety Runtime、ASIL/QM 隔离 | 三处安全概念分布在 Framework、Kernel/HAL、虚拟化层，映射关系未定义 | 影响安全闭环 | 需要建立 Safety State -> Safety Runtime -> ASIL/QM domain 映射表 | Open |

## 新增疑点模板

| ID | 图中位置 | 疑点/风险 | 影响 | 当前建议 | 状态 |
| --- | --- | --- | --- | --- | --- |
| ISSUE-XXX |  |  |  |  | Open |

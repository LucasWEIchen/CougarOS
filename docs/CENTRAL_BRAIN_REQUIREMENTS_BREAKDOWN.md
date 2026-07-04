# 车载中央大脑需求拆解

版本：0.1
日期：2026-07-04
角色视角：项目经理 / 产品经理 / 架构师

## 产品参考基线

本项目在产品概念上参考地平线 KaKaClaw 咖咖虾公开信息，但不做复制式实现。该参考只用于补充产品体验，不改变 `docs/assets/central_brain_architecture_source.png` 的架构需求基线。所有 Agent、Skill、Memory、Privacy 能力必须映射回图中的 AI SDK、AIOS Kernel、Tool、Permission、Security/Policy Adapter、Runtime & Governance 等模块。

- 整车智能体 OS：车载 OS 不只是固定功能集合，而是能理解用户意图、主动编排任务、调用硬件资源的 Agentic OS。
- 任务即服务：用户输入自然语言或场景触发条件后，系统自动拆解任务、选择服务、执行跨域流程。
- Soul / Skill / Memory：人格、技能、记忆是产品层核心体验，而不是单纯语音助手。
- 舱驾融合：数字 Agent、物理 Agent、端侧模型、云侧模型协同，但驾驶安全域必须保持隔离。
- Skill 沙箱：技能生态必须在安全沙箱内运行，默认拦截未授权工具调用。
- Privacy Router：所有云端、第三方 API、外发数据都要经过隐私路由和用户授权。

公开资料来源：

- 地平线官方新闻：https://www.horizon.auto/news/press/445
- 新浪财经转引界面新闻：https://finance.sina.com.cn/jjxw/2026-04-29/doc-inhwczny7905228.shtml
- 腾讯新闻报道：https://news.qq.com/rain/a/20260426A05LV100

## 一句话目标

构建一个面向整车智能体的软件底座：Android 前端负责体验，中间层负责语义、任务、权限和治理，外置 PCIe NPU 负责端侧 AI 算力，车辆/智驾/座舱/诊断/云能力通过统一接口被安全编排。

## 需求域拆解

| 需求域 | 产品目标 | 第一阶段交付 | 后续交付 |
| --- | --- | --- | --- |
| 用户交互 | 用户能通过 App/Agent 看到车况、服务、AI 状态 | Android Console | 多模态 HMI、自然语言任务入口 |
| 任务编排 | 用户意图被拆成可执行任务 | mock `/ai/infer` | Planner、Executor、Task Graph |
| 技能生态 | 车辆能力被封装成可授权 Skill | Skill schema 文档 | Skill runtime、Skill store、沙箱 |
| 记忆系统 | 系统记住用户偏好和上下文 | Memory 接口设计 | 本地记忆库、隐私策略、同步策略 |
| 车辆上下文 | 车辆信号统一语义化 | VSS 风格 mock 信号 | VSS/VHAL/ECU 映射 |
| 舱驾协同 | 座舱 Agent 能理解智驾状态，但不能破坏智驾安全 | Safety State 设计 | HSD-like ADAS Adapter |
| AI 算力 | 统一调用外置 PCIe NPU | mock NPU backend | vendor SDK、driver、runtime daemon |
| 服务治理 | 服务注册、发现、Schema、QoS、生命周期 | 文档和 mock contract | SOA Gateway |
| 安全与隐私 | 所有工具调用默认受控 | Policy/Permission 设计 | Sandbox、Privacy Router、Audit |
| 诊断运维 | 能观测调用、服务和模型状态 | health/trace 设计 | DTC、Trace、Metric、OTA |

## Epic 拆解

### E1 Android Console 与用户体验

目标：提供中央大脑的最小可视化入口。

功能：
- E1.1 系统健康页：展示 central brain、service bus、NPU 状态。
- E1.2 车辆信号页：展示 VSS 风格信号。
- E1.3 AI 推理页：输入意图，显示模型、结果、耗时。
- E1.4 服务目录页：展示 Registry/Discovery 中的服务。
- E1.5 Trace 页：展示最近调用、错误和拒绝原因。

验收：
- APK 可构建、安装、启动。
- 每个页面至少有一个真实接口返回。
- 网络失败时有明确错误状态。

### E2 Uni Info Bus 语义接口

目标：把车辆、用户、环境、服务、工具、权限统一成稳定对象模型。

功能：
- E2.1 定义 `Context`、`State`、`Event`、`Action`、`Service`、`Tool`、`Permission`。
- E2.2 定义统一请求/响应 envelope。
- E2.3 定义错误码、Trace ID、调用者身份。
- E2.4 定义事件 topic 和订阅模型。
- E2.5 定义向 AIDL/OpenAPI/protobuf 迁移的规则。

验收：
- JSON contract 能表达当前 mock 接口。
- 每个接口有权限、状态和错误模型。
- 能从 contract 生成 Android client 的输入信息。

### E3 Agentic Task Service

目标：实现类似“任务即服务”的任务编排能力。

功能：
- E3.1 `POST /agent/plan`：自然语言/场景转任务图。
- E3.2 `POST /agent/execute`：执行任务图。
- E3.3 `GET /agent/tasks/{id}`：查询任务状态。
- E3.4 `POST /agent/cancel`：取消任务。
- E3.5 执行前调用 `Policy.evaluate`。

验收：
- 一个“查询车况并给出建议”的任务可以完成 plan -> execute -> trace。
- 涉及车控的任务在 mock 驾驶状态下能被策略拒绝或降级。

### E4 Skill Runtime 与技能生态

目标：把车控、诊断、导航、座舱和 AI 工具封装成可审计技能。

功能：
- E4.1 Skill manifest。
- E4.2 Tool schema。
- E4.3 Skill permission。
- E4.4 Skill sandbox boundary。
- E4.5 Skill version/lifecycle。

验收：
- 至少定义 5 个内置技能：车辆状态查询、空调控制、座椅模式、NPU 推理、诊断 Trace。
- 每个技能声明权限和可用安全状态。

### E5 Memory 与个性化

目标：让系统具备“记性好”的产品体验，同时确保隐私。

功能：
- E5.1 `Memory.write`：写入偏好/事件/用户确认。
- E5.2 `Memory.query`：查询与当前任务相关的记忆。
- E5.3 `Memory.forget`：删除指定记忆。
- E5.4 Privacy Router：判断是否允许外发或云同步。
- E5.5 记忆分级：临时、会话、长期、本地敏感。

验收：
- 用户偏好能影响任务规划。
- 敏感记忆默认不出车。

### E6 Policy、Safety State 与 Privacy Router

目标：所有跨域和高风险调用默认受控。

功能：
- E6.1 `POST /policy/evaluate`：评估调用是否合法。
- E6.2 权限矩阵：caller、tool、resource、vehicle_state。
- E6.3 Safety State：normal、degraded、interlocked、fault_isolated、diagnostic_readonly。
- E6.4 Privacy Router：外发数据分类、脱敏、授权。
- E6.5 Audit：记录允许、拒绝、降级。

验收：
- 未授权工具调用被拒绝。
- 驾驶中高分心或高风险动作被拒绝。
- 拒绝结果包含机器可读原因。

### E7 NPU Runtime Adapter

目标：让上层不感知 mock、CPU、NPU、云端差异。

功能：
- E7.1 `GET /npu/status`。
- E7.2 `POST /ai/infer`。
- E7.3 `POST /models/load`。
- E7.4 `GET /models`。
- E7.5 backend selector：mock、cpu、vendor_sdk、pcie_driver、cloud。

验收：
- mock NPU 可返回健康和推理结果。
- 后续替换真实 vendor SDK 时不改 Android 前端接口。

### E8 Vehicle Signal Adapter

目标：建立 VSS/VHAL/ECU 的映射体系。

功能：
- E8.1 VSS 子集。
- E8.2 VHAL property mapping。
- E8.3 signal subscription。
- E8.4 signal quality。
- E8.5 action mapping。

验收：
- 至少 20 个基础车辆信号有路径、类型、权限和来源定义。

### E9 Observability 与工程运维

目标：持续知道系统有没有工作、为什么失败、哪里慢。

功能：
- E9.1 Health。
- E9.2 Trace。
- E9.3 Metric。
- E9.4 Audit。
- E9.5 OTA/lifecycle 状态。

验收：
- 每个 API 返回 `trace_id`。
- 每个 Action 有审计记录。
- smoke test 可验证关键接口。

## 里程碑细化

| 里程碑 | 周期目标 | 完成标准 |
| --- | --- | --- |
| M0 | 文档、mock 后端、Android Console | 已完成 |
| M1 | 一键验证闭环 | 一个脚本完成后端、模拟器、安装、接口、截图 |
| M2 | 接口契约扩展 | 完成 Agent/Skill/Memory/Policy/NPU/Vehicle contract |
| M3 | SOA Gateway mock | Registry、Discovery、Policy、Trace 独立模块 |
| M4 | Android Console 多页化 | 系统/车辆/AI/服务/Trace 页面 |
| M5 | AIDL 草案 | ICentralBrainService、IAgentService、IPolicyService |
| M6 | VSS/VHAL 映射 | 20+ 信号、订阅、权限 |
| M7 | NPU adapter 抽象 | mock/CPU/vendor SDK 插拔 |
| M8 | 安全沙箱与隐私路由 | Skill 权限、外发策略、审计 |

## 当前优先级

1. 接口契约优先于 UI 扩展。
2. Policy/Safety 优先于真实车控。
3. Mock 可验证优先于真实 NPU。
4. 一键验证优先于大规模功能。

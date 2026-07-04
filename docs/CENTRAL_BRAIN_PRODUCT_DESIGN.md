# 车载中央大脑产品设计

版本：0.1
日期：2026-07-04

## 产品定位

车载中央大脑是一个面向智能座舱、车控、诊断、智驾协同和 AI Agent 的软件生态底座。它把 Android 前端应用、车内中间件、车辆信号、诊断工具、云端服务和外置 PCIe NPU AI 基座统一到可治理的服务体系中。

第一阶段目标不是一次性完成量产系统，而是建立可持续演进的工程底座：设计文档、服务契约、Android 可运行控制台、后端 NPU mock、测试脚本和版本管理流程。

## 目标用户

- 驾驶员/乘员：使用座舱 HMI、语音/Agent、车控、导航、娱乐和个性化服务。
- 车厂/座舱团队：集成 HMI、仪表、TBOX、OTA、诊断、日志、权限和安全策略。
- 智驾团队：通过标准接口接入感知、融合、NOA/TJA/APA 状态和场景服务。
- 售后/标定/诊断人员：使用诊断、Trace、标定、模型/工具状态和远程运维能力。
- 第三方生态开发者：通过受控 SDK、Schema 和工具权限接入车内服务。

## 产品边界

### 第一阶段包含

- 中央大脑产品设计和架构设计初版。
- Android 控制台 App 原型，用于展示系统、车辆信号、服务注册、AI 后端状态。
- 后端 mock NPU 服务，模拟 PCIe NPU 卡、模型服务、推理请求和健康检查。
- 统一服务契约 JSON，约束 Android 前端和 mock 后端。
- 本地构建、安装和测试脚本。

### 第一阶段不包含

- 真实 PCIe NPU 驱动。
- 真实 AOSP/AAOS 系统服务改造。
- 真实 VHAL 或 CarService 修改。
- 真实 SOME/IP、DDS、MQTT broker 集成。
- 真实 ASIL 认证、UNECE 合规认证。

这些内容会进入后续里程碑，当前先把接口边界和验证通路打通。

## 核心场景

1. 座舱统一控制
   - 用户在 Android 前端查看车况、座舱状态和服务状态。
   - 前端通过中间层查询 Context、State、Event、Action、Service、Tool、Permission。

2. AI Agent 调用车辆能力
   - Agent 收到自然语言意图。
   - 中间层将意图拆成工具调用。
   - Permission 和 Safety State 判定是否允许执行。
   - Action 服务调用车控、诊断、导航或座舱能力。

3. 外置 NPU AI 推理
   - App 或 Agent 请求模型推理。
   - Model Runtime Adapter 将请求路由到 AI 后端。
   - NPU runtime 完成模型加载、队列调度、推理和结果返回。
   - Trace/Metric 记录延迟、失败率和模型版本。

4. 车辆信号治理
   - ECU/CAN/Ethernet/传感器数据被 Signal Adapter 归一化。
   - 信号模型优先映射到 COVESA VSS 风格路径。
   - Framework 层发布为 Context、State 和 Event。

5. 诊断和 OTA
   - 诊断工具通过受控服务入口读取 DTC、日志和 Trace。
   - OTA 服务发布版本、策略、回滚和升级状态。
   - Lifecycle 负责服务启动、降级、升级和回滚。

## 功能模块

| 模块 | 产品职责 | 第一阶段状态 |
| --- | --- | --- |
| Android Console | 手机/模拟器前端，查看中央大脑状态 | 已搭建原型 |
| Uni Info Bus API | 语义接口层，承载 Context/State/Event/Action/Service/Tool/Permission | 文档和契约初版 |
| SOA Gateway | 服务入口、鉴权、路由、降级 | mock 设计 |
| Vehicle Signal Adapter | VSS/VHAL/ECU 信号映射 | 文档设计 |
| AI Runtime Adapter | 面向 NPU/GPU/CPU/Cloud 的模型运行时适配 | mock 设计 |
| NPU Backend | 外置 PCIe NPU 服务和模型执行 | Python mock |
| Governance | Registry/Discovery/Schema/QoS/Policy/Lifecycle | 文档设计 |
| Observability | Trace/Logging/Metric/Health | mock 接口 |
| Safety/Security | Safety State、权限、安全域、审计 | 文档设计 |

## MVP 验收标准

- Android App 能构建为 APK。
- App 能安装到本地 Android 模拟器。
- Python mock 后端能在 WSL 启动并返回 `/health`。
- App 能通过 `10.0.2.2:8787` 访问 mock 后端。
- 文档明确长期架构、阶段路线和第一阶段限制。
- Git 中形成第一个可回溯里程碑。

## 产品原则

- 先契约后实现：接口和语义模型先稳定，再替换底层协议和真实硬件。
- 安全默认拒绝：涉及车控、诊断、OTA、智驾状态的操作必须经过权限和安全状态判断。
- 可观测优先：所有服务、模型、工具调用必须记录 Trace、Metric、版本和结果。
- 支持多后端：AI 推理后端必须能在 mock、CPU、GPU、NPU、云端之间切换。
- 不把 NNAPI 作为新系统核心依赖：Android 15 已弃用 NNAPI，新架构以供应商 runtime 和服务化接口为主。

## 风险和待确认问题

- 外置 NPU 卡型号、驱动 SDK、固件加载方式、PCIe BAR/DMA/中断模型待确认。
- 目标 Android 是普通手机 Android、AAOS 车机系统，还是两者都要支持，需进一步拆分发布形态。
- 原图中的 UNIOS/AIOS 是否已有内部代码或 SDK，需要用户补充。
- 是否需要兼容现有三套 APK 的 HMI/Render Service，需要在第二阶段确认接口复用策略。
- 真实车辆信号源暂缺，第一阶段只能用 mock VSS/VHAL 数据。

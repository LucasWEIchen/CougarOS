# Client2 APK Reverse Demo Path

版本：0.3
日期：2026-07-15

## 目标

本文件定义基于 `Client2` APK 底层逆向产物进行中央大脑演示 App 二次开发的测试工程。该路径用于快速构建接近用户真实座舱界面的演示 APK，同时保留架构图基线、Req ID、Driver/HAL 边界和 Android/Linux 交付边界。

## 输入与工程位置

| 项 | 路径 |
| --- | --- |
| 原始 APK | `apks/original/client2_20260306_170923.apk` |
| 逆向基线 | `reverse/client2/apktool` |
| 测试工程 | `apk-labs/client2-central-brain` |
| 生成工作目录 | `builds/client2-central-brain/workdir` |
| 签名输出 | `builds/client2-central-brain/signed/client2-central-brain.debug.apk` |

`apks/original` 和 `reverse/client2/apktool` 保持基线用途。测试工程每次复制逆向基线到 `builds/client2-central-brain/workdir`，再打补丁、构建和签名，避免污染原始 APK 与原始逆向证据。

## 当前增量

当前已完成一个可验证 APK 底层交互改动：

```text
Client2 MainActivity
├── res/layout/main_layout.xml
│   ├── full-screen: original TuanjieView containers view1/view2/view3
│   ├── overlay: translucent right 1/3 Central Brain menu, initially hidden
│   └── transparent bottom navigation trigger rail
├── AndroidManifest.xml
│   └── Runtime package query + signature Binder permission, no INTERNET
├── classes2.dex
│   └── public SDK/AIDL + Client2ScenarioBridge
└── smali/com/tuanjie/urasclient2
    ├── MainActivity.smali setContentView 后安装 CentralBrainPanelController
    ├── CentralBrainPanelController.smali
    └── CentralBrainPanelController$UiUpdate.smali
```

原始 `TuanjieView` 容器保持 `match_parent` 全屏，不因新增 UI 改变车模 viewport。右侧约 1/3 面板通过根 `FrameLayout` 上的 `centralBrainPanelOverlay` 覆盖车模，使用半透明浅灰背景、12dp 外边距、6dp 圆角、8dp elevation、浅色按钮和浅色回复区。上部固定高度控件区可独立滚动，按“场景任务”“状态与成长”“安全与系统”三组提供 12 个按钮；下部 `centralBrainReplyText` 保持固定结果区域。

面板启动状态为 `GONE`。Client2 底部导航由 Tuanjie/RenderService 绘制，没有 Android `View` 回调；patch 在底部增加透明、可访问性可识别的 `centralBrainNavigationTrigger`，映射当前导航图标。首次点击显示菜单，第二次点击或点击面板外区域隐藏；面板自身消费点击，内部按钮和滚动不会关闭菜单。

每个按钮通过 `android:tag` 绑定稳定 `scenario_id`。smali 控制器递归绑定控件区内全部 `Button`，由 `Client2ScenarioBridge` 和 public `CentralBrainClient` 创建 typed `AgentTaskRequest`，异步 Binder callback 更新回复区。APK 不申请网络权限，不保留 HTTP fallback。12 个场景和底层接口映射见 `CENTRAL_BRAIN_KAKACLAW_REFERENCE_TEST_PLAN.md`；进程内 `requestInFlight` 继续阻止同一 Activity 内的重复并发请求。

该改动不修改 RenderService，不修改 Unity Addressables，不访问真实硬件。它证明 APK 资源 patch、Manifest patch、smali hook、secondary dex、typed Binder、rebuild、zipalign、debug sign 和静态/真机验证链路成立。

## 架构映射

| Req ID | 映射 |
| --- | --- |
| `APP-004` / `XSC-001` | 导航触发的右侧菜单作为 AI SDK/Agent 可视入口，通过 public SDK 提交任务。 |
| `XSC-002` | 后续真实车辆状态必须来自 Uni Info Bus 语义对象；当前 deterministic reply 不读取车辆数据。 |
| `XSC-003` | 后续真实动作必须经 SOA 服务入口，不直接 dispatch 车控或 NPU。 |
| `XSC-005` | Runtime 是唯一调用入口，继续执行可信身份、Capability、Policy 和 Audit 边界。 |
| `XSC-006` | Android Protocol Binding 使用 signature-protected typed Binder；APK 不含 HTTP fallback。 |
| `DEL-001` | Android 是主验证路径，输出可安装 debug APK。 |
| `DEL-003` | 文档给出工程位置、构建命令和边界。 |
| `DEL-004` | 明确 APK patch 与量产 Android system service 的平台差异。 |

## 命令

构建：

```bash
bash tools/build_client2_central_brain_demo.sh
```

执行 Android 13 typed Binder/UI 验收：

```bash
bash tools/test_client2_central_brain_binder.sh --require-api-33
```

该脚本验证面板默认隐藏、导航显示/隐藏、面板外关闭、真实 `care.cold` 按钮、Binder callback 和 UI 回复。故障恢复矩阵使用 `tools/test_client2_central_brain_recovery.sh --require-api-33`。

验证：

```bash
bash tools/check_client2_central_brain_demo.sh
```

安装：

```bash
bash tools/install_client2_central_brain_demo.sh
```

若目标已安装同包但 signer 不同，默认命令返回 `SIGNER_MIGRATION_REQUIRED` 且不修改设备。
在明确允许清除原 Client2 及其应用数据的测试目标上，可执行：

```bash
bash tools/install_client2_central_brain_demo.sh \
  --serial <serial> \
  --replace-conflicting-client2
```

只有 Android 明确返回 signer mismatch 时才会执行卸载；其他安装错误继续失败关闭。

## 非目标

- 不开发虚拟化层。
- 不开发 Driver/HAL。
- 不访问 PCIe NPU、device node、ioctl、sysfs、vendor SDK、DMA、共享内存、车辆总线或 Safety Runtime。
- 不把 APK patch 路径描述为量产 Android system service。
- 不让 App UI 绕过 AI SDK、Uni Info Bus、SOA 和 Runtime & Governance 直接访问模型或 NPU。

## 2026-07-11 模拟器运行测试

测试环境为 `cabin_client_api36_x86_64`、Android API 36、`1920x1080` 和 `-gpu host`。为让测试过程出现在 WSLg/Windows 桌面，本轮直接启动 `emulator`，没有使用 `tools/start_client2_emulator.sh` 中的 `-no-window` 参数。

已通过：

- APK 增量安装成功，包名保持 `com.tuanjie.urasclient2`，`MainActivity` 进入 resumed 状态。
- Client2 原始座舱背景、3D 车辆和右侧约 1/3 Central Brain 面板同时可见；初版运行证据为分屏布局，后续 overlay 修正已重新验证车模 viewport 保持全屏且面板悬浮其上。
- 12 个按钮通过稳定 `scenario_id` 共用同一个后台执行入口；文本框可显示 `请求中`、场景结果和错误。
- APK 到 `http://10.0.2.2:8787/agent/scenarios/run` 的 HTTP 路径返回 `200`；App 无崩溃，未触发 Driver/HAL、硬件或虚拟化访问。

首次测试未通过（历史记录）：

- Ollama 自然语言 `result.generated_text` 尚未通过验收。默认 `CENTRAL_BRAIN_OLLAMA_NUM_PREDICT=96` 时，`qwen3.5:27b-optimized` 返回 `done_reason=length`、`response_length=0`、`thinking_length=337`，APK 因而回退显示 `ollama simulated NPU inference accepted`。
- 将生成上限提高到 `192` 后，端到端请求仍可能超过 APK `120000 ms` read timeout，界面会显示 `请求失败: timeout`。该结果登记到 `ISSUE-019`，不能视为 Ollama 自然语言回复验收通过。

### 修复复测

超时由四项叠加造成：27B 模型约 90% CPU/10% GPU 运行、thinking 消耗输出 token、APK 允许重复点击形成 Ollama 队列，以及测试用 `monkey ... 1` 可能随机注入额外点击。后端与 APK 同时使用 120 秒边界进一步放大了队列超时。

2026-07-11 修复后，Ollama adapter 默认 `think=false`，可通过 `CENTRAL_BRAIN_OLLAMA_THINK` 显式覆盖；本地演示使用 90 秒后端 timeout、64 token 上限，并从结构化模型输出中优先提取 `response_text`。APK 增加 single-flight，测试启动改用确定性的 `adb shell am start -n com.tuanjie.urasclient2/.MainActivity`。

清空旧 Ollama 队列后的可信单请求复测只产生一条 HTTP 请求，在 APK 120 秒 read timeout 内返回 HTTP 200，面板显示非空中文建议。`CENTRAL_BRAIN_OLLAMA_THINK=false` 下直接推理与 SOA `npu-inference` smoke 均通过，`generated_text` 非空且 `thinking_text_available=false`。

生产路径仍按计划迁移到 Binder/SDK，不因本次演示修复改变架构边界。

### Overlay 布局复测

2026-07-11 使用 API 36 可视模拟器和 `1920x1080` skin 重新安装、冷启动最终签名 APK。UI dump 显示 `centralBrainRenderRegion` 与 `centralBrainPanelOverlay` 均为 Activity 全内容区 `[0,128][1920,1080]`，说明新增 UI 没有改变车模渲染宽度；`centralBrainPanel` 位于 `[1265,160][1888,1048]`，约占物理屏宽三分之一并保留四周外边距。

运行截图确认车身、天气和底部座舱控件继续绘制到面板下方，浅灰面板可透出原车模内容；当时的两个初版按钮和回复区均在面板内，Activity 保持 resumed，过滤后的 logcat 未出现 `FATAL EXCEPTION`。证据位于 `logs/test/client2-central-brain-live/20260711_193406/`；该目录只作为本地测试输出，不纳入源码交付。

### 12 场景面板与最终复测

最终面板包含 `care.cold`、`care.fatigue`、`task.home`、`skill.nap`、`state.vehicle`、`memory.preference`、`skills.catalog`、`governance.audit`、`security.denied`、`security.privacy`、`runtime.npu` 和 `system.overview`。控件区高度固定并独立滚动，动态内容不会挤压下部结果区；两列按钮使用稳定尺寸，避免滚动或状态文本引发布局跳动。

2026-07-11 在 API 36、`1920x1080` 可视模拟器中重新构建、签名、安装和启动 APK。实机抽样结果：

- `回家规划` 返回 5 步任务图和 `validated_mock`，同时说明未调用真实导航或车控。
- `越权拦截` 返回 `DENY` 和缺少权限/安全状态原因。
- `NPU状态` 返回 `ollama-simulated-npu`、`qwen3.5:27b-optimized` 和 `hardware_accessed=false`。
- `系统总览` 返回当前 Python 原型范围完成、Android/Linux 同步就绪、`production_ready=false`。
- `我冷了` 通过本地 Ollama 仿真链路约 77.6 秒返回中文建议和 Action 门禁状态；当时 `ollama ps` 显示模型约为 `90%/10% CPU/GPU`，不作为真实 NPU 性能验收。

UI dump 验证首屏和滚动后全部 12 个按钮可见且可点击，Activity 保持 resumed。稳定截图位于 `logs/test/client2-central-brain/20260711_scenarios/`；该目录是本地测试证据，不纳入源码提交。当前环境图形后端回退到 `llvmpipe`，紧邻 Unity 帧更新的瞬时 `screencap` 可能出现黑块，延迟后的稳定截图正常。

## 2026-07-15 导航菜单真机验收

在 1920x1080、160 dpi、Android 13/API 33 ARM64 物理控制器上重新构建、安装并验收。UIAutomator
识别到透明导航目标 `[760,984][840,1080]`，与当前底部导航图标对齐。自动化依次验证启动时
`centralBrainColdButton` 不可见、首次导航点击显示、第二次点击隐藏、再次显示后点击面板外隐藏、
再次打开并完成 `care.cold` typed Binder/UI 回复。R7C 恢复矩阵确认 Client2 进程重启后菜单可以
重新打开，且 Runtime 不可用/死亡/恢复、single-flight 和 Binder race 未回归。

该坐标只记录当前受测显示配置，不是跨分辨率稳定接口。量产应改用源码 HMI 导航事件或厂商公开
回调；在此之前，其他 density、分辨率或主题必须单独执行触点与可访问性回归。

## 已知风险

1. Client2 原始源码不可用，长期维护风险高于源码工程。
2. Debug 重签名已在当前 API 33 ARM64 测试设备通过 RenderService 画面验证，但不代表生产 signer、OTA/MDM 或量产 allowlist 已批准。
3. RenderService 是 ARM64/Unity/Tuanjie 运行时；x86_64 模拟器证据仍不能替代目标 ARM64 验收。
4. 底部导航是闭源渲染内容，透明触摸目标依赖当前显示几何；分辨率、density、主题或导航布局变化可能造成触点漂移。
5. 当前 Client2 已迁移到 Binder/SDK 且无网络 fallback；Runtime deterministic reply 不代表 Python/Ollama、真实模型或 NPU 已接入 Android 实际工程。
6. 历史 Ollama 演示只证明用户态仿真可达；目标模型、输出预算、目标算力和端到端时延仍须独立标定。
7. KaKaClaw 只作为公开产品概念参考；连续多轮、人格/方言、零代码 Skill、主动触发、真实导航/媒体/车控/ADAS、量产 Skill sandbox 和 Privacy Router 尚未实现，见 ISSUE-020。

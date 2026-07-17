# Client2 APK Reverse Demo Path

版本：0.4
日期：2026-07-17

## 目标

本文件定义基于 `Client2` APK 底层逆向产物进行中央大脑演示 App 二次开发的测试工程。该路径用于快速构建接近用户真实座舱界面的演示 APK，同时保留架构图基线、Req ID、Driver/HAL 边界和 Android 13 交付边界。

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
│   ├── public SDK/AIDL + Client2ScenarioBridge
│   ├── immutable CockpitHmiState + sole CockpitHmiReducer
│   └── maintained Java CockpitControlCoordinator
└── smali/com/tuanjie/urasclient2
    └── MainActivity.smali setContentView 后仅调用 CockpitControlCoordinator.install
```

原始 `TuanjieView` 容器保持 `match_parent` 全屏，不因新增 UI 改变车模 viewport。右侧约 1/3 面板通过根 `FrameLayout` 上的 `centralBrainPanelOverlay` 覆盖车模，使用半透明浅灰背景、12dp 外边距、6dp 圆角、8dp elevation、浅色按钮和浅色回复区。上部固定高度控件区可独立滚动，按“场景任务”“状态与成长”“安全与系统”三组提供 12 个按钮；下部 `centralBrainReplyText` 保持固定结果区域。

面板启动状态为 `GONE`。Client2 底部导航由 Tuanjie/RenderService 绘制，没有 Android `View` 回调；patch 在底部增加透明、可访问性可识别的 `centralBrainNavigationTrigger`，映射当前导航图标。首次点击显示菜单，第二次点击或点击面板外区域隐藏；面板自身消费点击，内部按钮和滚动不会关闭菜单。

每个按钮通过 `android:tag` 绑定稳定 UI alias。P4-W01 后，`Client2ScenarioBridge.openSession` 通过 12 项 exact
map 转换为 canonical Session ID，再由 public `SessionClient` 打开 Session。P4-W02 把 typed
snapshot/event/replay callback 统一送入 immutable `CockpitHmiState` 和唯一 reducer；Java coordinator 只按
reducer state 渲染，且负责 Session replacement、Activity lifecycle、reconnect 和 existing Session resume。
旧 Smali controller 已删除，旧 `submit(...)` 只作为未被当前 UI 调用的兼容入口。APK 不申请网络权限，不保留
HTTP fallback。场景产品定义和实现顺序见 `CENTRAL_BRAIN_AIOS_STAGE2_PRODUCT_UX_PLAN.md` 与
`CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md`。

Process-local retain state 用于同进程 Activity recreate；app-private SharedPreferences checkpoint 只保存 schema、
panel visibility、UI/canonical alias、SessionHandle metadata、last sequence 和 resume cursor。用户输入、模型文本、
summary、reply projection 和车辆 payload 均不持久化。该恢复层是 debug APK 兼容实现，不是量产加密 HMI state store。

该改动不修改 RenderService，不修改 Unity Addressables，不访问真实硬件。它证明 APK 资源 patch、Manifest patch、smali hook、secondary dex、typed Binder、rebuild、zipalign、debug sign 和静态/真机验证链路成立。

## 架构映射

| Req ID | 映射 |
| --- | --- |
| `APP-004` / `XSC-001` | 导航触发的右侧菜单作为 AI SDK/Agent 可视入口，通过 public Session/Event SDK 打开会话。 |
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

## 退役测试路径记录

2026-07-11 曾使用 API 36 模拟器、HTTP gateway 和本机模型验证 UI 布局及早期请求链路。该链路已于
2026-07-16 随 Python 原型整体退役，不再提供构建、启动、回归或故障处理支持，也不能作为当前
APK 的验收依据。现行 Client2 只允许通过 public typed Android SDK/Binder 访问 Runtime。

旧测试日志保留在本地 `logs/` 时也不属于源码、发布包或当前证据。当前验收入口只有本文件所列
Binder/UI 脚本、Android 13 目标设备证据和受控 GitHub 硬件测试流程。

## 2026-07-15 导航菜单真机验收

在 1920x1080、160 dpi、Android 13/API 33 ARM64 物理控制器上重新构建、安装并验收。UIAutomator
识别到透明导航目标 `[760,984][840,1080]`，与当前底部导航图标对齐。自动化依次验证启动时
`centralBrainColdButton` 不可见、首次导航点击显示、第二次点击隐藏、再次显示后点击面板外隐藏、
再次打开并完成 `care.cold` typed Binder/UI 回复。P4-W02 恢复矩阵确认 Client2 进程重启后恢复同一 Session，
隐藏面板状态保持不变，重新打开后 replay projection 继续；Runtime 不可用/死亡/恢复、Session reconnect/replay、
duplicate suppression、Session replacement 和 Binder race 未回归，checkpoint 未持久化显示文本。

该坐标只记录当前受测显示配置，不是跨分辨率稳定接口。量产应改用源码 HMI 导航事件或厂商公开
回调；在此之前，其他 density、分辨率或主题必须单独执行触点与可访问性回归。

## 已知风险

1. Client2 原始源码不可用，长期维护风险高于源码工程。
2. Debug 重签名已在当前 API 33 ARM64 测试设备通过 RenderService 画面验证，但不代表生产 signer、OTA/MDM 或量产 allowlist 已批准。
3. RenderService 是 ARM64/Unity/Tuanjie 运行时；x86_64 模拟器证据仍不能替代目标 ARM64 验收。
4. 底部导航是闭源渲染内容，透明触摸目标依赖当前显示几何；分辨率、density、主题或导航布局变化可能造成触点漂移。
5. 当前 Client2 已迁移到 Binder/SDK 且无网络 fallback；Runtime deterministic test reply 不代表真实模型或 NPU 已接入 Android 实际工程。
6. 目标模型、输出预算、目标算力和端到端时延仍须在 Vendor provider 与真实 NPU 可用后独立标定。
7. KaKaClaw 只作为公开产品概念参考；连续多轮、人格/方言、零代码 Skill、主动触发、真实导航/媒体/车控/ADAS、量产 Skill sandbox 和 Privacy Router 尚未实现，见 ISSUE-020。

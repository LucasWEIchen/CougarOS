# Client2 渲染清晰度、动态温区与车模旋转修复详设

版本：1.1
日期：2026-07-26
状态：`TESTBOARD_PARTIAL_VERIFIED / ORBIT_BLOCKED_NO_TOUCH_DEVICE`

## 1. 需求与追踪

本增量为 `P4-R7`，覆盖 `APP-004`、`S2-HMI-001..004`、`S2-UX-002/003`
和 `DEL-004`：

1. 1920x1080 设备上的 Unity 车模请求更高内部渲染分辨率，降低模糊感。
2. 双区温度使用 Unity 原生字体，支持 18.0-30.0°C、0.5°C 步进和逐级动画。
3. 车模保持原厂滑动旋转合同，同时保留车门等既有 Unity 点击交互。

本增量只修改 Client2 和 RenderService 两个普通 Android APK，不修改系统镜像、厂商
Framework/BSP、`libtuanjie.so`、Vehicle/VHAL/CAN 或 Driver/HAL。

## 2. 根因与修复

### 2.1 清晰度

目标屏幕、Android display 和 RenderService Surface 均为 1920x1080。原始 RenderService
内部 framebuffer 也是 1920x1080，实际 MSAA sample 为 0。Client2 在 `TuanjieView`
attach 后请求 `setRenderScale(1.5f)`；测试板 RenderService 日志确认其将 framebuffer
提升为 2880x1620。该结果证明请求被采用，不等于完成 GPU 性能或 Unity 图形质量标定。

### 2.2 温度

原温度是固定 RawImage 纹理，不能表达连续温度。P4-R7 在 RenderService Addressables
bundle 中克隆已有 TextMeshPro 样式，创建驾驶席和乘员席动态文本，并禁用会重叠的固定纹理。

Unity 当前 TextMeshPro 的 `SetText` 是双参数重载，不能由单字符串 `SendMessage` 命中。
Client2 必须调用属性 setter：

```text
c2sSendMessage(zoneObject, "set_text", "27.0°C")
```

早期初始化同步也被删除。bundle 本身保持 26.5°C 默认值，避免动态对象加载前发送消息造成
无效调用或启动时覆盖。

### 2.3 旋转

早期诊断错误地把以下三个编号空间视为同一含义：

- Android 合成 MotionEvent：`displayId=0`；
- RenderService 渲染 surface：`DisplayIndex=1`；
- Unity InputSystem Pan recognizer：`targetInputDisplay=2`。

修改 Pan target/raycast 后，ADB swipe 仍不能旋转。安装未修改的原厂 RenderService 后，
相同 swipe 也不能旋转。进一步检查发现测试板只有 `gpio-keys` 和 `madev` 输入设备，没有
物理触摸 `/dev/input/event*`；ADB swipe 生成 `deviceId=-1/displayId=0` 的合成事件，不能
替代厂商 Unity InputSystem 的物理触摸路由。

最终实现删除所有 Pan mutation，改为构建时验证并保留原厂合同：

```text
targetInputDisplay=2
eventSystemRaycastCheck=true
useFingerPolling=false
```

尝试通过反射调用隐藏的 `InputEvent.setDisplayId(2)` 被 Android 13 hidden API policy
拒绝，该临时代码已完全删除。旋转验收必须在存在真实触摸输入的目标硬件上完成。

## 3. 模块与接口

| 模块 | 责任 | 主要接口 |
| --- | --- | --- |
| `CockpitControlCoordinator` | 双区温度状态、逐级动画、手动 +/- 命中和 Unity 消息发送 | `animateTemperature`、`setUnityTemperature`、`c2sSendMessage` |
| `TuanjieView` | RenderService Binder 代理、渲染尺度和原生触摸路径 | `getRenderScale`、`setRenderScale(1.5f)` |
| RenderService bundle patch | 创建动态温区对象；验证但不改写 Pan recognizer | TextMeshPro `set_text`、vendor Pan assertions |
| 构建与门禁脚本 | 幂等修改 bundle、重建/签名、检查关键合同 | `build_debug_apk.sh`、`verify_project.sh` |

温度调用链：

```text
Cold Effect / 手动温区按钮
  -> clamp(18.0, 30.0) and quantize(0.5)
  -> 360 ms per visible step
  -> Client2 reflect mTuanjieRenderService
  -> c2sSendMessage(zoneObject, "set_text", temperatureLabel)
  -> RenderService Unity TextMeshPro
  -> driver/passenger native temperature text
```

旋转目标链：

```text
物理触摸屏滑动
  -> Client2 observing OnTouchListener returns false
  -> original TuanjieView/Binder input path
  -> RenderService Unity EventSystem/InputSystem
  -> vendor InputSystemPanRecognizer(targetInputDisplay=2)
  -> CustomOrbitCamera
```

## 4. 温度状态机

- 最小值：18.0°C。
- 最大值：30.0°C。
- 量化步进：0.5°C。
- 初始值：26.5°C。
- AI Cold 场景：从当前值逐级变化到 28.0°C。
- 手动 +/-：驾驶席和乘员席独立，每次改变 0.5°C。
- 越界输入：先 clamp，再按 0.5°C 量化。
- Unity 不可用：记录 `unity_temperature_dispatch=false`，不恢复 Android TextView。

动态对象名固定为 `CentralBrainDriverTemperature` 和
`CentralBrainPassengerTemperature`。原固定温度 RawImage 被禁用，避免重叠。

## 5. 交付物

```text
builds/renderservice-central-brain/signed/renderservice-central-brain.debug.apk
SHA-256 852be3788328ac788de903f9f2173ab296c30b393dbd2857118e3914a758cc88

builds/client2-central-brain/signed/client2-central-brain.debug.apk
SHA-256 71c9176c86bf363a51206ff02c4eed27e9494b4bf6db00028de7bd8fec3901ed
```

两个 APK 的 signer digest 均为
`2079de9bf19818b40d25acbcd8cb7643549b04a536b057997ef9103e1dc241a1`，
必须成对安装。

## 6. 2026-07-26 测试板验收

设备为 Android 13/API 33、ARM64、1920x1080 的 `testboard`。生产板未操作。

| 用例 | 结果 | 证据边界 |
| --- | --- | --- |
| 清晰度 | PASS | RenderService 采用 1.5 render scale，framebuffer 2880x1620 |
| 初始温度 | PASS | 双区 26.5°C，动态文本无固定纹理重叠 |
| 手动调温 | PASS | 0.5°C 步进；驾驶席到 18.0/30.0°C；乘员席独立到 27.0°C |
| Cold 状态机 | PASS / SOFTWARE | 26.5 -> 27.0 -> 27.5 -> 28.0 动画合同和调度门禁通过 |
| 车门点击 | PASS | 前车门 Unity Button 可点击并显示打开状态 |
| Fatigue 闭环 | PASS | 真实 WSL OpenClaw/Ollama；117383 ms；3 个模拟 Effect；座椅 15° -> 30° |
| 稳定性 | PASS | Client2、RenderService、Runtime 无 crash/ANR |
| 车模旋转 | BLOCKED | 测试板无物理触摸 event node；原厂 APK 的 ADB swipe 基线同样失败 |

真实模型回归使用当前 WSL OpenClaw Gateway 和 Ollama
`qwen3.5:27b-optimized`，通过 `ADB_SERVER_PORT=5038` 连接 Windows ADB。模型输出只进入
白名单 Graph 和 UI 仿真 Effect，明确显示 `VEHICLE BUS NOT ACCESSED`。

## 7. 复验命令

仓库门禁：

```bash
bash tools/check_central_brain_unity_native_hvac_seat.sh
bash apk-labs/renderservice-central-brain/scripts/verify_project.sh
bash apk-labs/client2-central-brain/scripts/verify_project.sh
```

当前 Windows ADB server 复验示例：

```bash
ADB_SERVER_PORT=5038 \
ANDROID_SERIAL=testboard \
CENTRAL_BRAIN_OPENCLAW_MODEL_PORT=11434 \
CENTRAL_BRAIN_OPENCLAW_MODEL=qwen3.5:27b-optimized \
CENTRAL_BRAIN_CLIENT2_SCENARIO=fatigue \
CENTRAL_BRAIN_SKIP_CLIENT2_BUILD=true \
CENTRAL_BRAIN_CLIENT2_OPENCLAW_TIMEOUT_SECONDS=180 \
bash tools/run_client2_central_brain_openclaw_development_test.sh
```

物理旋转关闭条件：在带触摸输入的目标硬件上，记录一次连续滑动前后车模朝向变化，并确认
车门 Button 仍可点击。不得用 ADB 合成 swipe 替代。

## 8. 外部边界

本交付仍是 HMI 仿真。真实 HVAC/Seat target、readback、Safety authority、VHAL/SOA adapter、
量产 signer、GPU 性能标定和 Unity 源工程合入不在本增量内，保持
`vehicle_bus_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。

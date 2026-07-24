# Client2 渲染清晰度、动态温区与车模旋转修复详设

版本：1.0
日期：2026-07-25
状态：仓库实现与构建完成，测试板 ADB offline，ARM64 动态复测待恢复

## 1. 需求与追踪

本增量为 `P4-R7`，覆盖 `APP-004`、`S2-HMI-001..004`、`S2-UX-002/003`
和 `DEL-004`。它处理三个应用层回归：

1. 1920x1080 设备上的 Unity 车模必须使用更高内部渲染分辨率，降低模糊感。
2. 双区温度必须使用与原 Launcher 接近的 Unity 原生字体，支持 18.0-30.0°C、
   0.5°C 步进和逐级动画，不能只在 26.5/28.0 两档间跳变。
3. 车模必须恢复滑动旋转，同时保留车门等既有 Unity 点击交互。

本增量只修改 Client2 和 RenderService 两个普通 Android APK，不修改系统镜像、
厂商 Framework/BSP、`libtuanjie.so`、Vehicle/VHAL/CAN 或 Driver/HAL。

## 2. 根因

### 2.1 清晰度

目标屏幕、Android display 和 RenderService Surface 均为 1920x1080，没有发现系统级缩放。
Unity 日志显示 framebuffer 仍为 1920x1080，且实际 MSAA sample 为 0。原始 RenderService
对照包也存在相同模糊，因此根因位于 Unity 内部渲染质量，而不是 Android 布局尺寸。

### 2.2 温度

原温度是 512x128 的固定 RawImage 纹理。P4-R6 的 28.0°C 使用了与温区无关的 TextMeshPro
对象作为模板，字体、字重和位置无法与 26.5°C 一致，而且只能执行两档 `SetActive`。

### 2.3 旋转

RenderService 实际把 `TuanjieView` 注册为 `DisplayIndex=1`，但 Unity bundle 中
`InputSystemPanRecognizer._targetInputDisplay=2`。触摸 DOWN/MOVE/UP 已完整进入
`TuanjieView`，车门点击有效，但 Pan recognizer 因 display 不匹配而拒绝旋转；
`_eventSystemRaycastCheck=1` 还会让覆盖 UI 参与拦截判断。

## 3. 模块与接口

| 模块 | 责任 | 主要接口 |
| --- | --- | --- |
| `CockpitControlCoordinator` | 维护双区温度状态、逐级动画、手动 +/- 命中和 Unity 消息发送 | `animateTemperature`、`setUnityTemperature`、`c2sSendMessage` |
| `TuanjieView` | 提供 RenderService Binder 代理和渲染尺度 | `getRenderScale`、`setRenderScale(1.5f)` |
| RenderService bundle patch | 创建动态温区对象，修正 Pan recognizer | TextMeshPro `SetText`、display/raycast 字段 |
| 构建与门禁脚本 | 幂等修改 bundle、重建/签名、检查关键合同 | `build_debug_apk.sh`、`verify_project.sh` |

调用链：

```text
Cold Effect / 手动温区按钮
  -> clamp(18.0, 30.0) and quantize(0.5)
  -> 360 ms per visible step
  -> Client2 reflect mTuanjieRenderService
  -> c2sSendMessage(zoneObject, "SetText", "27.0°C")
  -> RenderService Unity TextMeshPro
  -> driver/passenger native temperature text
```

旋转链：

```text
用户在车模区域滑动
  -> TuanjieView OnTouchListener observes and returns false
  -> original TuanjieView/Binder touch path
  -> RenderService Unity EventSystem
  -> InputSystemPanRecognizer(targetDisplay=1, raycastCheck=false)
  -> CustomOrbitCamera
```

## 4. 温度状态机

- 最小值：18.0°C。
- 最大值：30.0°C。
- 量化步进：0.5°C。
- 初始值：26.5°C。
- AI Cold 场景：从当前值逐级变化到 28.0°C。
- 手动 +/-：分别命中驾驶席和乘员席原生按钮区域，每次改变 0.5°C。
- 越界输入：先 clamp，再按 0.5°C 量化。
- Unity 不可用：记录 `unity_temperature_dispatch=false`，不恢复 Android TextView。

动态对象名固定为 `CentralBrainDriverTemperature` 和
`CentralBrainPassengerTemperature`。原 `27`、`27 (1)` RawImage 对象禁用，
避免固定纹理与动态文本重叠。

## 5. 渲染与触摸

Client2 在 `TuanjieView` attach 后反射调用 `setRenderScale(1.5f)`，并记录请求前后值。
该设置只提高 RenderService 内部 buffer 请求，是否被 GPU/Unity 最终采纳必须由真机日志和
截图验证，不能仅凭静态代码宣称清晰度达标。

Client2 的观察型 `OnTouchListener` 始终返回 `false`，不得消费滑动或点击。RenderService
bundle 将 Pan recognizer 的 target display 改为 1，并关闭额外 raycast gate；车门 Button
仍由原 EventSystem 处理。

## 6. 交付物

配对 APK：

```text
builds/renderservice-central-brain/signed/renderservice-central-brain.debug.apk
SHA-256 6a7d90b243a6e67ab6fbd119b3f1a3ad37610610bb2d0ec95e5ef21bda30b8af

builds/client2-central-brain/signed/client2-central-brain.debug.apk
SHA-256 252b45813948d78b69a4303e4fd0ff261f58ae4c018e36bbc8a6e25bd5697e30
```

两个 APK 的 signer digest 均为
`2079de9bf19818b40d25acbcd8cb7643549b04a536b057997ef9103e1dc241a1`，
必须成对安装。

## 7. 验收

仓库门禁：

```bash
bash tools/check_central_brain_unity_native_hvac_seat.sh
bash apk-labs/renderservice-central-brain/scripts/verify_project.sh
bash apk-labs/client2-central-brain/scripts/verify_project.sh
```

测试板必须逐项验证：

| 用例 | 通过条件 | 当前状态 |
| --- | --- | --- |
| 清晰度 | 日志确认 1.5 render scale 被采纳，1920x1080 截图主观复核改善 | BLOCKED |
| 初始温度 | 双区为 26.5°C，字体一致，无固定纹理重叠 | BLOCKED |
| 手动调温 | 每次 0.5°C，18.0/30.0 边界不越界 | BLOCKED |
| Cold 动画 | 26.5→27.0→27.5→28.0，步骤可见 | BLOCKED |
| 车模旋转 | 连续滑动改变车模朝向，车门点击仍有效 | BLOCKED |
| 稳定性 | Client2/RenderService 无 crash/ANR | BLOCKED |

阻塞原因是 `testboard` 当前由 Windows ADB 枚举为 `offline`，不是代码或 APK 构建失败。
恢复设备授权/传输会话后，仅在测试板部署；生产板下线期间不操作生产环境。

## 8. 外部边界

本交付仍是 HMI 仿真。真实 HVAC/Seat target、readback、Safety authority、VHAL/SOA adapter、
量产 signer、GPU 性能标定和 Unity 源工程合入不在本增量内，保持
`vehicle_bus_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。

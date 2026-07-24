# Client2 Unity 原生 HVAC 与座椅动画修复详设

版本：1.0
日期：2026-07-24
状态：测试板与生产板应用层复测通过

## 1. 需求与边界

本增量编号为 `P4-R6`，追踪 `S2-HMI-001/002/003/004`、
`S2-UX-002`、`S2-ADP-001/002`、`S2-SAF-001` 和 `DEL-004`。

必须同时满足：

1. “我有些疲惫”的驾驶席靠背动画从 15 度向 30 度展开，不能向坐垫方向合拢。
2. “车里有点冷”的 28.0°C 结果必须显示在 RenderService 的 Unity 原生温区中。
3. Client2 不得再在 Unity 画面上叠加 Android 温度 TextView。
4. 驾驶席和乘员席必须保持双区一致。
5. 所有温度和座椅结果继续明确标记为仿真，不访问 Vehicle/VHAL/CAN/Driver-HAL。

本增量允许修改应用层 Client2 APK 和独立 RenderService APK；不修改 Android
系统镜像、厂商 Framework/BSP、`libtuanjie.so` 或真实车辆控制接口。

## 2. 模块

| 模块 | 责任 | 输入 | 输出 |
| --- | --- | --- | --- |
| `CockpitControlCoordinator` | 接收已准入的 HVAC/Seat 仿真结果，驱动动画和 Unity 触控 | Orchestration Effect projection | 座椅角度、双区 Unity 状态切换 |
| `TuanjieView` | 将合法触屏 MotionEvent 经 Binder 送入 RenderService | local x/y、finger/touchscreen event | `c2sOnTouchEvent` |
| RenderService Unity bundle patch | 在 `UI_Launcher.prefab` 内维护 26.5/28.0 双状态 | 原生 Button onClick | Unity 原生温区可见状态 |
| Client2 panel XML | 承载调用链和左侧执行器仿真 | reducer state | 不包含底部温度 overlay |
| build/verify scripts | 可重复 patch、APK 重建、签名和静态验收 | 原始 APK、Unity bundle | 签名 debug APK、门禁结果 |

## 3. 座椅靠背方向

座椅靠背 View 的 pivot 固定在底边中心。角度从 `from=15` 增长到 `to=30`
时使用负旋转：

```java
seatBackView.setRotation(-(current - from) * 1.2f);
```

在 Android 屏幕坐标系中，正旋转会让靠背顶端向坐垫方向移动；负旋转让顶端
向车尾方向移动，因此视觉语义是展开/后仰。

## 4. Unity 原生温度状态

### 4.1 Patch 对象

维护工程：
`apk-labs/renderservice-central-brain`

目标 bundle：
`assets/aa/HMIAndroid/launcher_assets_all_c93fe44a4d61e1b9545c50b3baddfbd7.bundle`

原生 26.5°C 状态继续使用两个既有 GameObject：

- 驾驶席：`27`
- 乘员席：`27 (1)`

新增 TextMeshPro 原生状态：

- `CentralBrain_driver_temperature_28_0`
- `CentralBrain_passenger_temperature_28_0`

新增对象使用固定 path ID，确保重复构建为幂等更新而不是无限复制。两个既有
减/加温 Button 的 persistent calls 调用 Unity 内置
`GameObject.SetActive(bool)`，分别切换 26.5°C 与 28.0°C。

### 4.2 Client2 到 Unity 的调用

```text
Cold Effect admitted
  -> animateTemperature(26.5, 28.0)
  -> setUnityTemperatureState(true)
  -> resolve TuanjieView under view1
  -> driver Unity increase button
  -> 520 ms
  -> passenger Unity increase button
  -> TuanjieView.c2sOnTouchEvent
  -> RenderService Unity EventSystem
  -> Button persistent SetActive calls
  -> native 28.0°C states visible
```

MotionEvent 必须与成功的真实触屏事件一致：

- `TOOL_TYPE_FINGER`
- `SOURCE_TOUCHSCREEN`
- `deviceId=-1`
- `metaState=0`
- down/up 使用相同的事件时间戳

这些字段不能简化为默认 `UNKNOWN`。Tuanjie 跨进程输入路径会把 MotionEvent
原样送入 Unity，错误的 device/meta 字段会被 Android 接收，但不会触发 Unity Button。

## 5. 状态与失败处理

- 找不到 `TuanjieView` 或尺寸为零：记录
  `unity_hvac_native_dispatch=false`，不绘制 Android fallback 温度。
- 成功送出双区事件：记录尺寸、目标状态、坐标和 down/up handling，不记录用户文本、
  模型回复、凭据或车辆数据。
- Unity bundle 与 Client2 必须成对安装；只安装 Client2 不会创建原生 28.0°C 状态。
- 原生 UI 状态只是演示反馈，不是真实 HVAC target/readback。

## 6. 构建

```bash
source env.sh
apk-labs/renderservice-central-brain/scripts/build_debug_apk.sh
apk-labs/client2-central-brain/scripts/build_debug_apk.sh
```

输出：

```text
builds/renderservice-central-brain/signed/renderservice-central-brain.debug.apk
builds/client2-central-brain/signed/client2-central-brain.debug.apk
```

## 7. 验收

静态门禁：

```bash
tools/check_central_brain_unity_native_hvac_seat.sh
```

Android 13 ARM64 测试板证据：

| 用例 | 结果 |
| --- | --- |
| 初始双区显示 Unity 原生 26.5°C，Android overlay 不存在 | PASS |
| Cold 真实 OpenClaw 完成后，双区 Unity 原生显示 28.0°C | PASS |
| Fatigue 靠背从 15 度向 30 度展开 | PASS |
| `vehicle_bus_accessed=false` | PASS |

生产板恢复 ADB 后成对安装当前最终 Client2/RenderService APK。目标以太 OpenClaw
Cold/Fatigue 分别在 11132 ms、7207 ms 完成；原生双区 28.0°C、靠背 15°→30° 展开和
crash/ANR 检查通过；Client2/RenderService 冷启动后双区恢复原生 26.5°C。该结论仅为普通
Android 应用层 HMI 仿真复测，不是车辆控制验收。

## 8. 未完成外部项

- OEM Vehicle/VHAL/SOA HVAC 和 Seat adapter。
- 真实 target/readback、超时、误差和撤销。
- 可信 driving/gear/belt Context 与座椅 Safety authority。
- 正式 RenderService/Client2 signer、OTA/rollback 和厂商源码合并。

这些项目继续由 `ISSUE-019/023/029/030/052` 跟踪。

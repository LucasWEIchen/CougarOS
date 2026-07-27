# Central Brain Android 真实硬件到 WSL OpenClaw 开发网关

状态：`P7-R4-OCDEV / ANDROID13_ARM64_VERIFIED`
Req IDs：`S2-MDL-001/002`、`S2-SAF-001`、`S2-OBS-001/002`、`XSC-001/005/006`、
`DEL-001/003/004/005`

## 1. 目标

开发测试使用真实 Android 13 座舱硬件和真实 WSL OpenClaw/Ollama，不再让 APK 直接调用 WSL Ollama，也不使用
deterministic model stub。USB 只承担 ADB 调试与 `adb reverse` 端口映射；从 APK 视角，模型仍是一个外置网络网关。

该拓扑用于尽量接近“Android 座舱通过以太网访问外部算力基座”的调用层次，但不能替代量产以太网、链路安全、
目标 NPU、资源治理或性能证据。

## 2. 拓扑

```text
Client2 UI
  -> Central Brain Runtime APK
  -> ws://127.0.0.1:18789/ (Android development profile)
  -> adb reverse tcp:18789 tcp:18789
  -> WSL OpenClaw Gateway 2026.7.1
  -> Ollama provider http://127.0.0.1:11435
  -> qwen3.6:27b
  -> OpenClaw structured terminal reply
  -> scenario/action allowlist
  -> debug-only model projection
  -> Client2 trace and simulated actuator feedback
```

## 3. 与量产 profile 的隔离

| 属性 | 开发测试 | 量产过渡 profile |
| --- | --- | --- |
| Build profile | `development_wsl_openclaw` | `target_openclaw_transitional` |
| Android endpoint | `ws://127.0.0.1:18789/` | `ws://169.254.208.110:18789/` |
| Transport | ADB reverse | 目标以太网 |
| OpenClaw protocol | v4 | v3 |
| Provider assurance | DEVELOPMENT | TARGET_INTEGRATION |
| Release routing | disabled | disabled |
| Production qualified | no | no |

协议版本按 endpoint profile 固定，不能运行时覆盖。开发网关升级到 v4 不改变冻结的目标 v3 合同。

## 4. Android 握手合同

开发 profile 使用 OpenClaw v4：

1. RFC6455 WebSocket upgrade；开发分支不发送浏览器 `Origin`。
2. 接收 `connect.challenge`。
3. 发送 `connect`，`minProtocol=maxProtocol=4`。
4. 客户端身份固定为 `gateway-client/backend`，显示名为 `CougarOS Android runtime`。
5. 只申请 `operator.read` 和 `operator.write`，使用 build-owned shared token。
6. 发送 `chat.send`，绑定 session、run 和 idempotency key。
7. 消费 `chat` delta/final；终态缺文本时最多读取六条 `chat.history`。
8. 超时或失败时 best-effort 发送 `chat.abort`。

`gateway-client/backend` 只用于开发 ADB 回环拓扑。OpenClaw v4 会清除无设备密钥的 UI client scope；本 Runtime
承担的是模型网关后端桥接，而不是浏览器控制页。量产 v3 分支继续保留原有 target identity 和 Origin 行为。

## 5. WSL 前置条件

- `openclaw-gateway.service` 必须为 active，监听 `127.0.0.1:18789` 或 `0.0.0.0:18789`。
- OpenClaw 运行时 Node 必须满足当前版本要求；本次验证使用 Node `24.18.0`。
- Gateway shared token 必须与 Android build-owned profile 一致；修改配置后必须重启 Gateway。
- OpenClaw 的 Ollama provider 指向 `http://127.0.0.1:11435`。
- `qwen3.6:27b` 必须出现在 `/api/tags`。
- Windows ADB 位于 `E:\platform-tools`，或通过 `ADB_BIN` 显式指定。

上述 11435/qwen3.6 是已冻结开发合同的默认值。脚本允许通过
`CENTRAL_BRAIN_OPENCLAW_MODEL_PORT` 和 `CENTRAL_BRAIN_OPENCLAW_MODEL` 选择当前 WSL 已安装
模型；该覆盖只改变本机开发前置检查，不改变 Android endpoint、OpenClaw 协议或量产 profile。

## 6. 执行

只有一台 ADB 设备时：

```bash
tools/run_central_brain_android_openclaw_development_probe.sh
```

多设备时必须显式选择无敏感信息的 transport ID：

```bash
ANDROID_TRANSPORT_ID=<id> \
  tools/run_central_brain_android_openclaw_development_probe.sh
```

当 Windows ADB server 不在默认 5037 端口时，可直接指定 server port 和稳定设备别名：

```bash
ADB_SERVER_PORT=5038 \
ANDROID_SERIAL=testboard \
  tools/run_central_brain_android_openclaw_development_probe.sh
```

执行 Client2 -> Runtime -> OpenClaw -> Ollama -> UI 仿真末端的完整回归：

```bash
ANDROID_TRANSPORT_ID=<id> \
  tools/run_client2_central_brain_openclaw_development_test.sh
```

2026-07-26 测试板使用的完整模型覆盖为：

```bash
ADB_SERVER_PORT=5038 \
ANDROID_SERIAL=testboard \
CENTRAL_BRAIN_OPENCLAW_MODEL_PORT=11434 \
CENTRAL_BRAIN_OPENCLAW_MODEL=qwen3.5:27b-optimized \
CENTRAL_BRAIN_CLIENT2_SCENARIO=fatigue \
CENTRAL_BRAIN_CLIENT2_OPENCLAW_TIMEOUT_SECONDS=180 \
  tools/run_client2_central_brain_openclaw_development_test.sh
```

该命令在同一次构建中生成 SDK AAR、Runtime APK 和 Client2 bridge dex，然后按顺序安装两个 APK。不能拿历史
Client2 APK 与新 Runtime 混装；否则 Parcelable 字段或投影摘要可能被 Client2 SDK 判为
`SDK_PROJECTION_INVALID`。

如果 Windows ADB 不能从 WSL UNC 路径重复安装，但两个 APK 已通过原生 Windows 路径部署，
可以设置 `CENTRAL_BRAIN_SKIP_ANDROID_INSTALL=true`。该模式不是无条件跳过：runner 会读取
设备已安装 base APK，并分别与本地 Runtime/Client2 APK 计算 SHA-256；任一包缺失或哈希不一致
都会以 `PREINSTALLED_PACKAGE_MISSING` 或 `PREINSTALLED_APK_HASH_MISMATCH` 失败关闭。

只建立/检查桥接：

```bash
ANDROID_TRANSPORT_ID=<id> \
  tools/start_central_brain_wsl_openclaw_bridge.sh
```

开发 OpenClaw 是默认 debug 模型 profile。显式构建命令为：

```bash
CENTRAL_BRAIN_MODEL_GATEWAY_PROFILE=development_wsl_openclaw \
  tools/build_central_brain_android_runtime.sh
```

## 7. 通过条件

真机探针必须依次出现以下脱敏阶段：

- `socket_connected`
- `websocket_handshake_complete`
- `connect_challenge_received`
- `connect_authenticated`
- `chat_sent`
- `chat_acknowledged`
- `chat_final_received`
- `openclaw_inference_completed=true`
- `openclaw_development_probe_complete=true`
- `client2_orchestration_snapshot_projected=true ... model_projection_available=true`
- `client2_openclaw_development_test_complete=true`

Client2 回归还必须在 1920x1080 UI 树中看到 `RESULT / COMPLETED`、`centralBrainActuatorOverlay` 和
`VEHICLE BUS NOT ACCESSED`，证明真实模型结果已进入 HMI 仿真反馈，同时没有宣称车身执行。

同时必须保持：`raw_prompt_logged=false`、`raw_response_logged=false`、`credential_logged=false`、
`model_action_authority=false`、`vehicle_effect_dispatch_authorized=false`、`ethernet_validated=false`、
`production_ready=false`、`target_hardware_validated=false`。

## 8. 2026-07-22 实机证据

当前 Android 13/API 33 ARM64 设备已完成 ADB reverse、WebSocket v4 鉴权、`chat.send` ACK、真实 Ollama 模型终态和
严格结构化输出校验。观测模型延迟为 31968 ms，response envelope 为 197 bytes，HMI 回复为 32 字符。
未记录设备序列号、prompt、模型回复或 token。

同日 Client2 Cold 一键全链路回归也通过：Client2 与 Runtime/SDK 同源构建，真实模型调用耗时 30104 ms，编排投影包含
2 个模拟 Effect，左侧 HVAC 动画与底部温度显示更新为 28.0°C。该反馈明确标记
`VEHICLE BUS NOT ACCESSED`，没有调用车辆硬件。

该结果证明开发链路真实调用了 WSL OpenClaw/Ollama；不证明以太网、目标 `169.254.208.110`、direct NPU、
车辆 Effect、Driver/HAL 或量产发布完成。

## 8.1 2026-07-26 testboard 复测

Windows ADB server 位于 5038，设备别名为 `testboard`。WSL OpenClaw provider 临时适配当前
Ollama `http://127.0.0.1:11434` 和 `qwen3.5:27b-optimized` 后，Fatigue 全链路通过：
WebSocket v4 challenge/auth/chat ACK/final 完整，模型延迟 117383 ms，回复 envelope 195 bytes，
Graph revision 60，3 个白名单模拟 Effect 进入 Client2。UI 显示座椅 15° -> 30° 展开、
HVAC fan 3、媒体播放和 `VEHICLE BUS NOT ACCESSED`。

本机 `~/.openclaw/openclaw.json` 的 provider 修正属于环境配置，不提交仓库；仓库脚本只增加
`ADB_SERVER_PORT` 透传。该证据不改变冻结开发合同默认模型，更不改变量产 OpenClaw endpoint。

## 9. 常见失败

| 失败 | 检查 |
| --- | --- |
| `PROTOCOL_REJECTED` | OpenClaw 2026.7.1 需要开发 profile v4；确认 APK 不是旧包 |
| `AUTHENTICATION_REJECTED` | 检查 Gateway token 配置并重启 systemd service；不得打印 token |
| `missing scope: operator.write` | 确认开发连接是 `gateway-client/backend` 且不发送浏览器 Origin |
| `TRANSPORT_FAILURE` | 重新运行 bridge，检查 `adb reverse --list` 和设备侧 HTTP probe |
| `DEADLINE_EXCEEDED` | 检查 Ollama 模型是否已加载、GPU/内存状态和 120 秒预算 |
| `MODEL_OUTPUT_REJECTED` | 检查模型是否只返回固定 JSON shape、scenario 绑定和 action allowlist |
| `SDK_PROJECTION_INVALID` | 重新运行 Client2 全链路脚本，使 SDK、Runtime 和 Client2 bridge dex 来自同一次构建；不得放宽投影校验 |

## 10. 代码与合同

- Endpoint/profile：`OpenClawEndpointConfig`
- WebSocket/协议：`OpenClawInferenceEngine`
- Router/Provider：`DebugDecisionCompositionBoundary`
- 真机 probe：`OpenClawDevelopmentIntegrationProbeActivity`
- ADB bridge：`tools/start_central_brain_wsl_openclaw_bridge.sh`
- 一键 probe：`tools/run_central_brain_android_openclaw_development_probe.sh`
- Client2 全链路：`tools/run_client2_central_brain_openclaw_development_test.sh`
- 机器合同：`central_brain_android_openclaw_development_gateway_v1.json`
- CI 门禁：`tools/check_central_brain_android_openclaw_development_gateway.sh`

偏差记录为 `DEV-126`。目标 OpenClaw 服务的当前外部阻塞仍由 `ISSUE-054` 单独跟踪。

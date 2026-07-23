# Central Brain OpenClaw 多模态开发通道

状态：`P7-R5-MMDEV / CONTROLLED_FRAME_BOUND / ANDROID13_ARM64_VERIFIED / LIVE_CAMERA_OPEN`

Req IDs：`S2-MDL-001/002`、`S2-OBS-001/002`、`S2-SAF-001`、`XSC-001/005/006`、
`DEL-001/003/004/005`

## 1. 本增量目标

在既有文字场景输入之外，Client2 增加“处理一下”入口，将文字和一张固定摘要的受控座舱帧经
debug SDK/Binder、Runtime 编排和 Android OpenClaw 网关送入 WSL OpenClaw -> Ollama
`qwen3.6:27b`，再把实际模型回复和白名单动作投影回 HMI 模拟执行。实时相机、语音 history
与目标以太网仍由 `ISSUE-055` 跟踪。

## 2. 调用关系

```text
Client2 “处理一下” + 受控 PNG
    -> DevelopmentModelInput (metadata + ParcelFileDescriptor)
    -> DevelopmentModelProjectionService
    -> DevelopmentModelInputStore (owner/session/scenario bound, consume once)
    -> DebugDecisionCompositionBoundary
    -> CockpitModelPrompt (automotive context + action allowlist)
    -> OpenClawInferenceEngine
    -> chat.send.message + chat.send.attachments[0]
    -> ADB reverse / WSL OpenClaw v4
    -> Ollama qwen3.6:27b vision
    -> StructuredModelOutput
    -> Graph / Policy / simulated Effect / Readback
    -> DevelopmentModelProjection
    -> Client2 live trace + HVAC/Media animation
```

核心 `ModelContractV2` 仍只持有摘要，不携带原始语音文本或图片。图片字节只通过 debug FD 输入、
进程内一次消费 store、`OpenClawInferenceEngine` 有界暂存区和已鉴权 `chat.send` 帧。Client2、
SDK 和 Runtime 使用文字+图片 aggregate digest 绑定本次请求和输出。现有 OpenClaw 版本可能把超过
2 MB 的附件转存到 managed inbound media；量产 retention/cleanup owner 未配置，因此保持
`production_media_retention_configured=false`。

## 3. Android SDK/Binder 输入接口

```aidl
DevelopmentModelInputReceipt stageOwnMultimodalInput(
    in DevelopmentModelInput input);
```

`DevelopmentModelInput` 携带 schema、Session、Scenario、文字、MIME、文件名、字节数、SHA-256 和
`ParcelFileDescriptor`。服务端按声明长度读取、拒绝尾随字节，复核 MIME/魔数/SHA，并把输入绑定到
调用方 owner、Session 和 Scenario。原始字节不进入 Binder transaction、数据库、SharedPreferences
或日志；消费或淘汰时清零。

## 4. Android 网关接口

```java
registerScenarioImageAttachment(
    String inputDigest,
    String mimeType,
    String fileName,
    byte[] content)
```

约束：

- `inputDigest` 必须与同一场景 prompt / `InferenceRequest` 完全一致。
- 每个 digest 最多一张图片；允许 `image/png`、`image/jpeg`。
- 单图最大 6 MiB，总暂存图片最大 12 MiB。
- 文件名只能使用 1..96 个安全字符，不能带路径。
- MIME 与 PNG/JPEG 魔数不一致时失败关闭。
- 重复注册只有元数据和 SHA-256 完全一致时才幂等。
- 原始图片、prompt、模型回复和凭据均不得写入 Android 日志。

图片存在时，已鉴权 `chat.send` 增加：

```json
{
  "attachments": [
    {
      "type": "image",
      "mimeType": "image/png",
      "fileName": "cabin.png",
      "content": "<base64>"
    }
  ]
}
```

握手、`connect`、`chat.history` 和 `chat.abort` 仍受 64 KiB 出站上限约束。只有携带受控图片的已鉴权
`chat.send` 可使用 8.5 MB 上限。

## 5. WSL 前置条件

1. `openclaw-gateway.service` 为 active，Gateway 位于 `ws://127.0.0.1:18789/`。
2. Ollama 位于 `http://127.0.0.1:11435`，已安装 `qwen3.6:27b`。
3. `~/.openclaw/openclaw.json` 中该模型的 `input` 必须同时包含 `text` 和 `image`。
4. 测试图片位于 `central-brain/test-assets/multimodal/2025-SUV-OMS-cabin-photo.png`，SHA-256 必须为
   `93441797b96c512a7b87905e4d326fbacdbf3a80e4d336d018a41224a0cd8438`。

## 6. 执行与通过条件

```bash
tools/run_central_brain_wsl_openclaw_multimodal_probe.sh
```

脚本在同一 `chat.send` 中发送中文任务文字和 PNG。文字规定 JSON shape，图片提供事实。通过条件：

- `text_present=true`、`image_present=true`；
- `occupant_count=3`；
- `visible_occupant_holding_bottle=true`；
- `all_visible_occupants_belted=true`；
- `multimodal_probe_complete=true`；
- 不输出 Base64、完整 prompt、完整模型回复或 Gateway credential。

2026-07-23 纳入 Git 后的正式探针实测耗时 8087 ms。该结果证明受控图片进入真实视觉模型并与文字联合解析；它不证明 OMS
座位定位、身份识别、目标 NPU、目标以太网或量产精度。

真实 Android 13 ARM64 Client2 闭环使用：

```bash
source env.sh
ANDROID_SERIAL=testboard \
CENTRAL_BRAIN_CLIENT2_SCENARIO=multimodal \
CENTRAL_BRAIN_CLIENT2_OPENCLAW_TIMEOUT_SECONDS=240 \
tools/run_client2_central_brain_openclaw_development_test.sh
```

通过条件还包括 `image_present=true`、固定字节数和 SHA、`image_consumed=true`、模型终态、
`AGENT ACTIONS / ALLOWLISTED`、Graph/Effect/Readback 完成、UI 模拟执行以及
`vehicle_effect_hardware_accessed=false`。

## 7. 开放项和边界

- `ISSUE-055`：接入实时相机 owner、语音 history 附件绑定、目标以太网和量产媒体治理。
- 当前测试图片作为受控测试资产进入 Git；门禁固定校验路径、2,244,206 字节大小和 SHA-256。
- 受控帧 SDK/Binder 与 Android 13 ARM64 开发链路已经完成；不得将其表述为实时 OMS 摄像头能力。
- 图片模型结果仍只是候选信息，不能直接授权 Safety 或车辆 Effect。
- 不访问 Vehicle/VHAL/CAN、Driver/HAL 或 direct NPU。
- 保持 `production_ready=false`、`target_hardware_validated=false`。

机器合同：`central-brain/contracts/central_brain_android_openclaw_multimodal_gateway_v1.json`。
静态门禁：`tools/check_central_brain_android_openclaw_multimodal_gateway.sh`。

偏差：`DEV-127`。开放项：`ISSUE-055`。

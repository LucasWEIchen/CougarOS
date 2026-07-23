# Central Brain OpenClaw 多模态开发通道

状态：`P7-R5-MMDEV / WSL_MODEL_VERIFIED / FRONTEND_BINDING_OPEN`

Req IDs：`S2-MDL-001/002`、`S2-OBS-001/002`、`S2-SAF-001`、`XSC-001/005/006`、
`DEL-001/003/004/005`

## 1. 本增量目标

在既有文字场景输入之外，为 Android 调试模型网关增加单张图片附件能力，并先使用受控座舱图片验证
WSL OpenClaw -> Ollama `qwen3.6:27b` 的真实文字+图片联合推理。该增量只打通模型网关层；前端相机采集、
语音转写与图片的 Binder 传输合同由 `ISSUE-055` 后续接入。

## 2. 调用关系

```text
前端语音转写（下一增量） ---- text digest ----+
                                                |
前端相机帧（下一增量） ------ image bytes -----+-> Android OpenClawInferenceEngine
                                                    -> chat.send.message
                                                    -> chat.send.attachments[0]
                                                    -> ADB reverse / WSL OpenClaw v4
                                                    -> Ollama qwen3.6:27b vision
                                                    -> structured candidate result
                                                    -> existing action allowlist
```

核心 `ModelContractV2` 仍只持有摘要，不携带原始语音文本或图片。图片字节只进入 debug
`OpenClawInferenceEngine` 的有界暂存区和已鉴权 `chat.send` 帧。现有 OpenClaw 版本可能把超过 2 MB 的附件转存到
managed inbound media；量产 retention/cleanup owner 未配置，因此保持 `production_media_retention_configured=false`。

## 3. Android 网关接口

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

## 4. WSL 前置条件

1. `openclaw-gateway.service` 为 active，Gateway 位于 `ws://127.0.0.1:18789/`。
2. Ollama 位于 `http://127.0.0.1:11435`，已安装 `qwen3.6:27b`。
3. `~/.openclaw/openclaw.json` 中该模型的 `input` 必须同时包含 `text` 和 `image`。
4. 测试图片位于 `central-brain/test-assets/multimodal/2025-SUV-OMS-cabin-photo.png`，SHA-256 必须为
   `93441797b96c512a7b87905e4d326fbacdbf3a80e4d336d018a41224a0cd8438`。

## 5. 执行与通过条件

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

## 6. 开放项和边界

- `ISSUE-055`：定义前端语音转写+相机帧的版本化 SDK/Binder 合同，并在 Android 13 ARM64 上实测。
- 当前测试图片作为受控测试资产进入 Git；门禁固定校验路径、2,244,206 字节大小和 SHA-256。
- 图片模型结果仍只是候选信息，不能直接授权 Safety 或车辆 Effect。
- 不访问 Vehicle/VHAL/CAN、Driver/HAL 或 direct NPU。
- 保持 `production_ready=false`、`target_hardware_validated=false`。

机器合同：`central-brain/contracts/central_brain_android_openclaw_multimodal_gateway_v1.json`。
静态门禁：`tools/check_central_brain_android_openclaw_multimodal_gateway.sh`。

偏差：`DEV-127`。开放项：`ISSUE-055`。

# 吸烟检测 Agent 100 张阳性图片评测

## 1. 评测目标

验证显式场景入口是否稳定路由到专用吸烟检测 Agent，并统计 100 张已标注为“存在吸烟场景”的图片上的正样本命中率、结构化输出有效率和串行推理耗时。

本数据集不包含阴性样本，因此本文的 98% 仅表示正类别召回率（positive sample hit rate），不能表示包含误报能力的完整准确率，也不能计算 precision、specificity 或 false-positive rate。

## 2. 数据预处理

- 输入目录：`passenger_smoking_100/`
- 图片数量：100
- 目标分辨率：精确 `1920x1080`
- 处理方法：应用 EXIF 方向，使用 Lanczos 等比例缩放，将完整画面居中放入黑色 `1920x1080` 画布；不拉伸、不裁切
- JPEG 参数：quality 95、4:4:4 色度采样、优化编码
- 转换后总大小：45,020,285 bytes
- 唯一 SHA-256 数量：100
- 校验结果：100 张均可解码、尺寸完全一致、无遗留临时文件

原始图片已备份到工作机的 `/home/normad400/backups/appDev/passenger_smoking_100-pre-1080p-20260820.tar.gz`，归档 SHA-256 为 `6f80b6f2767a3aa499e43615aa39504c280b2e5b270e6a18386849b670c6b8c7`。该备份不属于仓库交付物。

## 3. Agent 路由与模型参数

| 项目 | 值 |
| --- | --- |
| 激活来源 | `EXPLICIT_SCENARIO` |
| 分诊 Agent | `agent.cabin.compliance-triage.v1` |
| 专用 Agent | `agent.cabin.smoking-detection.v1` |
| 场景 ID | `scene.cabin.compliance.smoking.v1` |
| 路由摘要 | `096e1a54356dc4fc4c38cfcfcb3e439137f6a350bc75977556b9f65681486796` |
| 模型 | `Qwen3.5-9B-AWQ` |
| 接口 | OpenAI-compatible `/v1/chat/completions` |
| 温度 | 0 |
| 流式输出 | false |
| 最大输出 token | 64 |

该入口由场景 ID 确定性激活，不由模型自由选择 Agent。输出必须符合五字段严格 JSON Schema。吸烟场景的 `max_tokens` 从通用值 192 收紧到 64；本轮实际 completion token 最大值为 37，未出现截断。

## 4. 结果

| 指标 | 结果 |
| --- | ---: |
| 运行次数 | 100 |
| 检测到吸烟 | 98 |
| 不确定 | 2 |
| 判定为无吸烟 | 0 |
| 契约错误 | 0 |
| 传输错误 | 0 |
| 正样本命中率 / 正类别召回率 | 98.00% |
| 严格结构化输出有效率 | 100.00% |
| 截断响应 | 0 |

未命中样本为 `016.jpg` 和 `096.jpg`。两次响应均为规范化的不确定结果：`confidence=0.45`、`location=UNKNOWN`。两张图中均能看到手持香烟，但没有明显烟雾，后续应作为困难样本用于提示词或视觉模型回归，不应修改标签来抬高结果。

## 5. 耗时

以下为单请求串行执行结果，不含并发吞吐优化：

| 指标 | 模型请求 | 端到端 |
| --- | ---: | ---: |
| 最小值 | 5,331.709 ms | 5,337.644 ms |
| 平均值 | 5,608.263 ms | 5,613.417 ms |
| P50 | 5,581.888 ms | 5,587.130 ms |
| P90 | 5,779.345 ms | 5,784.602 ms |
| P95 | 5,877.606 ms | 5,882.826 ms |
| P99 | 6,126.328 ms | 6,131.631 ms |
| 最大值 | 6,132.918 ms | 6,137.452 ms |

- 100 张总墙钟时间：561,368.105 ms（约 9 分 21 秒）
- 吞吐：10.688 张/分钟
- completion token 平均值：35.04
- completion token 最大值：37

## 6. 复现

先建立 TY1100 vLLM 的 WSL 本地桥接，再执行：

```bash
python3 tools/evaluate_central_brain_smoking_dataset.py \
  --dataset passenger_smoking_100 \
  --output-dir outputs/evaluation/smoking-detection/<run-id> \
  --max-tokens 64
```

评测工具在发送任何模型请求前强制校验所有图片必须为精确 `1920x1080`。逐样本结果见 [cases CSV](results/2026-08-20-positive-1080p-cases.csv)，机器可读汇总见 [summary JSON](results/2026-08-20-positive-1080p-summary.json)。

## 7. 证据边界

本轮通过 WSL 桥接直接访问 TY1100 vLLM，覆盖图片编码、Agent 提示词、OpenAI-compatible 请求、严格结果解析和路由标识。它不包含 Android HMI、Binder、真实摄像头采集、车辆总线或执行器，不能作为目标硬件或量产验收证据。

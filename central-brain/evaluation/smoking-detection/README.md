# 吸烟检测 Agent 100 张阳性图片评测与快速通道优化

## 1. 评测目标

验证显式场景入口是否稳定路由到专用吸烟检测 Agent，并统计 100 张已标注为“存在吸烟场景”的图片上的正样本命中率、结构化输出有效率和串行推理耗时。

本数据集不包含阴性样本，因此本文的 98%/99% 仅表示正类别召回率（positive sample hit rate），不能表示包含误报能力的完整准确率，也不能计算 precision、specificity 或 false-positive rate。

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
| 基线最大输出 token | 64 |
| 快速通道最大输出 token | 24 |
| 回退通道最大输出 token | 64 |
| 当前模型 thinking 配置 | 请求级显式关闭 |

该入口由场景 ID 确定性激活，不由模型自由选择 Agent。对外输出仍必须符合五字段严格 JSON Schema。当前 Android Provider 和两个评测工具均显式发送 `chat_template_kwargs.enable_thinking=false`，不依赖服务端默认值。旧基线实测时服务端默认关闭 thinking，但请求体尚未显式声明；优化版实测及此后的实现采用请求级显式关闭。

优化后的 Provider 采用两阶段策略：

1. 将原始 `1920x1080` 图片等比例缩放到不超过 `1280x720`，编码为 quality 85 的 JPEG。
2. 快速通道只允许输出四整数数组 `[status,count,location_code,confidence_percent]`，最多 24 token。
3. 仅当结果为“检测到吸烟”且置信度不低于 0.80 时直接接受，并由 Android Provider 投影为原有五字段结果。
4. 未检测到、不确定或低置信度结果均使用原始 `1920x1080` 图片和五字段契约执行一次回退，最多 64 token。

该策略避免低分辨率阴性结论直接进入业务层。代价是实际阴性场景会执行两次模型请求，因此必须补充阴性和困难样本集后才能评价整体延迟及误报率。

## 4. 基线结果

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

## 5. 优化结果

| 指标 | `1920x1080` 单阶段基线 | 720p 快速通道 + 原图回退 | 变化 |
| --- | ---: | ---: | ---: |
| 检测到吸烟 | 98/100 | 99/100 | +1 张 |
| 不确定 | 2/100 | 1/100 | -1 张 |
| 严格结构化输出有效率 | 100.00% | 100.00% | 不变 |
| 回退率 | 不适用 | 1.00% | 1 张 |
| 端到端平均耗时 | 5,613.417 ms | 2,438.297 ms | 降低 56.56% |
| 端到端 P95 | 5,882.826 ms | 2,615.207 ms | 降低 55.55% |
| 端到端 P99 | 6,131.631 ms | 2,840.329 ms | 降低 53.68% |
| 最大耗时 | 6,137.452 ms | 7,847.007 ms | 回退样本增加 27.86% |
| 吞吐 | 10.688 张/分钟 | 24.607 张/分钟 | 提升 130.23% |
| completion token 平均值 | 35.04 | 14.26 | 降低 59.30% |

优化后 `016.jpg` 在快速通道中检测成功。`096.jpg` 触发原图回退，最终仍为规范化的不确定结果。该样本的两阶段端到端耗时为 7,847.007 ms，说明回退保护提高了困难样本成本，但没有篡改模型判断来抬高结果。

优化后预处理平均耗时为 30.683 ms，快速模型请求平均耗时为 2,350.247 ms。100 张总墙钟时间为 243,835.004 ms（约 4 分 4 秒）。

## 6. 基线耗时明细

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

## 7. 复现

先建立 TY1100 vLLM 的 WSL 本地桥接，再执行：

```bash
python3 tools/evaluate_central_brain_smoking_dataset.py \
  --dataset passenger_smoking_100 \
  --output-dir outputs/evaluation/smoking-detection/<run-id> \
  --max-tokens 64
```

快速通道与原图回退复现命令：

```bash
python3 tools/evaluate_central_brain_smoking_fast_fallback.py \
  --dataset passenger_smoking_100 \
  --output-dir outputs/evaluation/smoking-detection/<run-id>
```

两个评测工具都会在发送模型请求前强制校验所有源图片必须为精确 `1920x1080`。

- 基线逐样本结果：[cases CSV](results/2026-08-20-positive-1080p-cases.csv)
- 基线机器汇总：[summary JSON](results/2026-08-20-positive-1080p-summary.json)
- 优化版逐样本结果：[cases CSV](results/2026-08-20-fast720-compact-fallback-cases.csv)
- 优化版机器汇总：[summary JSON](results/2026-08-20-fast720-compact-fallback-summary.json)

## 8. 证据边界

本轮通过 WSL 桥接直接访问 TY1100 vLLM，覆盖图片编码、Agent 提示词、OpenAI-compatible 请求、thinking 关闭参数、严格结果解析、原图回退和路由标识。Android 侧相同策略由单元测试和编译验证覆盖，但本轮实测耗时不包含 Android Bitmap 预处理、HMI、Binder、真实摄像头采集、车辆总线或执行器，不能作为目标硬件或量产验收证据。

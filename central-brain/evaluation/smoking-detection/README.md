# 吸烟检测 Agent 正反例评测、快速通道与置信度证据

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

## 9. 正反例数据准备

新增反例集 `passenger_unbelted_nonsmoking_100/`，共 100 张确定为无吸烟场景的 JPEG。原图均为
`1448x1086`；归一化前已在仓库外备份为
`passenger_unbelted_nonsmoking_100-pre-1080p-20260821.tar.gz`，SHA-256 为
`922c1501de0e1b21385bed77bc2c7101890b3d89800da6dfb0431bed7bbda889`。

归一化使用与正例相同的方法：EXIF 方向校正、Lanczos 等比例缩放、黑色画布居中、不裁切、不拉伸，
最终以 quality 95、4:4:4 JPEG 覆盖原文件。处理后：

| 数据 | 数量 | 分辨率 | 唯一 SHA-256 | 平均字节 | 平均亮度 |
| --- | ---: | --- | ---: | ---: | ---: |
| 吸烟正例 | 100 | 1920x1080 | 100 | 450,202.850 | 62.666 |
| 无吸烟反例（处理前） | 100 | 1448x1086 | 100 | 189,430.250 | 85.124 |
| 无吸烟反例（处理后） | 100 | 1920x1080 | 100 | 443,433.740 | 63.875 |

两类数据没有类内或跨类完全重复。每个相同编号的正反例被视为同一 `group_id`，按固定 salt 的哈希排序
形成 70 组校准数据（140 张）和 30 组独立测试数据（60 张），同组样本不会跨 split。

该数据仍存在明确混杂：反例集同时标注为未系安全带，因此 smoking label 与 seatbelt state 并非独立变量；
两类原始图片的分辨率、压缩、亮度和生成分布也不同。它只能用于试点评测，不可替代四象限数据
（吸烟/不吸烟 × 系/不系安全带）、目标摄像头数据或量产分布。

- [成对清单 CSV](datasets/pilot-v1/confidence-pilot-manifest.csv)
- [数据质量 JSON](datasets/pilot-v1/confidence-pilot-data-quality.json)
- 数据准备工具：`tools/prepare_central_brain_smoking_confidence_dataset.py`

## 10. 置信度采集方法

快速通道请求保持 `temperature=0`、`max_tokens=24`、`enable_thinking=false`，并增加
`logprobs=true`、`top_logprobs=5`。工具严格定位数组第一个 `status` token，只接受 `0/1/2` 三个候选
均存在且有限的响应，再对三个对数概率做归一化。末尾只允许 vLLM 的单个 `<|im_end|>` 控制 token；任何
其他不可见后缀、缺失候选或无法重构的内容均失败关闭。

评测同时保存两类不同信号：

1. `self_confidence`：模型在数组第四项主动生成的置信度百分比。
2. `selected_status_probability`：状态 token 在 `0/1/2` 三个候选中的归一化概率。

两者都不是天然的真实正确率。后者通常比固定的自报 0.95/1.00 有更多区分度，但仍受提示词、模型、
Schema 和约束解码影响，必须通过独立标注数据校准后才能解释为概率。

本轮还发现原快速 Schema 只分别限制四个整数范围，允许模型生成 `[0,0,1,95]` 这类“未吸烟但保留位置”
的语义非法数组。本次将 Python 和 Android Provider 同步改为三个互斥 `oneOf` 分支：

```text
[0,0,0,50..100]
[1,1..2,1..4,50..100]
[2,0,0,0..49]
```

Android `SmokingDetectionResult.parseCompactWire()` 的本地二次校验继续保留。

## 11. 200 张平衡评测结果

| 指标 | 校准 split（140） | 独立测试 split（60） |
| --- | ---: | ---: |
| 严格有效输出 | 140/140 | 60/60 |
| 真阳性 | 70 | 29 |
| 真阴性 | 70 | 30 |
| 假阳性 | 0 | 0 |
| 假阴性 | 0 | 1 |
| 不确定 | 0 | 0 |
| 终态准确率 | 100.00% | 98.33% |
| 正类召回率 | 100.00% | 96.67% |
| 特异度 | 100.00% | 100.00% |
| Precision | 100.00% | 100.00% |

唯一错误是独立测试样本 `096-positive`：模型输出未吸烟，自报置信度为 `1.00`，但所选状态 token 概率仅为
`0.444483`。这直接证明自报置信度不能视为真实正确率，也表明 token 分布能够暴露部分自报字段掩盖的
犹豫。其他低 token 概率样本包括 `077-negative=0.574596`、`017-negative=0.642230` 和
`097-negative=0.657979`，可作为后续困难样本扩展的种子。

本轮 200 次串行请求的平均端到端耗时为 2,626.383 ms，P95 为 2,937.615 ms，最大值为
3,433.475 ms；总墙钟时间为 525,288.497 ms。

## 12. 校准器结论

工具实现了带 L2 正则的逻辑校准器，目标是估计 `P_AUTOMATIC_DECISION_CORRECT`，候选特征为：

- 自报置信度的 logit；
- 所选状态相对最强其他状态的 logprob margin；
- 当前状态是否为阳性。

校准器只使用 calibration split 拟合，test split 只用于独立验收。启用门槛要求：校准数据至少有 30 个
终态决策且同时包含正确/错误结果；独立测试必须包含错误；校准后 Brier 和 10-bin ECE 必须同时改善；
全部响应必须严格有效。

本轮 calibration split 的 140 个决策全部正确，缺少“错误”目标类别，因而无法拟合逻辑校准器。工具按
设计输出 `PILOT_NOT_DEPLOYABLE`，没有生成系数，也没有修改 Android 接受阈值。独立测试中虽有 1 个错误，
但不能为了拟合而在观察结果后把它移入 calibration split，否则会产生数据泄漏和乐观偏差。

下一轮至少需要新增成组困难样本，并覆盖吸烟/不吸烟与安全带状态的四象限、遮挡、小目标、无烟雾、
相似手持物、低照度、运动模糊和目标摄像头视角。只有校准分组和独立测试分组都包含足够错误后，才可判断
logprob 特征能否稳定改善 Brier/ECE，并考虑版本化接入 Android。

## 13. 复现与结果文件

```bash
python3 tools/prepare_central_brain_smoking_confidence_dataset.py \
  --positive-dir passenger_smoking_100 \
  --negative-dir passenger_unbelted_nonsmoking_100 \
  --output-dir central-brain/evaluation/smoking-detection/datasets/pilot-v1 \
  --backup /path/outside/repository/passenger_unbelted_nonsmoking_100-pre-1080p.tar.gz \
  --normalize-negative-in-place \
  --calibration-groups 70

python3 tools/evaluate_central_brain_smoking_confidence.py \
  --manifest central-brain/evaluation/smoking-detection/datasets/pilot-v1/confidence-pilot-manifest.csv \
  --positive-dir passenger_smoking_100 \
  --negative-dir passenger_unbelted_nonsmoking_100 \
  --output-dir outputs/evaluation/smoking-detection/confidence-pilot
```

- [逐样本 cases CSV](results/2026-08-21-balanced-confidence-pilot/cases.csv)
- [机器汇总 summary JSON](results/2026-08-21-balanced-confidence-pilot/summary.json)
- [校准器状态 calibrator JSON](results/2026-08-21-balanced-confidence-pilot/calibrator.json)
- 置信度评测工具：`tools/evaluate_central_brain_smoking_confidence.py`

本节证据来自唯一原型模型环境，不包含 Android UI、Binder、真实摄像头、生产以太网、车辆通信或目标硬件
验收。`production_ready=false`、`target_hardware_validated=false`、`calibrator_deployment_ready=false`。

## 14. Qwen3.5-2B-AWQ 小模型候选测试

### 14.1 模型来源与运行方式

候选模型从 ModelScope 的 `tclf90/Qwen3.5-2B-AWQ` 获取，固定源码提交为
`9ed8d11f8742918db7c7946ff592db1a66f67a31`。模型为
`Qwen3_5ForConditionalGeneration`，模型仓库声明 `Apache-2.0` 许可，权重采用 4-bit AWQ、
group size 128；两片权重的大小和 SHA-256
记录在 [模型来源清单](qwen35-2b-awq-model-source.json) 中。模型权重约 3.1 GB，不提交到 CougarOS
仓库，只提交可复核的来源、提交号、大小与哈希。

本次在同一台 TY1100-NX-PRO 上临时停止 9B 基线容器，以 vLLM 0.17.0 的 AWQ-Marlin 内核启动
2B 候选。候选固定使用 `127.0.0.1:10031`，9B 基线继续固定使用 `127.0.0.1:10030`；评测工具按
profile 同时锁定端口与模型 ID，禁止将候选结果误记到基线。测试结束后已删除候选容器，并恢复 9B
容器及 `restart=always` 策略。生产配置未修改。

### 14.2 同条件对比结果

两次评测使用同一 TY1100、同一 200 张成对清单、同一图像预处理、Agent 指令、条件 Schema、
`temperature=0`、请求级关闭 thinking 和串行请求。逐样本 `status`、预测标签、正确性与失败码均无差异；
两个模型都只在独立测试集的 `096-positive` 上产生 1 个假阴性。

| 指标 | 9B AWQ 基线 | 2B AWQ 候选 | 2B 相对变化 |
| --- | ---: | ---: | ---: |
| 独立测试准确率 | 98.33% | 98.33% | 相同 |
| 正类召回率 | 96.67% | 96.67% | 相同 |
| 特异度 | 100.00% | 100.00% | 相同 |
| 端到端平均耗时 | 2,626.383 ms | 1,346.056 ms | 降低 48.75% |
| 端到端 P50 | 2,613.721 ms | 1,243.867 ms | 降低 52.41% |
| 端到端 P95 | 2,937.615 ms | 2,208.778 ms | 降低 24.81% |
| 端到端 P99 | 3,067.828 ms | 2,418.779 ms | 降低 21.16% |
| 总墙钟时间 | 525,288.497 ms | 269,220.725 ms | 降低 48.75% |
| 串行吞吐 | 22.845 张/分钟 | 44.573 张/分钟 | 1.951 倍 |

候选服务就绪后的首次真实请求耗时为 `32,388.244 ms`，紧接的同样本热请求为 `1,072.263 ms`，
端到端为 `1,105.404 ms`。200 张对比不包含该冷请求，且没有同条件 9B 冷启动数据，因此只能把冷启动
记录为风险，不能据此比较两模型冷启动性能。

2B 在 200 次响应中自报置信度全部为 `1.0`，包括错误样本。这比 9B 更明确地说明自报值不是正确率。
校准 split 仍然没有错误样本，校准器未拟合且不可部署。

### 14.3 复现和证据边界

候选服务只允许通过固定管理脚本临时启动，并必须在测试后恢复基线：

```bash
bash tools/manage_central_brain_ty1100_qwen35_2b_benchmark.sh start
python3 tools/evaluate_central_brain_smoking_confidence.py \
  --model-profile qwen35-2b-awq \
  --manifest central-brain/evaluation/smoking-detection/datasets/pilot-v1/confidence-pilot-manifest.csv \
  --positive-dir passenger_smoking_100 \
  --negative-dir passenger_unbelted_nonsmoking_100 \
  --output-dir outputs/evaluation/smoking-detection/qwen35-2b-awq
bash tools/manage_central_brain_ty1100_qwen35_2b_benchmark.sh restore
```

- [2B 逐样本 cases CSV](results/2026-08-22-qwen35-2b-awq-balanced-confidence-pilot/cases.csv)
- [2B 汇总 summary JSON](results/2026-08-22-qwen35-2b-awq-balanced-confidence-pilot/summary.json)
- [2B 校准器状态 calibrator JSON](results/2026-08-22-qwen35-2b-awq-balanced-confidence-pilot/calibrator.json)
- [2B 与 9B 机器可读对比](results/2026-08-22-qwen35-2b-awq-balanced-confidence-pilot/comparison-to-qwen35-9b-awq.json)

结论仅适用于当前固定数据和 TY1100 原型链路。数据仍存在安全带状态与类别相关的已知混杂，未覆盖目标摄像头、
Android 实时采集、并发、长稳、生产以太网或量产硬件。2B 当前是性能候选，不替换 Android 默认模型，
`production_ready=false`、`target_hardware_validated=false`。

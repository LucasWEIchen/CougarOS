# Central Brain Python 原型退役说明

版本：1.0

日期：2026-07-16

状态：已执行

## 1. 决策

自 2026-07-16 起，仓库不再维护 Python 语义网关、REST mock、Ollama 仿真 NPU、
Linux Python binding/CLI/systemd 样例和旧 Android Console。后续开发只以黑盒 Android 13
座舱控制器上的 Java/AIDL/C 实际工程为主线。

本次变更覆盖 Req ID：`APP-004`、`XSC-001`、`XSC-004`、`XSC-005`、`XSC-006`、
`NV-F-001`、`NV-F-011`、`NV-F-012`、`NV-G-003`、`NV-G-005`、`NV-G-006`、
`NV-G-007`、`NV-P-002`、`KH-003`、`KH-006`、`DEL-001`、`DEL-003`、`DEL-004`、
`DEL-005`。范围偏差登记为 `DEV-026`，退役风险登记为 `ISSUE-032`。

Git 历史保留已删除实现用于审计，但任何当前构建、测试、文档门禁或交付包都不得依赖它。

## 2. 已删除范围

| 类别 | 退役内容 | 原因 |
| --- | --- | --- |
| Python Runtime | semantic gateway、AI SDK/Agent mock、Governance、Vehicle Signal、hardware registry | 不进入 Android 实机进程，且与 Java Runtime 重复 |
| 模型仿真 | Python Ollama adapter、mock NPU HTTP endpoint | 不能作为实体 NPU、Driver/HAL 或性能证据 |
| Protocol Binding 样例 | 第一阶段 JSON Binder、Linux UDS/JSON-RPC/proto | 已由 typed Android AIDL/Binder 主路径替代 |
| Linux 样例 | CLI、daemon、systemd、package profile、smoke | 当前正式范围仅为 Android 13 硬件 |
| Android Console | 代理 REST gateway 的旧 debug APK | 已由 Runtime APK、Demo HMI 和 Client2 替代 |
| 原型合同 | prototype API、handoff/completion/closure JSON | 仅描述被退役运行时，不再是交付基线 |
| 原型文档/门禁 | usage、module map、closure、旧详设和对应 smoke/check | 防止 CI 继续强制维护已退役代码 |

旧产品设计、旧需求拆解和旧按图执行计划也已删除。它们的有效内容已经进入 Stage 2 产品 UX、
开发 backlog、完整软件开发设计和强制 Req ID 基线。

## 3. 必须保留的 Android 与硬件资产

| 资产 | 当前路径 | 保留目的 |
| --- | --- | --- |
| Java SDK / typed AIDL | `central-brain/android-runtime/central-brain-sdk` | 应用到 Runtime 的正式 IPC 合同 |
| Runtime/Governance/Diagnostics | `central-brain/android-runtime/runtime-service` | Android 实机中央大脑进程 |
| Native C ABI/JNI | `central-brain/android-runtime/native-runtime` | Vendor SDK/NPU 的窄原生接入边界 |
| Model contract | `runtime-service/.../model/ModelProvider.java` | 大模型/NPU provider 生命周期合同 |
| Resource scheduler | `runtime-service/.../scheduler/InferenceResourceScheduler.java` | 模型资源、deadline、quota 和 cancel 合同 |
| Vendor NPU empty profile | `runtime-service/.../model/ModelProviderProfiles.java` | 未取得 vendor SDK 前失败关闭 |
| Effect/vehicle empty gate | `runtime-service/.../effects` | 未取得车辆 API 前禁止真实副作用 |
| Client2 实机 HMI | `apk-labs/client2-central-brain` | typed Binder 的座舱 UI 验收 |
| Hybrid delivery | `central-brain/delivery/android-hybrid` | APK/AAR/hash/signer/ABI 交付 |
| NPU 接口设计 | `docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md` | PCIe NPU 和 vendor runtime 接入合同 |
| Driver/HAL 矩阵 | `docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md` | 只在确认能力缺口后触发最小开发量 |
| 虚拟化/Safety 约束 | `docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md` | 记录外部边界；仍不开发虚拟化 |

这些资产的保留不代表真实 NPU/VHAL/Driver/HAL 已接入。当前仍保持
`production_ready=false`、`target_hardware_validated=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。

## 4. Python 使用政策

1. `central-brain/` 下不得再出现 `.py` 运行时文件。
2. Python 只允许作为宿主侧确定性构建工具，例如 Android 交付清单生成和 Client2 静态 patch。
3. Python 工具不得承载 AIOS 服务、Agent、模型推理、车辆状态、治理或协议 binding。
4. Android `debug`/`test` source set 中的 deterministic provider 和 test double 允许保留；它们不进入
   production 激活路径，也不得宣称真实 NPU 或车辆控制。
5. 后续如需连接本机 Ollama，应实现为 Android `ModelProvider` 的显式开发 profile，而不是恢复
   Python gateway。该 profile 必须有超时、取消、输出校验、网络/隐私和 production 禁用门禁。
6. Linux 前端若未来恢复，必须使用新的非 Python 正式工作包，并重新建立 Android/Linux contract
   parity；不得复活已删除样例。

## 5. 替换关系

| 旧能力 | 当前权威替代 |
| --- | --- |
| JSON/REST Agent task | `ICentralBrainRuntime` + `CentralBrainClient` |
| Python Policy/Governance | `ICentralBrainGovernance` + Android capability/policy/middleware |
| Python audit/readiness | Room audit + protected Diagnostics Binder/log/dumpsys snapshots |
| Python Ollama simulated NPU | Android `ModelProvider` contract；当前 Vendor provider 为空 |
| Python hardware interface registry | NPU/Driver-HAL 文档 + Android empty provider/activation gate |
| Android Console | Demo HMI + Client2 typed Binder panel |
| Linux CLI/daemon | 当前无替代产物；正式范围外 |

## 6. 验证

```bash
bash tools/check_central_brain_python_prototype_retirement.sh
bash tools/check_central_brain_npu_interface.sh
bash tools/check_central_brain_virtualization_docs.sh
bash tools/check_central_brain_android_runtime_evolution.sh
```

退役门禁验证旧目录/合同/脚本不存在、`central-brain/` 下没有 Python、当前文档不再把 Python
原型列为运行路径，并确认 Android Model/NPU/C ABI/Driver-HAL 保留资产仍存在。

## 7. 后续起点

清理完成后继续 Stage 2 `P1-W01 Session DTO/AIDL`。所有新功能只进入 Android Java/AIDL/C
主线；依赖真实车辆服务、Vendor NPU 或 Driver/HAL 的部分继续保持 empty interface，直到
`S2-ADP-002`、`ISSUE-022`、`ISSUE-024`、`ISSUE-027`、`ISSUE-030` 的 owner/evidence 条件满足。

# Central Brain P9-W02 Stability and Fault Matrix

版本：1.0

日期：2026-07-18

状态：software contract verified / target 72h run pending

## 1. 目的和边界

P9-W02 冻结 Android 13 应用层的长稳 workload、故障矩阵、恢复预算、聚合规则和证据模式。当前交付是
`software_contract_target_72h_pending`：它不启动场景、不杀进程、不制造磁盘或网络故障，也不读取系统、车辆或 NPU 状态。

Req IDs：`S2-REL-001`、`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006`、
`DEL-001/004/005`。tracking：`DEV-087`、`ISSUE-049`。

## 2. 交付

- `central_brain_android_p9_stability_fault_matrix.json`：三 workload、六 fault、18-case matrix、evidence mode 和 claim state。
- `StabilityFaultMatrixContract.java`：固定 matrix、strict observation/context、fail-closed report 和 canonical digest。
- `StabilityFaultMatrixContractTest.java`：交叉积、缺项/重复、样本/时长、crash/ANR/invariant、outcome/recovery 和 authority 边界。
- `StabilityFaultMatrixProbeActivity.java`：DUMP-protected debug-only 合成合同探针，不运行 soak 或注入故障。
- `check_central_brain_android_stability_fault_matrix.sh`：验证 JSON/Java 同源、release 隔离、禁止平台读取和文档追踪。

## 3. Workload

| Workload | 目的 | 当前 Runtime 执行 |
| --- | --- | --- |
| `scene.comfort.cold.v1` | 冷感场景循环合同 | 未接 |
| `scene.fatigue.assist.v1` | 疲劳场景循环合同 | 未接 |
| `scene.rest.nap.v1` | 小憩场景循环合同 | 未接 |

每个 workload 必须覆盖六种 fault，形成固定 18-case 交叉积。合同不会用 UI 点击或 test double 结果替代真实 Session/Graph/Effect 路径。

## 4. Fault matrix

| Fault | 期望结果 | 最大恢复时间 | 量产注入器状态 |
| --- | --- | ---: | --- |
| `none` | `COMPLETED` | 0 ms | 未接 |
| `adapter_death` | `RECOVERED` | 5,000 ms | 未接 |
| `runtime_restart` | `RECOVERED` | 30,000 ms | 未接 |
| `storage_pressure` | `FAIL_CLOSED` | 5,000 ms | 未接 |
| `callback_churn` | `RECOVERED` | 5,000 ms | 未接 |
| `network_loss` | `DEGRADED` | 5,000 ms | 未接 |

`network_loss` 只验证云/网络依赖不可用时的降级合同；它不得影响本地车辆安全策略，也不得证明当前代码已经访问网络。
`storage_pressure` 必须失败关闭，禁止丢失 owner、approval、effect/outbox 或 audit 的一致性边界。

## 5. Evidence mode

| Mode | 每 case 最少迭代 | 最短持续时间 | 可否作为目标候选 |
| --- | ---: | ---: | --- |
| `CONTRACT_TEST` | 1 | 0 | 否 |
| `ANDROID_APPLICATION` | 30 | 60 s | 否 |
| `TARGET_ANDROID13_72H` | 30 | 259,200,000 ms | 仅结构完整候选 |

目标模式还必须绑定 release tag、40 位 source commit、非敏感 device alias、SHA-256 evidence digest 和 owner approval。
即使 18 case 和 72 小时全部通过，`isTargetHardwareQualified()` 仍固定 false，必须由独立 Release/OEM 安全验收提升资格。

## 6. 通过和失败规则

每个 observation 只保留 case identity、期望状态枚举、尝试/完成迭代数、unexpected crash、ANR、invariant violation、最大恢复时间和
evidence digest。以下任一情况失败或不完整：

- case 缺失、重复或低于 evidence mode 的最小迭代；
- unexpected crash、ANR 或 invariant violation 非零；
- completed iterations 不等于 attempted iterations；
- observed outcome 与固定 matrix 不一致；
- max recovery 超过对应 fault budget；
- Android/target run 未达到最短持续时间。

报告输入顺序不影响 digest。原始 logcat、tombstone、ANR trace、Perfetto、serial、fingerprint、车型、车辆/用户/模型 payload 不进入 GitHub。

## 7. 真实 72h 运行前置条件

1. ADB transport online，release/source/archive hash 和非敏感 alias 已冻结。
2. 真实 Runtime 场景执行、fault injector、observation collector 和 crash/ANR owner 已发布。
3. 原始证据进入仓库外受控目录，只向 Issue/PR 提交计数、枚举和 digest 摘要。
4. 目标 owner 批准 workload、故障方法、恢复预算和终止条件。
5. P8 的真实 Adapter/NPU 阻塞不得由模拟结果关闭；未支持 fault 应明确 unavailable。

当前不满足这些条件，因此 `stability_target_72h_complete=false`。

## 8. 验证

```bash
cd central-brain/android-runtime
JAVA_HOME="$PWD/../../.tools/jdk" \
ANDROID_HOME="$PWD/../../.tools/android-sdk" \
./gradlew :runtime-service:testDebugUnitTest \
  --tests com.centralbrain.runtime.reliability.StabilityFaultMatrixContractTest
cd ../..
bash tools/check_central_brain_android_stability_fault_matrix.sh
```

当前：`stability_fault_matrix_contract_defined=true`、`stability_workload_count=3`、
`stability_fault_count=6`、`stability_matrix_case_count=18`、`stability_report_validation_verified=true`、
`stability_target_72h_complete=false`、`stability_target_owner_approved=false`、
`stability_android13_arm64_verified=false`、`stability_fault_injection_runtime_wired=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。

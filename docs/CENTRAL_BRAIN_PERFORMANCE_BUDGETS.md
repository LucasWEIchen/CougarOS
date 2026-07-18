# Central Brain P9-W01 Performance Budgets

版本：1.0

日期：2026-07-18

状态：software contract verified / target measurement pending

## 1. 目的和边界

P9-W01 为 Android 13 应用层冻结可执行的性能预算、聚合口径和证据模式。预算覆盖 Binder、Plan、Effect dispatch、
数据库、内存、CPU 和启动七类，不读取系统性能、不调用车辆/NPU，也不把 WSL/host 或 debug 合成数据解释为目标硬件结果。

Req IDs：`S2-OBS-001`、`S2-REL-001`、`XSC-001/004/005/006`、`KH-003/006`、
`DEL-001/004/005`。tracking：`DEV-086`、`ISSUE-048`。

## 2. 交付

- `central_brain_android_p9_performance_budget.json`：机器可读 profile、七类/十项预算、证据模式和 claim state。
- `PerformanceBudgetContract.java`：固定 catalog、严格 measurement/context、fail-closed aggregate report 和 canonical digest。
- `PerformanceBudgetContractTest.java`：阈值边界、缺项、样本不足、单位错配、重复项、顺序稳定和 authority 边界。
- `PerformanceBudgetProbeActivity.java`：DUMP-protected debug-only 合成阈值探针，不执行真实 benchmark。
- `check_central_brain_android_performance_budget.sh`：校验 JSON/Java 一致、release 隔离、禁止系统读取和文档追踪。

## 3. 初始工程预算

| Metric ID | 聚合 | 限值 | 口径 |
| --- | --- | ---: | --- |
| `binder.protocol_negotiation.latency_us` | P95 | 10,000 us | version/hash negotiation |
| `binder.session_open.latency_us` | P95 | 50,000 us | typed Session admission |
| `binder.read_cancel.latency_us` | P95 | 30,000 us | bounded read/cancel admission |
| `binder.callback_registration.latency_us` | P95 | 50,000 us | callback + owner validation |
| `plan.compile.latency_us` | P95 | 300,000 us | accepted resolution -> validated Plan；不含模型 |
| `effect.dispatch_admission.latency_us` | P95 | 200,000 us | policy/prepare/durable admission/claim；不含车辆 apply/readback |
| `database.combined_size.bytes` | MAX | 67,108,864 B | Room DB + WAL + SHM，执行 bounded acceptance workload 后 |
| `runtime.pss.bytes` | MAX | 268,435,456 B | steady-state Runtime total PSS |
| `runtime.cpu.single_core_permille` | P95 | 300 permille | steady-state，按单核归一化 |
| `runtime.cold_start_to_binder_ready.latency_us` | P95 | 3,000,000 us | 冷启动到 reconciliation barrier 后 typed Binder ready |

这些值是 `initial_software_budget`，不是 OEM owner 已批准的量产指标。变更阈值必须升级 profile/version、同步 JSON/Java、
记录 owner 和重新执行完整验收，禁止为了让测试通过而静默放宽。

## 4. Evidence mode

| Mode | 最小样本 | 可否作为目标候选 | 用途 |
| --- | ---: | --- | --- |
| `CONTRACT_TEST` | 1 | 否 | 合成边界、schema 和状态机测试 |
| `ANDROID_APPLICATION` | 30 | 否 | 非目标 API 33 应用环境或实验室 APK 测量 |
| `TARGET_ANDROID13` | 30 | 仅结构完整候选 | 绑定 release/source/non-secret alias/evidence digest/owner approval |

即使 `TARGET_ANDROID13` 十项都通过，`isTargetHardwareQualified()` 仍固定 false。生产或目标资格只能由独立 Release/安全/
车辆/NPU 验收关闭，不能由单个性能报告自动提升。

## 5. 报告规则

每项 measurement 只包含固定 metric、固定 unit、聚合值、样本数和 SHA-256 evidence digest。报告拒绝重复项；缺项、单位错配和
样本不足产生 `INCOMPLETE`，任一超限产生 `EXCEEDED`，十项全部在预算内才产生 `PASSED`。输入顺序不影响 report digest。

原始 trace、Perfetto、logcat、serial、fingerprint、车型、车辆/用户/模型 payload 不进入 GitHub。目标采集器和受控 evidence
目录将在设备恢复在线后单独交付；当前代码没有读取 `/proc`、Android memory/CPU API、系统时钟、车辆服务或 NPU telemetry。

## 6. 验证

```bash
cd central-brain/android-runtime
JAVA_HOME="$PWD/../../.tools/jdk" \
ANDROID_HOME="$PWD/../../.tools/android-sdk" \
./gradlew :runtime-service:testDebugUnitTest \
  --tests com.centralbrain.runtime.performance.PerformanceBudgetContractTest
cd ../..
bash tools/check_central_brain_android_performance_budget.sh
```

当前：`performance_budget_contract_defined=true`、`performance_budget_category_count=7`、
`performance_budget_metric_count=10`、`performance_budget_report_validation_verified=true`、
`performance_budget_target_owner_approved=false`、`performance_budget_target_measurement_complete=false`、
`performance_budget_android13_arm64_verified=false`、`performance_budget_runtime_wired=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W02`。

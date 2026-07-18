# Central Brain P8-W01 Target Capability Discovery Contract

版本：1.0

日期：2026-07-18

状态：software preparation complete / target evidence external-blocked

## 目的

P8-W01 面向黑盒 Android 13 座舱控制器，冻结真实车辆和 Vendor NPU adapter 开发之前必须取得的
property/service/permission/owner/version/readback/fault 证据。它不尝试发现私有设备节点，不调用未发布
Vendor API，不写车辆属性，也不把 Automotive feature 或 Binder service 名称解释为可用车控能力。

Req IDs：`S2-ADP-002`、`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007`、
`DEL-004/005`。tracking：`DEV-085`、`ISSUE-024/027/030/047`。

## 交付

- `central_brain_android_p8_target_capability_discovery.json`：固定八个 Stage 2 capability、14 列矩阵、
  完成门禁、禁止操作和全部失败关闭 claim。
- `collect_central_brain_android_target_capabilities.sh`：只读 ADB 采集器。读取 API level、公开
  PackageManager feature、shell 可见 Binder service 和 command service 清单。
- `check_central_brain_android_target_capability_discovery.sh`：JSON 合同、禁止操作、仓库外证据、
  `0600` 权限、八行矩阵和 summary 脱敏的动态假设备测试。

## 使用

设备在线后，在 WSL 中执行：

```bash
bash tools/collect_central_brain_android_target_capabilities.sh \
  --adb /mnt/e/platform-tools/adb.exe \
  --serial <adb-serial> \
  --device-alias local-cockpit-a13-01 \
  --evidence-dir /tmp/cougaros-p8-capability-evidence
```

`--device-alias` 必须是非秘密别名。`--evidence-dir` 必须位于 Git 仓库外；目录权限为 `0700`，
文件权限为 `0600`。命令终端只输出 `summary.properties` 的计数、布尔值和 SHA-256 引用，不输出
serial、fingerprint、车型、signer 或原始 service 名称。原始证据不得提交 GitHub；Issue 只引用
非秘密设备别名和内部证据 digest/路径编号。

## Matrix 填写规则

生成的 `target-capability-matrix.tsv` 固定八行并全部为 `EXTERNAL_BLOCKED`。测试人员只能基于以下
受控输入替换 `UNAVAILABLE`：

1. 公开 Android Car property list，包含 property ID、area、type 和 read/write；
2. 厂商发布的 Vendor service AIDL/SDK，包含 interface version 和错误合同；
3. permission/signature/SELinux owner 文档；
4. readback、timeout、Binder death、fault、rollback 和幂等说明；
5. 对应材料的内部 evidence reference 和 owner approval。

任何一项缺失时，对应 capability 继续为 `EXTERNAL_BLOCKED`，不得用 debug Digital Twin、
deterministic provider、service name、`dumpsys` 车辆状态或猜测 property ID 补齐。

## 当前结论

当前 ADB transport 为 offline，且仓库没有目标公开 CarProperty list、Vendor AIDL/SDK 或权限/签名文档。
因此本轮只完成软件准备，不完成真实目标发现：

```text
target_capability_discovery_contract_defined=true
target_capability_read_only_collector_verified=true
target_capability_matrix_template_count=8
target_capability_summary_redaction_verified=true
target_capability_matrix_complete=false
public_car_property_list_available=false
vendor_service_contract_available=false
permission_signature_policy_available=false
target_capability_discovery_external_blocked=true
vehicle_property_mapping_configured=false
production_adapter_registered=false
vendor_npu_provider_available=false
driver_development_triggered=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
implementation_stage=P9-W03
```

P8-W02..W06 继续外部阻塞。其间可独立推进 P9-W01 性能预算合同，但不得把 P9 软件证据解释为
P8 adapter、车辆控制、Vendor NPU 或目标硬件完成。

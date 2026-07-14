# Central Brain Android 13 黑盒预检与安装验收

版本：0.1

日期：2026-07-14

状态：B3 verified on API 33 emulator and physical Android 13 ARM64 controller

## 目的与范围

本流程面向无法取得厂商 AOSP/BSP/SDK 源码、只能通过厂商允许的 ADB 和普通 APK
安装进行集成的 Android 13 座舱域控制器。它验证 Central Brain C/Java 工程能否作为
普通 `/data/app` 应用运行，不修改已刷机系统，不推断任何私有 Vendor NPU/VHAL 接口。

Req IDs：`APP-004`、`XSC-001`、`XSC-004`、`XSC-005`、`XSC-006`、
`NV-F-001`、`NV-F-011`、`NV-F-012`、`NV-G-003`、`NV-G-005`、
`NV-G-006`、`NV-G-007`、`NV-P-002`、`KH-003`、`KH-006`、`DEL-001`、
`DEL-003`、`DEL-004`、`DEL-005`。

## 两道门禁

### 1. 安装前只读预检

```bash
source env.sh
export ADB=/mnt/e/platform-tools/adb.exe  # Windows owns USB; omit for native Linux adb
bash tools/preflight_central_brain_android13_blackbox.sh \
  --serial <serial> \
  --require-api-33 \
  --report builds/central-brain-blackbox-evidence/pre-install.properties
```

脚本只读取 Android release/API、build identity、fingerprint、ABI 列表、Automotive feature、
shell 可见的 SELinux/verified-boot 状态、包路径/UID/flags/version 和 signer。若 Runtime 或
Demo 已安装，脚本通过 `pm path` 取得普通 APK 路径，读取该 APK 并用 `apksigner` 比较
当前 signer；不匹配或无法读取时在安装前失败。

`test_central_brain_android_blackbox_signer_guard.sh` 会用临时证书重签两份 APK 副本并
验证现有包 signer mismatch 在任何安装命令之前被拒绝；临时 keystore 和副本退出即删除，
设备不发生 mutation。

预检不安装/卸载包，不请求高权限，不修改 SELinux，不扫描设备节点，不测试私有 vendor
service，不写 system/vendor/boot 分区。`preflight_mutation_performed=false` 是强制输出。

### 2. 受控安装与应用内验证

```bash
bash tools/test_central_brain_android_blackbox_acceptance.sh \
  --serial <serial>
```

该命令先执行只读 signer/ABI 门禁，然后构建并验证 hybrid Runtime APK，运行已有完整
Binder/Room/Governance/HMI 安装验收和 Native Runtime 进程恢复，再启动仅存在于 debug
source 的 DUMP-protected Java probe。Probe 使用公开 `PackageManager`、`Build`、`Process`
API 验证：

- 当前进程是 64 位，并支持 `arm64-v8a` 或 `x86_64`；
- Runtime 位于 `/data/app`，UID 是普通应用 UID，数据目录为 app-private；
- PackageManager 当前 signer SHA-256 与交付 APK signer 一致；
- Native Runtime load/init 成功，force-stop 后可由新进程重建；
- Binder、Room、Governance 与 Demo HMI 回归通过；
- private vendor API/device node 未探测，Vendor NPU/dispatch/hardware 全部为 false。

## 结果解释

| 输出 | 含义 |
| --- | --- |
| `api33-emulator-blackbox-preflight` | 只证明 API 33 模拟器应用层；仍需物理控制器验收 |
| `api33-device-blackbox-preflight` | 证明某台非模拟器设备的应用层门禁；不自动证明它是最终量产控制器 |
| `automotive_feature_advertised=false` | 系统未声明 AAOS feature；记录事实但不单独阻断座舱 app 集成 |
| `selinux_state_observed=*` | 只读观测值；工具不会更改或声明策略合规 |
| `target_hardware_validated=false` | NPU/VHAL/车辆/安全/性能/热验证仍未完成 |

## 失败处理

- API 不是 33：切换到指定 Android 13 环境，不放宽 B3 证据门禁。
- 无 `arm64-v8a`/`x86_64` 64 位 ABI：停止安装，评审是否需要新增 ABI；不得假装兼容。
- 现有包 signer 不一致：停止升级，由目标 owner 提供签名迁移/卸载策略；工具不自动卸载。
- APK 不在 `/data/app` 或存在 SYSTEM/PRIVILEGED/PERSISTENT flags：记录厂商部署策略，
  不自行改成系统应用。
- Probe 无法读取 signer/native 状态：保留 ISSUE-027，不用模拟器结果替代真机结果。

## 尚需目标方提供

- 物理控制器 ADB serial 和允许的安装/升级窗口；
- production signer、旧版本 signer 迁移和回滚策略；
- 后台进程/电源/休眠/多用户/MDM 策略；
- Client2/RenderService 信任和覆盖安装规则；
- 公开 vendor service/NPU/VHAL contract（若存在）及其权限/ABI 文档。

在这些生产签名、后台策略、Client2 trust 和 vendor contract 输入到位前，`ISSUE-027` 保持 Open，
`target_hardware_validated=false`、`native_vendor_npu_provider_available=false`、
`native_runtime_dispatch_enabled=false` 和 `hardware_accessed=false` 不得改为 true。

## 2026-07-12 模拟器证据

`emulator-5554`（`sdk_gphone64_x86_64`、API 33、x86_64）已完成安装前 signer
preflight、受控安装、完整 Binder/Room/HMI 回归、Native Runtime force-stop recovery、
应用内 PackageManager signer parity 和安装后复检。Runtime version 为 `0.3.0-b3`，
普通应用 UID 为 `10174`，SELinux 只读观测为 `Enforcing`，signer SHA-256 为
`2079de9bf19818b40d25acbcd8cb7643549b04a536b057997ef9103e1dc241a1`。

该 AVD 未声明 `android.hardware.type.automotive`，verified-boot/flash-lock/vbmeta 属性对
shell 返回 UNKNOWN。上述结果保持原值，不据此推断物理控制器策略；
`physical_controller_evidence_available=false` 和 `target_hardware_validated=false`。

## 2026-07-14 物理设备证据

非秘密设备别名 `local-cockpit-a13-01` 已通过 WSL 调 Windows ADB 完成同一 B3 流程。目标为
Android 13/API 33、UNISOC、arm64-v8a、Automotive、SELinux Enforcing；Runtime/Demo 普通
`/data/app` 安装、signature permission、Binder/Room/Governance/HMI、Native Runtime lifecycle
和 process recovery 通过，测试结束后 Crash/ANR buffer 为空。

测试中修复了 Windows ADB CRLF 设备枚举/get-state 误判和 signer guard 切换回 Linux ADB 的
问题。物理设备的 raw serial、fingerprint、signer digest 和完整日志未写入仓库。目标机现有
Client2 与 debug Client2 signer 不一致，Client2 dry-run 在首次安装前失败关闭且没有修改原包。

详细脱敏结果见 `CENTRAL_BRAIN_ANDROID13_PHYSICAL_TARGET_TEST_REPORT.md`。当前只允许
`physical_controller_application_evidence_available=true`；`target_hardware_validated=false`、
`production_ready=false` 和所有 NPU/VHAL/Driver-HAL 激活标志继续保持 false。

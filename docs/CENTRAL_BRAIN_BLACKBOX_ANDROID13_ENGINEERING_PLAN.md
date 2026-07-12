# Central Brain 黑盒 Android 13 实际工程计划

版本：1.0

日期：2026-07-12

状态：B0-B4 hybrid software handoff complete / physical target pending

## 目标

在只有已刷机 Android 13 设备、ADB 安装能力和公开 Android API 的条件下，
把现有 Central Brain Android Runtime 演进为以 Java 与 C 为主、可安装、可诊断、
可替换厂商适配器的实际工程。工程不得要求修改厂商 Android Framework、AOSP、
BSP、预编译系统组件、SELinux 策略或芯片 SDK 源码。

涉及 Req IDs：`APP-004`、`XSC-001`、`XSC-004`、`XSC-005`、`XSC-006`、
`NV-F-001`、`NV-F-011`、`NV-F-012`、`NV-G-003`、`NV-G-005`、
`NV-G-006`、`NV-G-007`、`NV-P-002`、`KH-003`、`KH-006`、`DEL-001`、
`DEL-003`、`DEL-004`、`DEL-005`。

## 黑盒假设

- 目标系统是 Android 13/API 33，但厂商 Framework/BSP/SDK 源码不可用。
- 首选普通 `/data/app` 安装，不假设 platform key、priv-app、root、remount 或
  service manager 注册权限。
- 目标 CPU ABI、应用签名策略、后台限制、RenderService 信任规则和厂商服务目录
  在首次真机预检前均为未知。
- 首版 native artifact 支持 `arm64-v8a` 与 `x86_64`：前者用于座舱硬件，后者用于
  现有 API 33 模拟器。其他 ABI 不自动推断。
- 真实 NPU、VHAL、车辆总线、Camera/Audio/Sensor、Safety Runtime 和共享内存
  均保持版本化 empty provider；禁止扫描私有设备节点、猜测 ioctl 或调用未公开 API。
- 当前阶段只开发 Android 交付，不新增 Linux 前端，不开发虚拟化。

## 工程模块

| 模块 | 主要语言 | 职责 | 部署形态 |
| --- | --- | --- | --- |
| `central-brain-sdk` | Java/AIDL | Typed Binder、callback/cancel/death、客户端 API | AAR |
| `runtime-service` | Java | Binder 身份、Capability、Governance、Room、生命周期和诊断 | APK |
| `native-runtime` | C + Java/JNI | 版本化 C ABI、native 生命周期、资源状态和 provider 插槽 | AAR + `.so` |
| `demo-hmi` | Java | 维护型测试客户端和状态显示 | APK |
| Client2 patch | Java/Smali | 原车 UI 叠加层和场景入口 | APK |

## 语言边界

Java 层拥有 Android 对象和系统语义：Binder UID/package/signer、signature permission、
PackageManager、Room、线程调度、Activity/Service 生命周期、用户可见错误和审计。

C 层只拥有与 Android 对象无关的 native runtime 状态：ABI 版本、固定宽度状态码、
有界资源计数、provider descriptor、初始化/关闭和故障快照。C 核心不得保存 `JNIEnv*`、
`jobject` 或 Java 字符串引用，不读取 Binder 身份，不直接访问文件系统、网络或硬件。

JNI 是窄桥接层：通过 `JNI_OnLoad`/`RegisterNatives` 注册；只传递整数、固定数组或
有界 byte buffer；不在 Binder 线程执行长任务；Java wrapper 负责把 native 状态转换为
不可变对象。任何 native handle 必须显式关闭并防止 use-after-close。

## C ABI V1 规则

- 导出符号使用 `cb_` 前缀和 C linkage；不把 C++ ABI 暴露给 Java 或厂商 adapter。
- 每个公开 struct 的首字段必须包含 `struct_size` 与 `abi_version`。
- 使用 `<stdint.h>` 固定宽度类型；不跨 ABI 传递 `long`、C++ object、STL 或可变布局。
- 调用者分配输出结构；callee 不向调用者转移隐式 heap ownership。
- 所有输入执行 null、长度、版本、容量和状态检查；错误必须返回稳定枚举。
- Runtime/provider 生命周期为 `create -> query/bind -> shutdown -> destroy`。
- 当前 software provider 可以用于生命周期验收；vendor NPU provider 必须返回
  `UNAVAILABLE`，并固定 `hardware_accessed=false`。

## 黑盒设备预检

预检只能读取公开或普通应用可见状态：API level、primary/supported ABI、64-bit 状态、
build fingerprint、package install path、应用 UID、签名权限、native library load、Binder
连通性和 app-private storage。预检不得执行 root、remount、fastboot、分区写入、SELinux
修改、设备节点遍历或厂商服务压力测试。

## 连续实施阶段

| 阶段 | 交付 | 退出条件 |
| --- | --- | --- |
| B0 | 黑盒约束、模块/语言/ABI/验收设计 | 静态门禁与 Req ID/偏差/问题记录通过 |
| B1 | `native-runtime` C ABI V1、JNI、Java wrapper | Host C tests、AAR、arm64/x86_64 `.so` 校验通过 |
| B2 | Native Runtime 接入 Java Runtime/Diagnostic | API 33 load/lifecycle/dumpsys/Binder parity 通过 |
| B3 | 黑盒能力探测和安全安装验收 | 模拟器通过；真机命令可执行且不伪造真机结果 |
| B4 | 实际工程交付包与安装/使用指南 | artifact、hash、signer、ABI、rollback 和指南可复验 |

每个阶段完成代码、验证、文档、偏差/问题跟踪与 Git commit 后，直接进入下一阶段；
不使用定时心跳。

B2 已在 API 33 x86_64 完成 Runtime APK native load/lifecycle/dumpsys/Diagnostic/process
recovery 验证；这不构成物理目标验证。B3 证据始终将模拟器结果与目标设备结果分开记录。

B3 已形成只读 preflight、existing-signer fail-closed gate 和应用内 PackageManager probe，
并在 API 33 x86_64 模拟器通过。物理控制器、production signer、后台策略、RenderService
trust 与 vendor contract 仍 unresolved；B4 不得用交付包生成结果替代这些输入。

B4 已生成独立 hybrid profile、5 项 artifact/hash/signer inventory、Native 双 ABI/ELF
inventory、默认 dry-run installer、maintenance/Client2 两种安装 profile 和完整使用/rollback/
adapter 指南，并通过 API 33 x86_64。该 software handoff 完成不改变上述目标输入状态。

## 完成判定

实际工程完成必须同时满足：

1. Java/AIDL/Room/Binder 路径与 C Native Runtime 均能在 API 33 安装运行。
2. Runtime APK 只包含 allowlist 内的 `libcentral_brain_native.so`，且同时提供
   `arm64-v8a`/`x86_64`。
3. Native load/init/query/shutdown、Binder death/reconnect、进程恢复和签名权限回归通过。
4. 黑盒预检和 installer 默认无破坏，签名或 ABI 不匹配时在安装前失败关闭。
5. Vendor NPU/VHAL 等 empty provider 不因 native library 存在而被标记为可用。
6. 最终文档给出环境准备、构建、安装、启动、UI/命令使用、诊断、卸载/回滚和
   目标厂商 adapter 接入步骤。

## 明确非目标

- 不修改厂商 Android 系统软件、预编译组件、SELinux 或 system/vendor 分区。
- 不开发或猜测 NPU kernel driver、VHAL、HIDL/AIDL HAL、Safety Runtime 或私有 daemon。
- 不开发 Linux 前端、Hypervisor 或跨 VM 数据面。
- 模拟器 native load 不等于真实座舱硬件、NPU、车辆控制或量产资格验证。

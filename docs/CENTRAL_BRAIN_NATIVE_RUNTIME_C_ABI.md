# Central Brain Native Runtime C ABI V1

版本：1.0

日期：2026-07-12

状态：B1 implemented / B2 Runtime integration pending

## 范围

本 ABI 是 Android Runtime 与未来 Native Provider 之间的进程内用户态边界。它只负责
native runtime 生命周期、有界 slot 和 fail-closed 状态，不是 NPU Driver/HAL、VHAL、
车辆总线或模型推理 API。

Req IDs：`XSC-004`、`XSC-005`、`NV-F-001`、`NV-F-011`、`NV-G-006`、
`NV-G-007`、`NV-P-002`、`KH-003`、`KH-006`、`DEL-001`、`DEL-004`、
`DEL-005`。

公开头文件：
`native-runtime/src/main/cpp/include/central_brain_native.h`。

## ABI 规则

- ABI version 为 `1`，最大 slot 为 `64`。
- 所有公开 struct 以 `uint32_t struct_size`、`uint32_t abi_version` 开头。
- 只使用固定宽度整数、opaque handle 和 caller-owned output。
- 不跨边界传递 C++、STL、`long`、variadic 参数、Java object 或隐式 heap ownership。
- V1 接受大于等于当前已知大小的 struct，以允许兼容地追加尾字段；未知 reserved flag
  必须为零。
- 同一 handle 上的 query/acquire/release 由 C 内部 mutex 串行化；调用者必须把 destroy
  与其他操作串行化。

## 状态码

| 值 | 名称 | 含义 |
| --- | --- | --- |
| 0 | `CB_STATUS_OK` | 操作完成 |
| 1 | `CB_STATUS_INVALID_ARGUMENT` | null、范围、reserved flag 或 lease 非法 |
| 2 | `CB_STATUS_ABI_MISMATCH` | struct size/version 不兼容 |
| 3 | `CB_STATUS_OUT_OF_MEMORY` | runtime 分配失败 |
| 4 | `CB_STATUS_CAPACITY_EXHAUSTED` | active slot 达到上限 |
| 5 | `CB_STATUS_NOT_FOUND` | lease 不存在或已释放 |
| 6 | `CB_STATUS_BUSY` | active lease 存在，拒绝 destroy |
| 7 | `CB_STATUS_CLOSED` | Java wrapper 已关闭；当前由 Java 层产生 |
| 8 | `CB_STATUS_INTERNAL_ERROR` | mutex 或内部不变量失败 |

## 函数

| 函数 | 输入 | 输出/约束 |
| --- | --- | --- |
| `cb_runtime_create_v1` | config + out handle | max slot 1..64；reserved=0 |
| `cb_runtime_get_health_v1` | handle + health output | 返回有界计数、generation 和 provider/hardware false |
| `cb_runtime_acquire_slot_v1` | handle + lease output | 唯一非零 lease；容量满时不写有效 lease |
| `cb_runtime_release_slot_v1` | handle + lease | 重复/未知 lease 返回 `NOT_FOUND` |
| `cb_runtime_destroy_v1` | handle | active slot 非零时返回 `BUSY`，不释放 handle |
| `cb_status_name` | status | 返回静态稳定名称，不转移 ownership |

每次 acquire/release 增加 monotonic generation。Lease counter 回绕时必须跳过零和仍活动的
lease ID。当前 native runtime 不创建 software model provider，不绑定 Vendor NPU，健康快照
固定 `software_provider_available=0`、`vendor_npu_provider_available=0`、
`hardware_accessed=0`。

## JNI 映射

`NativeRuntime` 通过 `System.loadLibrary("central_brain_native")` 加载 `.so`；
`JNI_OnLoad` 使用 `RegisterNatives` 注册五个 static native method。JNI 不缓存
`JNIEnv*`、`jobject` 或 local reference，不启动线程，不执行 I/O，也不调用 Android
Framework API。

Java wrapper 对所有 handle 操作使用 `synchronized` 串行化。`close()` 遇到 `BUSY`
抛出异常并保留 handle；只有 native destroy 成功后才把 handle 清零。Native health 通过
固定长度 `long[]` 转换为不可变 `NativeRuntimeSnapshot`；任何 ABI、范围、provider 或
hardware positive claim 都失败关闭。

## 构建与 ABI

- NDK：`27.3.13750724`。
- CMake：`3.22.1`。
- C language：C11，无 C++ runtime/STL。
- Android ABI：`arm64-v8a`、`x86_64`。
- 输出：`native-runtime-debug.aar`，每个 ABI 只允许
  `jni/<abi>/libcentral_brain_native.so`。

## 验证

Host C contract：

```bash
bash tools/test_central_brain_native_runtime_host.sh
```

Android AAR：

```bash
bash tools/build_central_brain_android_runtime.sh
```

独立 artifact verifier：

```bash
bash tools/verify_central_brain_native_runtime_aar.sh
```

Verifier 必须确认 AAR 只有 `arm64-v8a` 与 `x86_64` 两个 allowlist payload、ELF
machine 正确、七个 C/JNI 入口可见、没有 name-based `Java_*` 导出、RELRO/NOW
生效，并且没有链接 NPU/OpenCL/Vehicle/vendor library。该检查只验证软件 artifact，
不表示已访问或验证硬件。

2026-07-12 B1 evidence：Host C 的 ASan/UBSan 生命周期、容量和四线程并发测试
通过，Java snapshot 6 项单测通过；Android Gradle 141-task build 成功。AAR 的两套
ELF machine、七个公开 C/JNI 入口、无 name-based JNI、RELRO/NOW 与无
NPU/OpenCL/Vehicle/vendor dependency 检查通过。

B1 只证明 native artifact 与 ABI contract。B2 才允许 Runtime APK 依赖该 AAR；B3
才在 API 33/黑盒设备验证 load、lifecycle 和 recovery。任何这些证据都不等于 NPU/VHAL
硬件验证。

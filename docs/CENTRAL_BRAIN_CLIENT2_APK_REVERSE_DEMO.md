# Client2 APK Reverse Demo Path

版本：0.1
日期：2026-07-11

## 目标

本文件定义基于 `Client2` APK 底层逆向产物进行中央大脑演示 App 二次开发的测试工程。该路径用于快速构建接近用户真实座舱界面的演示 APK，同时保留架构图基线、Req ID、Driver/HAL 边界和 Android/Linux 交付边界。

## 输入与工程位置

| 项 | 路径 |
| --- | --- |
| 原始 APK | `apks/original/client2_20260306_170923.apk` |
| 逆向基线 | `reverse/client2/apktool` |
| 测试工程 | `apk-labs/client2-central-brain` |
| 生成工作目录 | `builds/client2-central-brain/workdir` |
| 签名输出 | `builds/client2-central-brain/signed/client2-central-brain.debug.apk` |

`apks/original` 和 `reverse/client2/apktool` 保持基线用途。测试工程每次复制逆向基线到 `builds/client2-central-brain/workdir`，再打补丁、构建和签名，避免污染原始 APK 与原始逆向证据。

## 当前增量

当前已完成一个可验证 APK 底层交互改动：

```text
Client2 MainActivity
├── res/layout/main_layout.xml
│   ├── left 2/3: original TuanjieView containers view1/view2/view3
│   └── right 1/3: fixed Central Brain interaction panel
├── AndroidManifest.xml
│   └── INTERNET + usesCleartextTraffic=true for temporary emulator HTTP demo
└── smali/com/tuanjie/urasclient2
    ├── MainActivity.smali setContentView 后安装 CentralBrainPanelController
    ├── CentralBrainPanelController.smali
    ├── CentralBrainPanelController$RequestTask.smali
    └── CentralBrainPanelController$UiUpdate.smali
```

右侧 1/3 面板使用半透明浅灰背景、浅色按钮和浅色回复区；上部包含 `我冷了` 和 `我累了` 两个按钮，下部包含 `centralBrainReplyText` 文本框。按钮点击后，smali 控制器从 APK 内发起临时 HTTP POST 到 `http://10.0.2.2:8787/ai/infer`，请求 Python 原型的 Model Runtime Adapter；回复优先显示 `result.generated_text`，没有 Ollama 文本时回退显示 mock `result.summary` 或原始响应。控制器使用 `requestInFlight` 阻止同一 Activity 内的重复并发请求。

该改动不修改 RenderService，不修改 Unity Addressables，不访问真实硬件。它证明 APK 资源 patch、Manifest patch、smali hook、smali 网络请求、rebuild、zipalign、debug sign 和静态验证链路成立。

## 架构映射

| Req ID | 映射 |
| --- | --- |
| `APP-004` / `XSC-001` | 右侧面板作为 AI SDK/Agent 可视入口；本轮为了本地演示临时直连 Python 原型 `/ai/infer`，偏差登记在 DEV-017/DEV-001。 |
| `XSC-002` | 后续面板状态必须来自 Uni Info Bus 语义对象。 |
| `XSC-003` | 后续动作必须经 SOA 服务入口，不直接 dispatch 车控或 NPU。 |
| `XSC-005` | 后续调用必须保留 Runtime & Governance 状态、Policy 和 Audit 可见性。 |
| `XSC-006` | APK 内 HTTP 接入只作为 Android Protocol Binding demo，生产路径仍应迁移到 system/privileged service、Binder 或 SDK。 |
| `DEL-001` | Android 是主验证路径，输出可安装 debug APK。 |
| `DEL-003` | 文档给出工程位置、构建命令和边界。 |
| `DEL-004` | 明确 APK patch 与量产 Android system service 的平台差异。 |

## 命令

构建：

```bash
bash tools/build_client2_central_brain_demo.sh
```

启动 Python 原型后端，供模拟器内 APK 访问：

```bash
CENTRAL_BRAIN_SIMULATED_NPU_BACKEND=ollama \
CENTRAL_BRAIN_OLLAMA_URL=http://127.0.0.1:11434 \
CENTRAL_BRAIN_OLLAMA_MODEL=qwen3.5:27b-optimized \
CENTRAL_BRAIN_OLLAMA_TIMEOUT_MS=90000 \
CENTRAL_BRAIN_OLLAMA_NUM_PREDICT=64 \
CENTRAL_BRAIN_OLLAMA_THINK=false \
bash tools/run_central_brain_backend.sh
```

APK 内固定访问 `http://10.0.2.2:8787/ai/infer`。在 Android emulator 中，`10.0.2.2` 指向模拟器宿主环境；如果后端不在该宿主上，需要后续把 endpoint 配置化或迁移到 Binder/system-service。

验证：

```bash
bash tools/check_client2_central_brain_demo.sh
```

安装：

```bash
bash tools/install_client2_central_brain_demo.sh
```

## 非目标

- 不开发虚拟化层。
- 不开发 Driver/HAL。
- 不访问 PCIe NPU、device node、ioctl、sysfs、vendor SDK、DMA、共享内存、车辆总线或 Safety Runtime。
- 不把 APK patch 路径描述为量产 Android system service。
- 不让 App UI 绕过 AI SDK、Uni Info Bus、SOA 和 Runtime & Governance 直接访问模型或 NPU。

## 2026-07-11 模拟器运行测试

测试环境为 `cabin_client_api36_x86_64`、Android API 36、`1920x1080` 和 `-gpu host`。为让测试过程出现在 WSLg/Windows 桌面，本轮直接启动 `emulator`，没有使用 `tools/start_client2_emulator.sh` 中的 `-no-window` 参数。

已通过：

- APK 增量安装成功，包名保持 `com.tuanjie.urasclient2`，`MainActivity` 进入 resumed 状态。
- Client2 原始座舱背景、3D 车辆和右侧固定 1/3 Central Brain 面板同时可见。
- `我冷了` 与 `我累了` 两个按钮均可触发后台请求，文本框可显示 `请求中`、后端摘要和超时错误。
- APK 到 `http://10.0.2.2:8787/ai/infer` 的 HTTP 路径返回过 `200`；App 无崩溃，未触发 Driver/HAL、硬件或虚拟化访问。

首次测试未通过：

- Ollama 自然语言 `result.generated_text` 尚未通过验收。默认 `CENTRAL_BRAIN_OLLAMA_NUM_PREDICT=96` 时，`qwen3.5:27b-optimized` 返回 `done_reason=length`、`response_length=0`、`thinking_length=337`，APK 因而回退显示 `ollama simulated NPU inference accepted`。
- 将生成上限提高到 `192` 后，端到端请求仍可能超过 APK `120000 ms` read timeout，界面会显示 `请求失败: timeout`。该结果登记到 `ISSUE-019`，不能视为 Ollama 自然语言回复验收通过。

### 修复复测

超时由四项叠加造成：27B 模型约 90% CPU/10% GPU 运行、thinking 消耗输出 token、APK 允许重复点击形成 Ollama 队列，以及测试用 `monkey ... 1` 可能随机注入额外点击。后端与 APK 同时使用 120 秒边界进一步放大了队列超时。

2026-07-11 修复后，Ollama adapter 默认 `think=false`，可通过 `CENTRAL_BRAIN_OLLAMA_THINK` 显式覆盖；本地演示使用 90 秒后端 timeout、64 token 上限，并从结构化模型输出中优先提取 `response_text`。APK 增加 single-flight，测试启动改用确定性的 `adb shell am start -n com.tuanjie.urasclient2/.MainActivity`。

清空旧 Ollama 队列后的可信单请求复测只产生一条 `/ai/infer` 日志，在 APK 120 秒 read timeout 内返回 HTTP 200，面板显示 `建议将模拟空调温度调高以缓解寒冷感。`。`CENTRAL_BRAIN_OLLAMA_THINK=false` 下直接推理与 SOA `npu-inference` smoke 均通过，`generated_text` 非空且 `thinking_text_available=false`。

生产路径仍按计划迁移到 Binder/SDK，不因本次演示修复改变架构边界。

## 已知风险

1. Client2 原始源码不可用，长期维护风险高于源码工程。
2. Debug 重签名可能影响 Client2 与 RenderService 的信任关系，需要在真机或 ARM64 环境验证。
3. RenderService 是 ARM64/Unity/Tuanjie 运行时，本地 x86_64 模拟器可能只能验证 Client2 UI 壳和右侧面板。
4. 后续如果面板需要访问 Python 原型后端，必须新增 `INTERNET`/cleartext 或 Binder/service 接入，并把直接 HTTP 演示路径记录为偏差。
5. 当前 HTTP endpoint 固定为 `10.0.2.2:8787`，只适合本地模拟器演示；真实座舱域环境应替换为 Binder/SDK 或目标平台允许的 IPC/RPC 接入。
6. 本地演示已用 `think=false`、single-flight 和 90 秒后端预算解决连续 timeout；目标模型和目标算力仍必须独立标定，且摘要回退不能当成自然语言回复。

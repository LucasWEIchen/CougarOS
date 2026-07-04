# 车载中央大脑路线图与进展

更新时间：2026-07-04

## 长期任务拆解

| 阶段 | 目标 | 主要交付物 | 状态 |
| --- | --- | --- | --- |
| M0 | 建立方向、文档、原型骨架 | 产品设计、架构设计、资料纪要、Android 原型、mock NPU 后端 | 进行中 |
| M1 | 本地端到端联通 | 后端启动脚本、APK 构建/安装、模拟器联通截图/日志 | 待开始 |
| M2 | 中间层服务网关 | Registry、Discovery、Schema、Policy、Trace mock 实现 | 待开始 |
| M3 | Android 系统接口 | AIDL contract、system service 原型、权限模型 | 待开始 |
| M4 | 车辆信号模型 | VSS 子集、VHAL/VSS 映射、信号订阅 | 待开始 |
| M5 | AI runtime | 模型管理、NPU SDK adapter、CPU fallback、推理队列 | 待开始 |
| M6 | 车载协议 | SOME/IP 或 DDS proof-of-concept | 待开始 |
| M7 | 量产约束 | 安全状态、OTA、诊断、审计、性能测试 | 待开始 |

## M0 任务清单

- [x] 读取并归档用户提供的软件架构图。
- [x] 调研 AAOS/VHAL/AIDL/VSS/SOME-IP/DDS/NPU 接入资料。
- [x] 核对现有 Android SDK、AVD、Git、OpenCLAW 环境。
- [x] 创建长期任务分支。
- [x] 编写产品设计文档初版。
- [x] 编写软件架构设计文档初版。
- [x] 编写资料纪要。
- [x] 创建 Android Console 原型。
- [x] 创建 mock NPU 后端。
- [x] 构建 Android APK。
- [x] 启动 mock 后端。
- [x] 安装 APK 到本地模拟器。
- [x] 验证 App 访问 `/health` 返回 `Health HTTP 200`。
- [x] 验证 App 触发 `/ai/infer` 返回 `Inference HTTP 200`。
- [ ] 形成 M0 Git 提交。

## 当前工程策略

- 不把现有 APK 逆向产物作为本任务第一阶段的修改对象。
- 所有新系统代码放在 `central-brain/`。
- 构建和运行脚本放在 `tools/`。
- 文档放在 `docs/CENTRAL_BRAIN_*`。
- 后续每完成一个可运行增量，都创建 Git 提交。

## 最近进展

### 2026-07-04

- 确认图片可通过 WSL 路径读取，并归档到 `docs/assets/central_brain_architecture_source.png`。
- 确认当前仓库已有 Android 逆向和模拟器工具链。
- 确认 OpenCLAW 绝对路径可用：`/home/normad400/.npm-global/bin/openclaw`。
- 创建分支：`codex/central-brain-ecosystem`。
- 新增中央大脑文档和第一阶段代码骨架。
- 构建并签名 Android APK：`central-brain/android-console/out/central-brain-console.debug.apk`。
- 启动 WSL mock 后端并验证 `/health`、`/vehicle/state`、`/ai/infer`。
- 在本地 AVD `cabin_client_api36_x86_64` 安装并启动 `com.centralbrain.console`。
- 验证模拟器 App 通过 `10.0.2.2:8787` 连通 WSL 后端。
- 保存验证截图：
  - `logs/test/central-brain/console-launch.png`
  - `logs/test/central-brain/console-inference.png`

# Android Console Prototype

这是中央大脑第一阶段 Android 前端原型。

## 特点

- 普通 Android App，不依赖 Gradle。
- 使用仓库本地 Android SDK、`aapt2`、`javac`、`d8`、`zipalign`、`apksigner` 构建。
- Debug APK 内置 `CentralBrainGatewayBinderService` 与 `CentralBrainGatewayClient`。
- App 层先绑定 Binder service，再通过 Binder 调用 Uni Info Bus State、AI SDK/Agent task plan、Agent execute、Skill invoke 与 Memory query contract mock。
- Binder service sample 默认以上游 prototype gateway `http://10.0.2.2:8787` 访问 mock 后端。
- 用于验证 Android App -> Binder -> AI SDK/Uni Info Bus/SOA/Tool/Memory contract 的最小闭环。

覆盖 Req ID：XSC-001、APP-004、FW-U-006、XSC-002、XSC-003、XSC-006、NV-P-002、NV-P-005、DEL-001。

## 构建

```bash
bash tools/build_central_brain_console.sh
```

产物：

```text
central-brain/android-console/out/central-brain-console.debug.apk
```

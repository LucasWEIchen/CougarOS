# Android Console Prototype

这是中央大脑第一阶段 Android 前端原型。

## 特点

- 普通 Android App，不依赖 Gradle。
- 使用仓库本地 Android SDK、`aapt2`、`javac`、`d8`、`zipalign`、`apksigner` 构建。
- 默认连接模拟器宿主机地址：`http://10.0.2.2:8787`。
- 用于验证应用层到后端 mock NPU 服务的最小闭环。

## 构建

```bash
bash tools/build_central_brain_console.sh
```

产物：

```text
central-brain/android-console/out/central-brain-console.debug.apk
```

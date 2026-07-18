# Central Brain Android 生产发布准入合同

状态：`SOFTWARE_CONTRACT_DEFINED / PRODUCTION_OWNER_INPUT_OPEN`

阶段：`P9-W05a`

Req IDs：`S2-REL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`

## 1. 设计目标

`ProductionReleaseAdmission` 在任何 APK 安装、数据库打开或回滚执行之前，对一个完整 Android 应用发布集给出确定性的
`ADMITTED` 或 typed rejection。合同覆盖：

1. Runtime、Demo HMI、Client2 三包的精确 package ID/name/order；
2. installed/candidate 每包 APK SHA-256、source commit、archive SHA-256 和 signer digest 结构；
3. installed 与 candidate 的逐包 same-signer，以及 candidate 三包 signer cohort；
4. upgrade release sequence、逐包 versionCode 单调性和至少一个包升级；
5. Runtime Room schema 的当前版本、candidate readable range 和 migration evidence；
6. rollback owner、rollback decision、data compatibility evidence，以及旧 APK 对现存数据库 schema 的可读性。

本合同不接受 APK/certificate bytes，不读取 PackageManager/keystore，不打开 Room，不安装或卸载包，也不执行 rollback。它是
release pipeline 与未来 target installer 之前的纯 Java policy boundary。

## 2. 固定发布集

| Package ID | Android package | 仓库 versionCode | 数据 schema |
| --- | --- | ---: | ---: |
| `runtime-service` | `com.centralbrain.runtime` | 3 | Room v4 |
| `demo-hmi` | `com.centralbrain.demo` | 1 | 0（非 durable owner） |
| `client2-demo` | `com.tuanjie.urasclient2` | 1 | 0（非 durable owner） |

该表来自当前仓库，不是下一量产 release 的版本规划。W05a checker 会同时读取 Gradle 与
`CentralBrainDatabase.VERSION`，防止合同悄悄偏离源码。Client2 versionCode 来自当前受维护重打包基线；未来正式源码化后必须由
构建工程发布，不得继续依赖逆向 workdir 作为量产来源。

## 3. 输入接口

### 3.1 `PackageSnapshot`

| 字段 | 约束 |
| --- | --- |
| `packageId/packageName` | 必须匹配固定三包表及顺序 |
| `versionCode` | 正整数 |
| `signerDigest` | 64 位小写 SHA-256；不接受证书或私钥 bytes |
| `artifactDigest` | 64 位小写 APK SHA-256 |
| `dataSchemaVersion` | Runtime 为正整数；其余包固定 0 |
| `minimumReadableDataSchemaVersion` | Runtime target 可读下界 |
| `maximumReadableDataSchemaVersion` | Runtime target 可读上界且不小于下界 |

### 3.2 `ReleaseSet`

`releaseId` 是有界 canonical ID；`releaseSequence` 是 owner 管理的正整数；`sourceCommitDigest` 与 `archiveDigest` 都是 SHA-256。
`packages` 必须精确包含固定三包，不允许缺项、重复、换名或动态扩展。

### 3.3 `AdmissionRequest`

`mode` 为 `UPGRADE` 或 `ROLLBACK`。两种模式都要求 production signer approval digest 与 release owner approval digest。schema
增加时要求 migration evidence digest。rollback 另要求 rollback owner、decision 和 data compatibility 三个 digest。Digest 只绑定
仓库外批准/证据，不把原始签名材料、issue 文本、设备身份或内部路径写入仓库。

## 4. 判定顺序

合同固定按下列顺序失败关闭，避免后置错误泄漏更多状态：

1. installed/candidate release identity 与 source/archive evidence；
2. 精确 package set、name、version、artifact/signer digest 和 schema 形状；
3. production signer owner 与 release owner evidence；
4. installed/candidate 逐包 same-signer；
5. candidate signer cohort；
6. mode-specific sequence、version 和 data compatibility；
7. migration 或 rollback 专用 evidence。

`Decision` 只暴露 `DecisionCode`、mode 和 domain-separated `decisionDigest`。四个 authority 方法
`mutatesDatabase/installsPackages/uninstallsPackages/executesRollback` 永远返回 false。

## 5. Upgrade 规则

- candidate release sequence 必须严格大于 installed；
- 每包 candidate versionCode 不得低于 installed，且至少一个包必须严格增加；
- candidate Runtime 必须能读取 installed Room schema；
- candidate declared data schema 不得低于 installed；
- data schema 增加时 migration evidence digest 必须存在；
- same-signer 或 signer cohort 任一不成立均拒绝，不自动卸载旧包。

W05a 不证明 migration SQL 正确。当前 v1->v4 migration 的已有测试仍是 schema 行为的权威证据；未来每个新 schema 必须先提交 Room
migration fixture，再由 release admission 绑定其 evidence digest。

## 6. Rollback 规则

- rollback owner、rollback decision、rollback data compatibility evidence 必须分别存在；
- candidate release sequence 必须严格小于 installed；
- 每包 candidate versionCode 不得高于 installed，且至少一个包必须严格降低；
- 不对数据库执行 destructive downgrade；
- rollback target Runtime 的 readable range 必须包含设备当前 installed database schema；
- same-signer 与 signer cohort 规则不因 rollback 放宽。

因此，“把旧 APK 重新安装上去”不等于安全 rollback。旧 APK 无法读取升级后的数据库时，合同返回
`DATA_SCHEMA_UNREADABLE`；即使 owner 给出 rollback decision 也不能覆盖该硬性数据兼容门禁。

## 7. 验证

```bash
source env.sh
bash tools/check_central_brain_android_production_release_admission.sh
cd central-brain/android-runtime
./gradlew :runtime-service:testDebugUnitTest \
  --tests com.centralbrain.runtime.release.ProductionReleaseAdmissionTest
```

八组 JVM test 覆盖正常 upgrade、signer/signer-cohort、精确包集/owner evidence、version/migration、schema
unreadable/downgrade、rollback 三 evidence、rollback data incompatibility 和全部 false claims。

## 8. 当前边界与后续工作

当前仓库仍是 debug signer 软件交付，production signer owner、正式 release sequence、OTA/MDM installer owner、rollback owner 和目标证据
均未提供。因此：

```text
production_release_admission_defined=true
production_signer_owner_approved=false
production_release_candidate_admitted=false
release_installer_wired=false
release_rollback_executor_wired=false
release_android13_arm64_verified=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

下一小步 W05b 应提供 debug-only Android release metadata probe 和 installer dry-run evidence adapter；它仍不得生成/上传签名材料或自动卸载。
真实 production signer、OTA/MDM、目标升级和 rollback rehearsal 继续由 `ISSUE-052` 外部阻塞。

# Central Brain GitHub 远程硬件测试闭环

## 1. 目的和边界

目标 Android 13 座舱控制器位于不可由维护者直接访问的内网，因此 B5 使用 GitHub 作为
版本与问题交换面，而不是设备远程控制面。测试人员在内网中连接真实设备并执行 ADB；维护者
发布不可变 Release、读取结构化 Issue、修复并发布下一候选版本。

覆盖 Req ID：`APP-004`、`XSC-001`、`XSC-004`、`XSC-005`、`XSC-006`、
`NV-F-001`、`NV-F-012`、`NV-G-006`、`NV-G-007`、`NV-P-002`、`DEL-001`、
`DEL-003`、`DEL-004`、`DEL-005`。

GitHub 不连接目标 ADB，不保存生产签名材料，不构建缺少受控 Client2 基线的完整交付，
也不自动关闭 NPU、VHAL、Driver/HAL、生产或硬件门禁。

## 2. 仓库和权限模型

仓库必须默认为 Private。建议权限如下：

| 角色 | 权限 | 责任 |
| --- | --- | --- |
| Maintainer | Write/Maintain | 分支、Release、Issue triage、修复和版本说明 |
| Target tester | Triage/Read | 下载 Release、创建/更新 Issue、复测 |
| Target owner | Triage/Read | 审批签名、后台、rollback、RenderService 和证据处理 |

`main` 必须启用保护：禁止 force-push、要求 Pull Request、要求
`Central Brain remote test contract` 检查通过。GitHub Actions 只检查合同和模板，不是完整
APK/Client2 的权威构建者。

推荐标签：`kind/hardware-test`、`state/triage`、`state/reproduced`、
`state/fix-ready`、`state/retest`、`state/verified`、`severity/blocker`、
`area/install`、`area/binder`、`area/native`、`area/client2`。

### 2.1 发布 ref 安全

本地 Git object database 还包含 Codex turn-diff 内部 refs，其全 ref 集合可达旧 APK/逆向
大对象；这些 refs 不是当前开发分支历史。对当前 `HEAD` 的独立可达性审计已确认最大 blob
为 1,221,099 字节，且没有 `apks/`、`reverse/`、APK/AAR/密钥路径。GitHub 对普通 Git
对象执行 100 MiB 上限，因此发布必须只推送已审计分支，不能使用 `--mirror`、不能推送
`refs/codex/turn-diffs/*`，也不能把旧内部 ref 合并到发布分支。参考
[GitHub large-file limits](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github)。

本地发布分支固定为 `codex/github-publication`，远端默认分支为 `main`。推送前运行：

```bash
bash tools/check_central_brain_github_publication_tree.sh codex/github-publication
git push -u origin codex/github-publication:main
```

后续 PR 分支必须从该发布分支或远端 `main` 创建。发布检查器扫描目标 ref 的全部可达历史，
拒绝 20 MiB 以上 blob、旧 APK/逆向路径、二进制/密钥扩展名和常见 credential marker。

## 3. Release 合同

标签格式固定为：

```text
android13-hwtest-v<major>.<minor>.<patch>-rc.<number>
```

每个 Release 必须由受控开发机上传：

```text
central-brain-android13-hybrid.tar.gz
central-brain-android13-hybrid.tar.gz.sha256
```

Release tag 所指提交必须与归档内 `DELIVERY-MANIFEST.json` 的 `source_git_commit` 一致。
Issue 必须同时引用 release tag、完整 source commit 和外层 archive SHA-256；只写“最新版”
的报告不可验收。

## 4. 测试人员准备

测试主机需要 Git、Python 3、JDK 17、Android platform-tools/build-tools，以及可见的 Android
13 设备。首先确认：

```bash
adb devices -l
```

只允许一个目标设备，或在后续命令中显式提供 `--serial`。测试人员应在批准的内部路径填写
`contracts/target-inputs.example.json` 的副本，将 `status` 改为非 `template` 状态并记录
已知 owner/策略；四个 `claim_state` 字段在证据正式评审前必须继续为 `false`。该文件不能
提交到 GitHub。

下载 Release 后先验证外层摘要：

```bash
sha256sum -c central-brain-android13-hybrid.tar.gz.sha256
tar -xzf central-brain-android13-hybrid.tar.gz
```

## 5. Dry-run 和安装

默认远程验收命令只执行 bundle 校验、B3/B4 安装预检和只读证据采集：

```bash
bash central-brain-android13-hybrid/tools/run_central_brain_android_remote_acceptance.sh \
  --bundle-dir central-brain-android13-hybrid \
  --release-tag android13-hwtest-v0.5.0-rc.1 \
  --archive-sha256 <verified-archive-sha256> \
  --target-inputs <completed-target-inputs.json> \
  --device-alias <non-secret-device-alias> \
  --evidence-reference <non-secret-internal-record-id> \
  --serial <serial>
```

在目标 owner 明确批准 debug 测试安装后，增加：

```text
--execute-install --allow-debug-signing --include-client2
```

`--execute-install` 仍先执行 dry-run；任一 checksum、API、ABI、package 或 signer 门禁失败时
不得安装。工具没有自动 uninstall、root/remount/fastboot 或系统分区写入能力。

## 6. 手工场景矩阵

安装后至少执行：

1. Demo typed Binder、replay、cancel、Governance 全部完成。
2. Client2 `care.cold`、`care.fatigue`、`task.home`、`skill.nap`。
3. `state.vehicle`、`memory.preference`、`skills.catalog`、`governance.audit`。
4. `security.denied`、`security.privacy`、`runtime.npu`、`system.overview`。
5. Runtime 缺失/恢复、快速重复点击、Runtime 进程死亡/重连、Client2 重启。
6. 目标 owner 要求的休眠/唤醒、升级和 rollback 场景。

`runtime.npu` 返回空接口状态是当前预期，不代表真实 NPU 通过。手工测试结束后再次运行上节
命令但不带 `--execute-install`，收集故障后的只读状态。

## 7. 证据和 Issue

工具生成：

```text
<output>/github-safe/summary.env
<output>/github-safe/issue-body.md
<output>/private/device.env
<output>/private/*.log
<output>/private-evidence.tar.gz
```

GitHub Issue 只允许粘贴 `github-safe/issue-body.md` 并按模板补充预期、实际和失败场景。
`private/` 与 `private-evidence.tar.gz` 默认仅保存在内网；上传前必须经过目标安全/隐私 owner
审核。严禁上传 ADB serial、原始 fingerprint、它们的派生 hash、target-input、签名材料、
未审查 logcat/dumpsys、用户或模型文本、Memory/token 或车辆 payload。Issue 只引用目标团队
分配的非敏感 evidence reference，用于从批准的内部渠道定位原始证据。

Issue 状态按 `state/triage -> state/reproduced -> state/fix-ready -> state/retest ->
state/verified` 推进。只有测试人员在 Issue 中确认具体替代 Release 已通过，才能关闭问题。

## 8. 维护者闭环

1. 按 Issue 中的 tag/commit/archive hash 定位唯一软件版本。
2. 审查 GitHub-safe 摘要；需要原始证据时走批准的内部通道，不要求公开上传。
3. 在 `codex/<issue>-<slug>` 分支修复，提交信息和 PR 引用 Issue。
4. 运行 Android 演化门禁与相关回归，发布新的 `rc.N+1`，不得替换旧 Release 资产。
5. 将 Issue 标为 `state/retest`，由原测试人员复测。
6. 复测通过后标为 `state/verified` 并关闭；失败则保留同一 Issue 的版本时间线。

GitHub Issue 本身不会自动唤醒 Codex。仓库激活后，用户需把 Issue URL/编号发送到当前任务，
或另行配置获批的 Issue 触发自动化；不得声称当前已经存在事件触发器。

## 9. 当前激活阻塞项

本地 B5 合同和工具可先交付，但实际 GitHub 闭环在以下输入完成前保持未激活：

- 私有仓库 `owner/name` 或 URL；
- 维护者写权限和 Git push 凭据；
- 测试人员 GitHub 用户列表；
- branch protection、labels 和 Release 权限；
- 首个不可变 Release；
- Issue 到 Codex 的人工或自动触发方式。

这些阻塞项不影响内网测试人员执行本地 ADB，但在解除前不能声称
`github_issue_intake_active=true`。

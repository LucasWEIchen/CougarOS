# Mock NPU Backend

`mock_npu_service.py` 是第一阶段后端，用标准库 HTTP server 模拟中央大脑 AI 基座。
`ai_sdk.py` 承载当前 AI SDK/Agent facade mock，用 intent/utterance 生成 policy-aware task graph，覆盖 XSC-001、APP-004、NV-F-001、FW-U-006、FW-U-007。
`runtime_governance.py` 承载当前 Runtime & Governance 原型，包括服务注册、发现、Policy、Lifecycle、per-service fixed-window QoS 和可选 JSONL 审计持久化。
`protocol_bindings.py` 承载当前 Protocol Binding 注册表，包括 REST active prototype、Android Binder/AIDL service stub sample、Linux IPC active sample、Linux gRPC contract skeleton 和 MQTT/SOME-IP/DDS 计划态。
`native_adapters.py` 承载当前 Native adapter 注册表，包括 AIOS Kernel、SOA Service Adapter、Vehicle Signal Adapter、Model Runtime Adapter 和 Security/Policy Adapter 的 Android/Linux 交付边界。

## 启动

```bash
bash tools/run_central_brain_backend.sh
```

默认监听：

```text
0.0.0.0:8787
```

## 接口

- `GET /health`
- `GET /services`
- `GET /uib/context`
- `GET /uib/state`
- `GET /uib/events/topics`
- `GET /uib/events/recent`
- `GET /soa/services`
- `GET /governance/runtime`
- `GET /audit/recent`
- `GET /bindings`
- `GET /bindings/detail`
- `GET /native/adapters`
- `GET /native/adapters/detail`
- `GET /ai/sdk/capabilities`
- `GET /vehicle/state`
- `GET /npu/status`
- `POST /agent/plan`
- `POST /soa/invoke`
- `POST /uib/events/publish`
- `POST /uib/actions/request`
- `POST /policy/evaluate`
- `POST /ai/infer`

旧的 `/context`、`/state`、`/actions/request`、`/service/invoke` 仍保留为兼容入口。Android 和 Linux 新样例优先使用 `/uib/*` 与 `/soa/*`，REST 在此阶段只作为 `NV-P-005` prototype binding。

## 环境变量

- `CENTRAL_BRAIN_PORT`：监听端口，默认 `8787`。
- `CENTRAL_BRAIN_NPU_VENDOR_ID`：用于模拟指定 PCIe vendor id。
- `CENTRAL_BRAIN_NPU_DEVICE`：用于标记真实或模拟 NPU 设备节点。
- `CENTRAL_BRAIN_AUDIT_LOG`：可选 JSONL 审计日志路径；设置后 `/audit/recent` 会在服务重启后恢复最近 50 条 SOA 审计记录，覆盖 XSC-005、NV-G-007、DEL-002。

当前服务只做 mock，不访问真实 NPU。

## 验证

```bash
bash tools/smoke_central_brain_qos.sh
```

该脚本验证 `POST /soa/invoke` 对 `npu-inference` 执行 NV-G-004 QoS fixed-window 限流，并把 `qos_rejected` 写入 audit。

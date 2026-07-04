# Mock NPU Backend

`mock_npu_service.py` 是第一阶段后端，用标准库 HTTP server 模拟中央大脑 AI 基座。

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
- `GET /vehicle/state`
- `GET /npu/status`
- `POST /ai/infer`

## 环境变量

- `CENTRAL_BRAIN_PORT`：监听端口，默认 `8787`。
- `CENTRAL_BRAIN_NPU_VENDOR_ID`：用于模拟指定 PCIe vendor id。
- `CENTRAL_BRAIN_NPU_DEVICE`：用于标记真实或模拟 NPU 设备节点。

当前服务只做 mock，不访问真实 NPU。

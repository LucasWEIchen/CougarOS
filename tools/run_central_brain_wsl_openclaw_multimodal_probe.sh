#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001/002, S2-OBS-001/002, S2-SAF-001,
# XSC-001/005/006, DEL-001/003/004/005. Stage: P7-R5-MMDEV.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_PATH="${1:-$ROOT_DIR/tmp/2025-SUV-OMS-cabin-photo.png}"
OPENCLAW_CONFIG="${OPENCLAW_CONFIG:-$HOME/.openclaw/openclaw.json}"

[[ -f "$IMAGE_PATH" ]] || { echo "multimodal test image is missing" >&2; exit 1; }
[[ -f "$OPENCLAW_CONFIG" ]] || { echo "OpenClaw config is missing" >&2; exit 1; }
command -v node >/dev/null || { echo "Node.js is required" >&2; exit 1; }

IMAGE_PATH="$IMAGE_PATH" OPENCLAW_CONFIG="$OPENCLAW_CONFIG" node <<'NODE'
const crypto = require("crypto");
const fs = require("fs");

const expectedDigest =
  "93441797b96c512a7b87905e4d326fbacdbf3a80e4d336d018a41224a0cd8438";
const imagePath = process.env.IMAGE_PATH;
const image = fs.readFileSync(imagePath);
const imageDigest = crypto.createHash("sha256").update(image).digest("hex");
if (imageDigest !== expectedDigest) {
  throw new Error("controlled multimodal image digest mismatch");
}
if (image.length < 8 || image.length > 6 * 1024 * 1024
    || image[0] !== 0x89 || image.subarray(1, 4).toString("ascii") !== "PNG") {
  throw new Error("controlled multimodal image envelope is invalid");
}

const config = JSON.parse(fs.readFileSync(process.env.OPENCLAW_CONFIG, "utf8"));
const token = config?.gateway?.auth?.token;
if (typeof token !== "string" || token.length < 8) {
  throw new Error("OpenClaw shared credential is unavailable");
}
let model;
for (const provider of Object.values(config?.models?.providers || {})) {
  const candidate = (provider.models || []).find(item => item.id === "qwen3.6:27b");
  if (candidate) model = candidate;
}
if (!model || !model.input?.includes("text") || !model.input?.includes("image")) {
  throw new Error("OpenClaw qwen3.6:27b must declare text and image input");
}

const sessionKey = `agent:main:cougaros-mm-${Date.now()}`;
const connectId = crypto.randomUUID();
const chatId = crypto.randomUUID();
const idempotencyKey = crypto.randomUUID();
const startedAt = Date.now();
let acknowledged = false;
let terminalText = "";
let finished = false;
const socket = new WebSocket("ws://127.0.0.1:18789/");
const timeout = setTimeout(() => fail("DEADLINE_EXCEEDED"), 180_000);

function request(id, method, params) {
  socket.send(JSON.stringify({type: "req", id, method, params}));
}

function fail(code) {
  if (finished) return;
  finished = true;
  clearTimeout(timeout);
  try { socket.close(); } catch (_) {}
  console.error(`multimodal_probe_complete=false failure_code=${code}`);
  process.exitCode = 1;
}

function messageText(message) {
  if (typeof message === "string") return message;
  if (typeof message?.text === "string") return message.text;
  if (typeof message?.content === "string") return message.content;
  if (Array.isArray(message?.content)) {
    return message.content.map(item => typeof item === "string" ? item : item?.text || "")
      .join("");
  }
  return "";
}

function validateReply(text) {
  let result;
  try {
    const object = text.match(/\{[\s\S]*\}/);
    result = JSON.parse(object ? object[0] : text);
  } catch (_) {
    return fail("STRUCTURED_REPLY_INVALID");
  }
  if (result.occupant_count !== 3
      || result.rear_passenger_holding_bottle !== true
      || result.all_visible_occupants_belted !== true) {
    return fail("VISION_ASSERTION_FAILED");
  }
  finished = true;
  clearTimeout(timeout);
  socket.close();
  console.log(`image_sha256=${imageDigest}`);
  console.log(`image_bytes=${image.length} image_mime=image/png text_present=true image_present=true`);
  console.log("occupant_count=3 rear_passenger_holding_bottle=true all_visible_occupants_belted=true");
  console.log(`model_reply_chars=${text.length} latency_ms=${Date.now() - startedAt}`);
  console.log("raw_image_logged=false raw_prompt_logged=false raw_response_logged=false credential_logged=false");
  console.log("direct_npu_accessed=false production_ready=false target_hardware_validated=false");
  console.log("multimodal_probe_complete=true implementation_stage=P7-R5-MMDEV");
}

socket.addEventListener("error", () => fail("WEBSOCKET_ERROR"));
socket.addEventListener("message", event => {
  let frame;
  try {
    frame = JSON.parse(String(event.data));
  } catch (_) {
    return fail("NON_JSON_FRAME");
  }
  if (frame.type === "event" && frame.event === "connect.challenge") {
    request(connectId, "connect", {
      minProtocol: 4,
      maxProtocol: 4,
      client: {
        id: "gateway-client",
        displayName: "CougarOS multimodal WSL probe",
        version: "0.1.0",
        platform: "linux",
        deviceFamily: "linux",
        mode: "backend",
      },
      role: "operator",
      scopes: ["operator.read", "operator.write"],
      caps: [],
      auth: {token},
      locale: "zh-CN",
      userAgent: "CougarOS-WSL-Multimodal-Probe/0.1",
    });
    return;
  }
  if (frame.type === "res" && frame.id === connectId) {
    if (!frame.ok || frame?.payload?.protocol !== 4) return fail("CONNECT_REJECTED");
    const message = "你是汽车座舱OMS多模态分析器。结合图片和本条文字，只输出一个JSON对象，键严格为occupant_count、rear_passenger_holding_bottle、all_visible_occupants_belted。值分别为整数、布尔值、布尔值。不要输出身份或其他敏感推断。";
    request(chatId, "chat.send", {
      sessionKey,
      message,
      deliver: false,
      idempotencyKey,
      attachments: [{
        type: "image",
        mimeType: "image/png",
        fileName: "2025-SUV-OMS-cabin-photo.png",
        content: image.toString("base64"),
      }],
    });
    return;
  }
  if (frame.type === "res" && frame.id === chatId) {
    if (!frame.ok) return fail("CHAT_REJECTED");
    acknowledged = true;
    return;
  }
  if (frame.type !== "event" || frame.event !== "chat") return;
  const payload = frame.payload || {};
  if (payload.sessionKey !== sessionKey) return;
  if (payload.state === "error") return fail("CHAT_TERMINAL_ERROR");
  const current = messageText(payload.message);
  if (current) terminalText = current;
  if (payload.state === "final") {
    if (!acknowledged) return fail("FINAL_BEFORE_ACK");
    if (!terminalText) return fail("EMPTY_TERMINAL_REPLY");
    validateReply(terminalText);
  }
});
NODE

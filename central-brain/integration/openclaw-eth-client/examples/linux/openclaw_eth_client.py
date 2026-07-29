#!/usr/bin/env python3
"""Business-neutral OpenClaw protocol 3 client for a directly attached ETH node."""

from __future__ import annotations

import argparse
import base64
import json
import mimetypes
import os
import re
import sys
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import websocket


PROTOCOL_VERSION = 3
DEFAULT_ENDPOINT = "ws://169.254.208.110:18789/"
MAX_TEXT_BYTES = 16 * 1024
MAX_IMAGE_BYTES = 6 * 1024 * 1024
MAX_PREAUTH_FRAME_BYTES = 65_536
MAX_MULTIMODAL_FRAME_BYTES = 8_500_000
MAX_INBOUND_FRAME_BYTES = 1_048_576
FILE_NAME_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$")


class OpenClawError(RuntimeError):
    """Bounded protocol or transport failure."""


@dataclass(frozen=True)
class ImageInput:
    mime_type: str
    file_name: str
    content: bytes

    @classmethod
    def from_path(cls, path: Path) -> "ImageInput":
        if not path.is_file():
            raise OpenClawError(f"image does not exist: {path}")
        size = path.stat().st_size
        if size < 1 or size > MAX_IMAGE_BYTES:
            raise OpenClawError("image must contain 1..6291456 bytes")
        content = path.read_bytes()
        mime_type = mimetypes.guess_type(path.name)[0]
        if mime_type not in ("image/png", "image/jpeg"):
            raise OpenClawError("image extension must identify PNG or JPEG")
        file_name = path.name
        if not FILE_NAME_PATTERN.fullmatch(file_name):
            raise OpenClawError("image file name is invalid")
        _require_image_signature(mime_type, content)
        return cls(mime_type=mime_type, file_name=file_name, content=content)


class OpenClawEthClient:
    def __init__(
        self,
        endpoint: str,
        token: str,
        connect_timeout_s: float = 3.0,
        request_timeout_s: float = 120.0,
    ) -> None:
        parsed = urlparse(endpoint)
        if (
            parsed.scheme != "ws"
            or parsed.hostname != "169.254.208.110"
            or parsed.port != 18789
            or parsed.path != "/"
            or parsed.params
            or parsed.query
            or parsed.fragment
        ):
            raise OpenClawError(
                "endpoint must be ws://169.254.208.110:18789/"
            )
        if (
            not isinstance(token, str)
            or not 8 <= len(token) <= 256
            or any(ord(character) < 0x21 or ord(character) > 0x7E for character in token)
        ):
            raise OpenClawError("OpenClaw token is invalid")
        if connect_timeout_s <= 0:
            raise OpenClawError("connect timeout must be positive")
        if request_timeout_s <= 0 or request_timeout_s > 120:
            raise OpenClawError("request timeout must be in (0, 120]")
        self.endpoint = endpoint
        self.origin = f"http://{parsed.hostname}:{parsed.port}"
        self.token = token
        self.connect_timeout_s = connect_timeout_s
        self.request_timeout_s = request_timeout_s

    def query(self, text: str, image: ImageInput | None = None) -> str:
        text = _require_text(text)
        deadline = time.monotonic() + self.request_timeout_s
        host = urlparse(self.endpoint).hostname
        ws = websocket.create_connection(
            self.endpoint,
            timeout=self.connect_timeout_s,
            origin=self.origin,
            http_no_proxy=[host],
            enable_multithread=True,
        )
        try:
            _stage("WEBSOCKET_OPEN")
            ws.settimeout(_remaining(deadline))
            challenge = _receive_json(ws, deadline)
            if not _is_event(challenge, "connect.challenge"):
                raise OpenClawError("OpenClaw connect challenge is missing")
            nonce = _object(challenge, "payload").get("nonce")
            if not isinstance(nonce, str) or not nonce:
                raise OpenClawError("OpenClaw challenge nonce is missing")
            _stage("CHALLENGE_RECEIVED")

            connect_id = str(uuid.uuid4())
            connect_params = {
                "minProtocol": PROTOCOL_VERSION,
                "maxProtocol": PROTOCOL_VERSION,
                "client": {
                    "id": "openclaw-control-ui",
                    "version": "eth-debug-client/1.0",
                    "platform": "linux",
                    "mode": "webchat",
                },
                "role": "operator",
                "scopes": ["operator.read", "operator.write"],
                "caps": [],
                "auth": {"token": self.token},
                "locale": "zh-CN",
                "userAgent": "OpenClaw-Eth-Debug/1.0",
            }
            _send_json(
                ws,
                _rpc_request(connect_id, "connect", connect_params),
                MAX_PREAUTH_FRAME_BYTES,
            )
            _stage("CONNECT_SENT")
            connect_response = _read_matching_response(ws, connect_id, deadline)
            _require_ok(connect_response, "OpenClaw authentication rejected")
            protocol = _object(connect_response, "payload").get("protocol")
            if protocol != PROTOCOL_VERSION:
                raise OpenClawError(f"OpenClaw protocol mismatch: {protocol!r}")
            _stage("AUTHENTICATED")

            session_key = f"agent:main:eth-debug-{uuid.uuid4().hex}"
            idempotency_key = str(uuid.uuid4())
            chat_id = str(uuid.uuid4())
            chat_params: dict[str, Any] = {
                "sessionKey": session_key,
                "message": text,
                "deliver": False,
                "idempotencyKey": idempotency_key,
            }
            frame_limit = MAX_PREAUTH_FRAME_BYTES
            if image is not None:
                chat_params["attachments"] = [
                    {
                        "type": "image",
                        "mimeType": image.mime_type,
                        "fileName": image.file_name,
                        "content": base64.b64encode(image.content).decode("ascii"),
                    }
                ]
                frame_limit = MAX_MULTIMODAL_FRAME_BYTES
            _send_json(
                ws,
                _rpc_request(chat_id, "chat.send", chat_params),
                frame_limit,
            )
            _stage("CHAT_SENT")
            return self._read_chat(ws, chat_id, session_key, deadline)
        except websocket.WebSocketTimeoutException as failure:
            raise OpenClawError("OpenClaw request deadline exceeded") from failure
        except websocket.WebSocketException as failure:
            raise OpenClawError("OpenClaw WebSocket transport failed") from failure
        finally:
            try:
                ws.close(status=1000, reason="complete")
            except websocket.WebSocketException:
                pass

    def _read_chat(
        self,
        ws: websocket.WebSocket,
        chat_id: str,
        session_key: str,
        deadline: float,
    ) -> str:
        acknowledged = False
        final_seen = False
        run_id = ""
        event_run_id = ""
        streamed_text = ""
        final_text = ""

        while True:
            frame = _receive_json(ws, deadline)
            if _is_response(frame, chat_id):
                _require_ok(frame, "OpenClaw chat request rejected")
                acknowledged = True
                payload = frame.get("payload")
                if isinstance(payload, dict) and isinstance(payload.get("runId"), str):
                    run_id = payload["runId"]
                if run_id and event_run_id and run_id != event_run_id:
                    raise OpenClawError("OpenClaw runId changed during request")
                _stage("CHAT_ACKNOWLEDGED")
            elif _is_event(frame, "chat"):
                payload = frame.get("payload")
                if not isinstance(payload, dict):
                    continue
                if payload.get("sessionKey") != session_key:
                    continue
                candidate_run_id = payload.get("runId")
                if isinstance(candidate_run_id, str) and candidate_run_id:
                    if run_id and candidate_run_id != run_id:
                        continue
                    if event_run_id and candidate_run_id != event_run_id:
                        continue
                    event_run_id = candidate_run_id
                state = payload.get("state")
                if state == "delta":
                    streamed_text = _merge_text(
                        streamed_text, _extract_text(payload.get("message"))
                    )
                    if streamed_text:
                        _stage(f"DELTA chars={len(streamed_text)}")
                elif state == "final":
                    final_seen = True
                    terminal_text = _extract_text(payload.get("message"))
                    final_text = terminal_text or streamed_text
                    _stage("FINAL_RECEIVED")
                elif state == "error":
                    raise OpenClawError("OpenClaw returned a terminal chat error")

            if acknowledged and final_seen:
                if not final_text:
                    raise OpenClawError("OpenClaw final reply is empty")
                return final_text


def _read_matching_response(
    ws: websocket.WebSocket, request_id: str, deadline: float
) -> dict[str, Any]:
    while True:
        frame = _receive_json(ws, deadline)
        if _is_response(frame, request_id):
            return frame


def _send_json(
    ws: websocket.WebSocket, value: dict[str, Any], maximum_bytes: int
) -> None:
    encoded = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode(
        "utf-8"
    )
    if not encoded or len(encoded) > maximum_bytes:
        raise OpenClawError("OpenClaw outbound frame is too large")
    ws.send(encoded.decode("utf-8"))


def _receive_json(
    ws: websocket.WebSocket, deadline: float
) -> dict[str, Any]:
    ws.settimeout(_remaining(deadline))
    raw = ws.recv()
    if isinstance(raw, bytes):
        try:
            raw = raw.decode("utf-8", errors="strict")
        except UnicodeDecodeError as failure:
            raise OpenClawError("OpenClaw frame is not valid UTF-8") from failure
    if not isinstance(raw, str):
        raise OpenClawError("OpenClaw returned a non-text frame")
    if len(raw.encode("utf-8")) > MAX_INBOUND_FRAME_BYTES:
        raise OpenClawError("OpenClaw inbound frame is too large")
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as failure:
        raise OpenClawError("OpenClaw returned invalid JSON") from failure
    if not isinstance(value, dict):
        raise OpenClawError("OpenClaw frame must be a JSON object")
    return value


def _rpc_request(
    request_id: str, method: str, params: dict[str, Any]
) -> dict[str, Any]:
    return {
        "type": "req",
        "id": request_id,
        "method": method,
        "params": params,
    }


def _is_response(frame: dict[str, Any], request_id: str) -> bool:
    return frame.get("type") == "res" and frame.get("id") == request_id


def _is_event(frame: dict[str, Any], event: str) -> bool:
    return frame.get("type") == "event" and frame.get("event") == event


def _require_ok(frame: dict[str, Any], prefix: str) -> None:
    if frame.get("ok") is True:
        return
    error = frame.get("error")
    code = _safe_error(error.get("code")) if isinstance(error, dict) else "UNKNOWN"
    message = _safe_error(error.get("message")) if isinstance(error, dict) else ""
    raise OpenClawError(f"{prefix} code={code} message={message}")


def _object(value: dict[str, Any], field: str) -> dict[str, Any]:
    child = value.get(field)
    if not isinstance(child, dict):
        raise OpenClawError(f"OpenClaw field is not an object: {field}")
    return child


def _extract_text(message: Any) -> str:
    if isinstance(message, str):
        return message
    if not isinstance(message, dict):
        return ""
    if isinstance(message.get("text"), str):
        return message["text"]
    content = message.get("content")
    if isinstance(content, str):
        return content
    if not isinstance(content, list):
        return ""
    return "".join(
        part["text"]
        for part in content
        if isinstance(part, dict)
        and part.get("type") == "text"
        and isinstance(part.get("text"), str)
    )


def _merge_text(accumulated: str, incoming: str) -> str:
    if not incoming:
        return accumulated
    if incoming.startswith(accumulated):
        return incoming
    if accumulated.endswith(incoming):
        return accumulated
    return accumulated + incoming


def _require_text(value: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise OpenClawError("text query is empty")
    if len(value.encode("utf-8")) > MAX_TEXT_BYTES:
        raise OpenClawError("text query exceeds 16 KiB")
    return value


def _require_image_signature(mime_type: str, content: bytes) -> None:
    png = content.startswith(b"\x89PNG\r\n\x1a\n")
    jpeg = (
        len(content) >= 4
        and content.startswith(b"\xff\xd8")
        and content.endswith(b"\xff\xd9")
    )
    if (mime_type == "image/png" and not png) or (
        mime_type == "image/jpeg" and not jpeg
    ):
        raise OpenClawError("image signature does not match MIME")


def _remaining(deadline: float) -> float:
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise OpenClawError("OpenClaw request deadline exceeded")
    return remaining


def _safe_error(value: Any) -> str:
    if not isinstance(value, str):
        return "UNKNOWN"
    return re.sub(r"[^A-Za-z0-9_.: -]", "_", value)[:160]


def _stage(value: str) -> None:
    print(f"openclaw_stage={value}", file=sys.stderr, flush=True)


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Send a text or text+image query to OpenClaw over Ethernet."
    )
    parser.add_argument("--endpoint", default=DEFAULT_ENDPOINT)
    parser.add_argument("--text", required=True)
    parser.add_argument("--image", type=Path)
    parser.add_argument("--timeout", type=float, default=120.0)
    return parser.parse_args()


def main() -> int:
    args = _parse_args()
    token = os.environ.get("OPENCLAW_TOKEN")
    if not token:
        print("OPENCLAW_TOKEN is not set", file=sys.stderr)
        return 2
    try:
        image = ImageInput.from_path(args.image) if args.image else None
        client = OpenClawEthClient(
            endpoint=args.endpoint,
            token=token,
            request_timeout_s=args.timeout,
        )
        reply = client.query(args.text, image)
    except OpenClawError as failure:
        print(f"openclaw_error={failure}", file=sys.stderr)
        return 1
    print(reply)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

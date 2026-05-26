---
title: Native Client Pairing
description: How Android and iOS apps pair with MotionCam and run JavaScript automation.
---

This guide is for Android and iOS apps that want to control MotionCam directly over the local remote-control protocol.

A native client does three things:

1. Read the pairing details from the MotionCam screen or QR code.
2. Open the advertised WebSocket connection and authenticate with `auth.hello`.
3. Send protocol methods such as `script.run`, `state.get`, `lens.list`, `profile.list`, `camera.list`, `capture.setMode`, `capture.start`, `capture.stop`, or direct `camera.*` commands.

## Pairing Inputs

When the user enables Remote Control in MotionCam, the app shows manual pairing details and may show a QR code.

Manual pairing uses the short on-screen `pairingCode`. QR pairing uses a separate high-entropy `pairingToken` carried in the QR payload.

QR payload:

```json
{
  "v": 1,
  "name": "MotionCam",
  "url": "wss://192.168.1.23:8765",
  "pairingToken": "q4Y...high-entropy-token...",
  "expiresAt": 1780000000000,
  "certSha256": "AA:BB:CC:..."
}
```

Fields:

- `v`: required number. Current payload version is `1`.
- `name`: required string. Display label for the service.
- `url`: required string. WebSocket URL to connect to, usually `wss://host:8765`.
- `pairingToken`: required string. High-entropy QR pairing secret to send in `auth.hello`.
- `expiresAt`: optional number. Unix epoch time in milliseconds when the pairing secret expires.
- `certSha256`: optional string. Colon-separated uppercase SHA-256 fingerprint for the WSS certificate.

Treat the QR payload, pairing token, and manual pairing code as secrets. Do not store either pairing secret as a durable credential.

## Native Pairing Flow

1. Ask the user to enable Remote Control in MotionCam.
2. Scan the QR code, or let the user type the address and manual pairing code.
3. If `expiresAt` exists and is in the past, ask the user to regenerate the pairing details in MotionCam.
4. Open a WebSocket to `url`.
5. If the URL uses `wss://` and `certSha256` is present, verify the server certificate fingerprint during TLS setup.
6. Send `auth.hello` as the first protocol request.
7. Wait for the auth response and `session.opened` event.
8. Send automation requests.
9. Close the WebSocket when finished, or ask the user to disable Remote Control in MotionCam.

Authentication request:

```json
{
  "id": "auth-1",
  "method": "auth.hello",
  "params": {
    "clientName": "my-controller",
    "protocolVersion": 1,
    "pairingToken": "q4Y...high-entropy-token..."
  }
}
```

For manual pairing, send the on-screen code instead:

```json
{
  "id": "auth-1",
  "method": "auth.hello",
  "params": {
    "clientName": "my-controller",
    "protocolVersion": 1,
    "pairingCode": "123456"
  }
}
```

Authentication response:

```json
{
  "id": "auth-1",
  "result": {
    "protocolVersion": 1,
    "authMode": "debug-pairing",
    "serverName": "MotionCam",
    "clientName": "my-controller",
    "capabilities": ["ping", "state.get", "camera.list", "lens.list", "profile.list", "capture.setMode", "capture.start", "capture.stop", "script.run"]
  }
}
```

Session event:

```json
{
  "event": "session.opened",
  "params": {
    "id": "session-id",
    "clientName": "my-controller",
    "startedAtMs": 1780000000000
  }
}
```

## Running JavaScript

After authentication, call `script.run` with JavaScript source.

```json
{
  "id": "script-1",
  "method": "script.run",
  "params": {
    "source": "JSON.stringify({ iso: motioncam.camera.iso, shutterNs: motioncam.camera.shutterNs })"
  }
}
```

Response:

```json
{
  "id": "script-1",
  "result": {
    "value": "{\"iso\":400,\"shutterNs\":10000000}"
  }
}
```

`result.value` is always a string. Return `JSON.stringify(...)` from scripts when your app needs structured data.

## Direct Camera Commands

Native apps do not have to use JavaScript for simple camera controls. The protocol also exposes direct methods:

```json
{
  "id": "iso-1",
  "method": "camera.setIso",
  "params": {
    "iso": 800
  }
}
```

Response:

```json
{
  "id": "iso-1",
  "result": {
    "ok": true
  }
}
```

Use `state.get` to read runtime state without evaluating JavaScript. Use `lens.list` for the app-facing lens catalog, `profile.list` for a flattened profile view, `camera.list` to discover underlying cameras and supported control ranges, `capture.setMode` to select the current mode, and `capture.start` / `capture.stop` to control capture without evaluating JavaScript.

## Android Notes

Use a WebSocket client that lets you control TLS validation, such as OkHttp.

For `wss://` with MotionCam's self-signed certificate, the platform trust store will not trust the certificate by default. A native client should pin the certificate fingerprint from the QR payload during pairing:

1. During TLS handshake, read the leaf server certificate.
2. Compute SHA-256 over the DER-encoded certificate bytes.
3. Format or compare it against `certSha256`.
4. Accept the connection only if it matches.

Do not disable certificate validation globally. If you provide an insecure lab mode, make it explicit in your UI and use it only with `ws://` on trusted test networks.

## iOS Notes

Use `URLSessionWebSocketTask` or another WebSocket stack that exposes server trust evaluation.

For `wss://` with MotionCam's self-signed certificate, implement trust handling in the session delegate:

1. Inspect the server trust challenge for the WebSocket connection.
2. Extract the leaf certificate data.
3. Compute SHA-256 over the DER certificate data.
4. Compare it to `certSha256` from the QR payload.
5. Accept the challenge only if it matches.

Do not add broad exceptions that trust every self-signed certificate. Trust only the certificate fingerprint paired from the MotionCam screen.

## Lifecycle And Failure Handling

A robust client should handle these cases:

- Pairing secret expired: `auth.hello` returns `UNAUTHORIZED`; ask the user to regenerate pairing details.
- Wrong pairing code or token: retry only a small number of times; the server closes the connection after three failures.
- Another client connected: the server returns `BUSY` or closes the second connection.
- Remote disabled or app paused: the socket may close; return to a disconnected state.
- Script error: `script.run` returns `SCRIPT_ERROR`; show the message and stack to the developer.
- Preview unavailable: `preview.start` may fail or emit `preview.error`.

Clients should reconnect only after explicit user action when authentication fails. Do not repeatedly brute-force or replay pairing codes or tokens.

## Minimal Client State Machine

```text
idle
  -> scan_or_enter_pairing
  -> connect_websocket
  -> authenticate
  -> connected
  -> run_commands
  -> close
```

On any auth error, close the WebSocket and return to `scan_or_enter_pairing`. On normal socket close, return to `idle` or prompt the user to reconnect.

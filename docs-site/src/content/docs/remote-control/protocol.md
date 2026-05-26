---
title: Remote Control Protocol
description: JSON protocol for running MotionCam automation commands from remote clients.
---

The remote-control protocol is a JSON request/response protocol for automation clients. The current implementation uses a WebSocket server in the app and routes script execution through the same automation layer as local scripting. Preview frames use binary WebSocket messages on the same authenticated connection.

Remote control is user-enabled from MotionCam. When it is active, the app shows the connection address, pairing code, optional QR code, transport security information, and a visible remote-control indicator.

## Transport

The server listens on port `8765` and advertises an address such as:

```text
wss://192.168.1.23:8765
```

By default, MotionCam uses `wss://` with an app-generated self-signed TLS certificate. The app shows the certificate SHA-256 fingerprint so native clients can pin the certificate during pairing.

For lab testing only, the user can enable insecure transport in Remote settings. In that mode the advertised URL uses `ws://` and traffic is not encrypted.

Browser clients cannot silently trust a self-signed local `wss://` certificate. Browser-based tooling should either use the insecure lab mode on a trusted test network, a loopback/ADB workflow, or a future browser-compatible trust path.

## Pairing Details

The manual pairing code is shown only in the local app UI. The QR payload uses a separate high-entropy `pairingToken` instead of embedding the short manual code. Neither secret is written to the automation console or sent to already authenticated remote clients.

Pairing secrets are short-lived. The current implementation expires them after 5 minutes, rotates them after successful authentication, and lets the user regenerate them manually. Regenerating the pairing details closes any unauthenticated connection.

The QR payload is JSON:

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
- `name`: required string. Display name for the service.
- `url`: required string. WebSocket URL to connect to.
- `pairingToken`: required string. High-entropy QR pairing secret to send in `auth.hello`.
- `expiresAt`: optional number. Unix epoch time in milliseconds when the pairing secret expires.
- `certSha256`: optional string. Colon-separated uppercase SHA-256 fingerprint of the WSS certificate.

## Connection Model

Only one WebSocket connection can own the active remote-control session. If a second client connects while another connection is active, the server returns `BUSY` and closes the second connection.

Clients must authenticate with `auth.hello` before any other protocol method is accepted. After three failed authentication attempts on one connection, the server closes that connection.

## Message Envelope

Client requests are JSON objects:

```json
{
  "id": "1",
  "method": "auth.hello",
  "params": {
    "clientName": "remote-lab",
    "protocolVersion": 1,
    "pairingToken": "q4Y...high-entropy-token..."
  }
}
```

Responses use the same `id`. Method sections below usually show the `result` object for readability; the actual WebSocket response wraps it in this envelope:

```json
{
  "id": "1",
  "result": {}
}
```

Errors use an `error` object:

```json
{
  "id": "1",
  "error": {
    "code": "SCRIPT_ERROR",
    "message": "ReferenceError: missing is not defined"
  }
}
```

Events do not include an `id`:

```json
{
  "event": "console.entry",
  "params": {}
}
```

## Methods

All methods except `auth.hello` require authentication.

### `auth.hello`

Authenticates the WebSocket connection and opens the automation session.

```json
{
  "id": "1",
  "method": "auth.hello",
  "params": {
    "clientName": "remote-lab",
    "protocolVersion": 1,
    "pairingCode": "123456"
  }
}
```

Example result:

```json
{
  "protocolVersion": 1,
  "authMode": "debug-pairing",
  "serverName": "MotionCam",
  "clientName": "remote-lab",
  "capabilities": [
    "ping",
    "auth.hello",
    "state.get",
    "camera.list",
    "lens.list",
    "profile.list",
    "profile.select",
    "capture.setMode",
    "capture.start",
    "capture.stop",
    "preview.start",
    "preview.stop",
    "script.run",
    "console.clear",
    "camera.setIso"
  ]
}
```

Rules:

- `clientName`, `protocolVersion`, and one pairing secret are required.
- `protocolVersion` must be `1`.
- Use `pairingCode` for manually typed pairing, or `pairingToken` when authenticating from the QR payload.
- `pairingCode` must match the current on-screen pairing code, or `pairingToken` must match the current QR token. The selected secret must not be expired.
- A successful pairing rotates both secrets so neither can be reused.
- After three failed authentication attempts on one connection, the server closes the connection.
- A successful auth also emits `session.opened`.

### `ping`

Checks that an authenticated connection is alive.

```json
{
  "id": "2",
  "method": "ping"
}
```

Example result:

```json
{
  "ok": true
}
```

### `state.get`

Returns the current runtime state without evaluating JavaScript.

```json
{
  "id": "3",
  "method": "state.get"
}
```

Example result:

```json
{
  "schemaVersion": 1,
  "version": 42,
  "timestampMs": 1779360000000,
  "app": {
    "captureMode": "VIDEO"
  },
  "camera": {
    "id": "0",
    "lensId": "back_24mm",
    "profileId": "profile-1",
    "controllerState": "ACTIVE",
    "ready": true,
    "running": true,
    "previewRunning": true,
    "controls": {
      "focus": {
        "manual": true,
        "distanceDiopters": 2.0
      },
      "exposure": {
        "isoManual": true,
        "iso": 400,
        "shutterManual": true,
        "shutterNs": 10000000
      },
      "whiteBalance": {
        "manual": false,
        "temperature": 5600,
        "tint": 0
      }
    }
  },
  "recording": {
    "active": false,
    "state": "STOPPED",
    "finalizing": false
  }
}
```

### `capture.setMode`

Selects the app capture mode through the same command path used by the in-app UI.

```json
{
  "id": "4",
  "method": "capture.setMode",
  "params": {
    "mode": "LOG_VIDEO"
  }
}
```

`mode` is a string enum. Supported values are `ZSL`, `BURST`, `RAW_VIDEO`, `LOG_VIDEO`, and `TIMELAPSE`.

### `capture.start`

Requests capture in the current capture mode. In photo modes this requests one capture. In video modes this requests recording start. The response is sent when the app accepts the request; use `state.get` to observe the resulting capture or recording state.

```json
{
  "id": "5",
  "method": "capture.start"
}
```

### `capture.stop`

Requests the current video/timelapse recording to stop. The response is sent when the app accepts the request; use `state.get` to observe finalization and the stopped state. Photo captures are not stoppable through this command.

```json
{
  "id": "6",
  "method": "capture.stop"
}
```

### `camera.list`

Returns discovered cameras and static control ranges. Fields that MotionCam cannot resolve are returned as `null`.

```json
{
  "id": "4",
  "method": "camera.list"
}
```

Example result:

```json
{
  "cameras": [
    {
      "id": "0",
      "physicalCameraId": null,
      "active": true,
      "logical": false,
      "frontFacing": false,
      "requiresJavaBackend": false,
      "sensorOrientation": 90,
      "isoRange": [100, 3200],
      "exposureTimeRangeNs": [1000000, 30000000000],
      "focalLengths": [24.0],
      "apertures": [1.8],
      "minFocusDistance": 0.0,
      "maxFocusDistance": 0.01,
      "hyperFocalDistance": 0.05,
      "oisSupported": true,
      "zoomRange": [1.0, 8.0],
      "exposureCompensationRange": [-12, 12],
      "exposureCompensationStep": [1, 3],
      "fpsRange": [24, 60]
    }
  ]
}
```

### `profile.list`

Returns a flattened view of the user-facing lens catalog. Profiles are the app selection model; use `cameraId` to join a profile to `camera.list` metadata.

```json
{
  "id": "5",
  "method": "profile.list"
}
```

Example result:

```json
{
  "profiles": [
    {
      "id": "profile-1",
      "lensId": "back_24mm",
      "cameraId": "0",
      "name": "Default",
      "active": true,
      "default": true,
      "autoGenerated": true
    }
  ]
}
```

### `lens.list`

Returns the same app-facing lens catalog used by MotionCam's lens picker.

```json
{
  "id": "6",
  "method": "lens.list"
}
```

Example result:

```json
{
  "lenses": [
    {
      "id": "back_24mm",
      "label": "24mm",
      "buttonPrimaryLabel": "24mm",
      "buttonSecondaryLabel": "1X",
      "frontFacing": false,
      "fallbackCameraId": "0",
      "focalLengthMm": 24,
      "active": true,
      "defaultProfileId": "profile-1",
      "activeProfileId": "profile-1",
      "profiles": [
        {
          "id": "profile-1",
          "lensId": "back_24mm",
          "cameraId": "0",
          "name": "Default",
          "active": true,
          "default": true,
          "autoGenerated": true
        }
      ]
    }
  ]
}
```

### `profile.select`

Selects an available lens profile using the same command path as the in-app lens picker.

```json
{
  "id": "7",
  "method": "profile.select",
  "params": {
    "profileId": "profile-1"
  }
}
```

Example result:

```json
{
  "ok": true
}
```

### `script.run`

Runs JavaScript inside the active automation session.

```json
{
  "id": "6",
  "method": "script.run",
  "params": {
    "source": "motioncam.camera.iso"
  }
}
```

Example result:

```json
{
  "value": "400"
}
```

Rules:

- `params.source` is required and must be a string.
- The script result is returned as a string.
- `undefined` and `null` return as an empty string.
- Script execution failures return `SCRIPT_ERROR`.
- Use `JSON.stringify(...)` in the script when the client needs structured data.

### `preview.start`

Starts a low-resolution JPEG preview stream over binary WebSocket frames. Only one preview stream is active per connection; starting a new stream replaces the previous one.

```json
{
  "id": "5",
  "method": "preview.start",
  "params": {
    "maxWidth": 640,
    "quality": 65,
    "fps": 2
  }
}
```

Example result:

```json
{
  "streamId": "preview-41cdb5c6e97a83f0",
  "mimeType": "image/jpeg",
  "transport": "websocket-binary",
  "frameFormat": "mcpv1",
  "maxWidth": 640,
  "quality": 65,
  "fps": 2
}
```

Rules:

- `maxWidth` is optional, defaults to `640`, and must be between `160` and `1280`.
- `quality` is optional, defaults to `65`, and must be between `1` and `90`.
- `fps` is optional, defaults to `2`, and must be between `1` and `10`.
- The stream is tied to the authenticated socket and stops when the socket closes.
- The server captures the next frame only after the previous capture has completed, so the requested FPS is an upper bound.

Binary frame format:

```text
0..3      ASCII "MCPV"
4         version, currently 1
5         type, 1 = JPEG frame
6..7      UTF-8 JSON header length, uint16 big-endian
8..N      UTF-8 JSON header
N..end    JPEG bytes
```

Frame header:

```json
{
  "streamId": "preview-41cdb5c6e97a83f0",
  "sequence": 42,
  "mimeType": "image/jpeg",
  "width": 640,
  "height": 360,
  "timestampMs": 1780000000000
}
```

Preview events:

```json
{
  "event": "preview.error",
  "params": {
    "streamId": "preview-41cdb5c6e97a83f0",
    "message": "Preview surface is not available"
  }
}
```

### `preview.stop`

Stops the active preview stream.

```json
{
  "id": "6",
  "method": "preview.stop"
}
```

### `console.clear`

Clears the automation console.

```json
{
  "id": "7",
  "method": "console.clear"
}
```

### Camera Methods

Camera methods drive the app control surface directly and return `{ "ok": true }` after the command completes.

- `camera.setIso`: `{ "iso": 400 }`
- `camera.setShutterNs`: `{ "shutterNs": 10000000 }`
- `camera.setFocusDistance`: `{ "diopters": 2.0 }`
- `camera.setWhiteBalance`: `{ "temperature": 5600, "tint": 0 }`
- `camera.setExposureLock`: `{ "enabled": true }`
- `camera.setIsoLock`: `{ "enabled": true }`
- `camera.setShutterLock`: `{ "enabled": true }`
- `camera.setFocusLock`: `{ "enabled": true }`
- `camera.setAutoWhiteBalanceLock`: `{ "enabled": true }`
- `camera.toggleAutoWhiteBalanceLock`
- `camera.setIsoAuto`
- `camera.setShutterAuto`
- `camera.setFocusAuto`
- `camera.setWhiteBalanceAuto`
- `camera.resetManual`

Example:

```json
{
  "id": "8",
  "method": "camera.setIso",
  "params": {
    "iso": 800
  }
}
```

Success result:

```json
{
  "id": "8",
  "result": {
    "ok": true
  }
}
```

## Console Events

After authentication, console entries are emitted as events:

```json
{
  "event": "console.entry",
  "params": {
    "timestampMs": 1780000000000,
    "level": "log",
    "source": "script",
    "message": "test"
  }
}
```

Console clear:

```json
{
  "event": "console.cleared",
  "params": {}
}
```

## Error Codes

- `INVALID_REQUEST`: malformed JSON, missing fields, or invalid field types.
- `UNKNOWN_METHOD`: method name is not supported.
- `INVALID_ARGUMENT`: method params are syntactically valid but semantically bad.
- `INVALID_STATE`: command cannot run in the current app, session, or camera state.
- `UNAUTHORIZED`: client is not paired, approved, or allowed to perform the action.
- `BUSY`: another automation session owns the app or another remote socket is already connected.
- `SCRIPT_ERROR`: JavaScript execution failed.
- `INTERNAL_ERROR`: unexpected implementation failure.

## Security Notes

Remote control exposes camera control, preview streaming, console access, and JavaScript execution. Treat the manual pairing code, QR pairing token, and full QR payload as secrets.

Recommended client behavior:

- Connect only after the user intentionally enables Remote Control in the app.
- Prefer `wss://` and verify the displayed or QR-provided certificate fingerprint when your platform allows pinning.
- Use insecure `ws://` only for controlled lab testing.
- Do not persist pairing codes or tokens as long-term credentials.
- Stop remote control from the app when the session is finished.

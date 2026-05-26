---
title: JavaScript Engine
description: How MotionCam scripts execute locally and remotely.
---

MotionCam scripts execute inside an embedded QuickJS runtime. The native engine installs a small host bridge, then exposes the public API on `globalThis.motioncam`.

The same JavaScript API is used by local scripts and remote `script.run` requests.

## Running Scripts

### Local Console

The in-app script console is intended for local development and testing. It opens an automation session named `debug-console` while visible and closes that session when the console is hidden.

Local console execution is disabled while another automation session is active, such as a paired remote-control client.

### Remote Control

Remote clients connect to MotionCam's remote-control WebSocket server and authenticate with the on-screen pairing details before calling `script.run`.

A remote script runs in the active remote automation session and has the same `motioncam` API as a local script. Console output from the script is forwarded to the authenticated remote connection as `console.entry` events.

See [Remote Control Protocol](/remote-control/protocol/) for the connection and message format.

## Execution Model

Each `MotionCamScriptEngine` instance owns one native QuickJS runtime and context. Calls to `run(source)` are queued onto a single background thread named `MotionCamScriptEngine`, so scripts in the same automation session do not execute concurrently.

The native bridge enforces a 30 second script timeout. Camera host commands wait up to 5 seconds for the app command to complete. If QuickJS or a host command fails, MotionCam returns a `MotionCamScriptException` with the JavaScript error and stack when available.

## Global Objects

The runtime exposes these globals:

- `motioncam`: the public MotionCam automation API.
- `console`: the same console sink as `motioncam.console`.

The implementation also exposes an internal `__motioncamHost` object. Treat it as private. Scripts should use `motioncam` and `console` only.

## Runtime Limits

The scripting runtime is not a browser or Node.js environment.

Available:

- Standard JavaScript supported by QuickJS.
- `motioncam` camera state and control APIs.
- `console.log`, `console.warn`, `console.error`, and `console.clear`.
- Synchronous JavaScript execution inside one automation session.

Not available unless explicitly added later:

- DOM APIs such as `window`, `document`, or canvas.
- Browser networking APIs such as `fetch`, `WebSocket`, or `XMLHttpRequest`.
- Node.js APIs such as `require`, `process`, `fs`, or npm packages.
- Timers such as `setTimeout` and `setInterval`.
- ES module imports.

## State Snapshots

`motioncam.state`, `motioncam.getState()`, `motioncam.cameras`, `motioncam.getCameras()`, `motioncam.lenses`, `motioncam.getLenses()`, `motioncam.profiles`, `motioncam.getProfiles()`, and camera property getters ask the Android host for the latest runtime data. They do not return live objects.

```js
const state = motioncam.getState();

console.log(state.app.captureMode);
console.log(state.camera.controls.exposure.iso);
```

If you need a consistent snapshot across several reads, store `motioncam.getState()` once and read from that object.

```js
const state = motioncam.getState();
const summary = {
  mode: state.app.captureMode,
  iso: state.camera.controls.exposure.iso,
  shutterNs: state.camera.controls.exposure.shutterNs,
};
JSON.stringify(summary);
```

Use `motioncam.lenses` or `motioncam.getLenses()` to discover the app-facing lens catalog. Use `motioncam.profiles` or `motioncam.getProfiles()` for the flattened profile view, and `motioncam.cameras` or `motioncam.getCameras()` when a script needs low-level camera capability ranges.

```js
JSON.stringify(motioncam.lenses, null, 2);
```

## Camera Commands

Camera commands delegate to the app's `MotionCamControlSurface`. Commands are asynchronous inside the app, but the script host waits for each command to finish before returning to JavaScript.

```js
motioncam.camera.setIso(800);
motioncam.camera.setShutterNs(10_000_000);
motioncam.camera.setIsoLock(true);
```

Command calls return `undefined` when they succeed. A command failure throws and stops the current script unless you catch it.

```js
try {
  motioncam.camera.setFocusDistance(2.0);
  console.log('Focus updated');
} catch (error) {
  console.error(error);
}
```

## Return Values

The JNI bridge converts the JavaScript result to a string before returning it to Java or the remote protocol.

```js
motioncam.camera.iso
```

The remote protocol returns this as:

```json
{
  "value": "400"
}
```

Objects are converted through JavaScript string conversion, so return `JSON.stringify(...)` when a remote client needs structured data.

```js
JSON.stringify(motioncam.state)
```

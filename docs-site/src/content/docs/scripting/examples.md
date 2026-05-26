---
title: Examples
description: Practical MotionCam JavaScript automation snippets.
---

These examples run in either the local script console or remote `script.run`, unless noted otherwise.

## Print Current Camera State

```js
console.log(JSON.stringify(motioncam.state, null, 2));
```

## Return Structured Data To A Remote Client

Remote `script.run` returns a string. Use `JSON.stringify()` when the client should parse a structured result.

```js
JSON.stringify({
  mode: motioncam.state.app.captureMode,
  camera: motioncam.state.camera,
});
```

## Set Manual Exposure

```js
motioncam.camera.setIso(800);
motioncam.camera.setShutterNs(10_000_000);
motioncam.camera.setIsoLock(true);
motioncam.camera.setShutterLock(true);

`ISO ${motioncam.camera.iso}, shutter ${motioncam.camera.shutterNs} ns`;
```

## Focus Pull

```js
const marks = [3.0, 2.5, 2.0, 1.5, 1.0];

for (const mark of marks) {
  motioncam.camera.setFocusDistance(mark);
  console.log('Focus distance', mark);
}
```

The runtime currently does not provide timers, so this loop sends commands back-to-back. For timed focus pulls, issue separate `script.run` calls from the remote controller or add timing support in the app runtime.

## Reset Back To Auto Controls

```js
motioncam.camera.setIsoAuto();
motioncam.camera.setShutterAuto();
motioncam.camera.setFocusAuto();
motioncam.camera.setWhiteBalanceAuto();
motioncam.camera.resetManual();
```

## White Balance Preset

```js
const daylight = { temperature: 5600, tint: 0 };

motioncam.camera.setWhiteBalance(daylight.temperature, daylight.tint);
motioncam.camera.setAutoWhiteBalanceLock(true);
```

## Safe Command Wrapper

```js
function runCommand(name, fn) {
  try {
    fn();
    console.log(name, 'ok');
  } catch (error) {
    console.error(name, error);
    throw error;
  }
}

runCommand('setIso', () => motioncam.camera.setIso(640));
runCommand('setShutterNs', () => motioncam.camera.setShutterNs(8_333_333));
```

## Remote `script.run` Request

After pairing and authenticating the WebSocket connection, send JavaScript source with `script.run`:

```json
{
  "id": "script-1",
  "method": "script.run",
  "params": {
    "source": "JSON.stringify({ iso: motioncam.camera.iso, shutterNs: motioncam.camera.shutterNs })"
  }
}
```

Example response:

```json
{
  "id": "script-1",
  "result": {
    "value": "{\"iso\":400,\"shutterNs\":10000000}"
  }
}
```

The client can parse `result.value` as JSON because the script returned a JSON string.

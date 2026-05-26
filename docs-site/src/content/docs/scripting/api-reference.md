---
title: API Reference
description: Reference for the MotionCam JavaScript automation API.
---

The public API is available as `globalThis.motioncam`. The global `console` object is also installed and is the same object as `motioncam.console`.

For editor integration, the TypeScript declaration lives at `docs-site/api/motioncam.d.ts`.

## `motioncam`

### `motioncam.state`

Type: `MotionCamState`

Returns the latest runtime state snapshot. Reading this property asks the Android host for fresh state.

```js
const state = motioncam.state;
```

### `motioncam.getState()`

Returns the same state shape as `motioncam.state`.

```js
const cameraRunning = motioncam.getState().camera.running;
```

### `motioncam.capture.setMode(mode)`

Selects the app capture mode through the same command path used by the UI.

```js
motioncam.capture.setMode(motioncam.capture.modes.LOG_VIDEO);
```

Supported user-facing mode constants are `motioncam.capture.modes.ZSL`, `BURST`, `RAW_VIDEO`, `LOG_VIDEO`, and `TIMELAPSE`.

### `motioncam.capture.start()`

Requests capture in the current capture mode. In photo modes this requests one capture. In video modes this requests recording start. The call returns when the app accepts the request; use `motioncam.state` to observe the resulting capture or recording state.

```js
motioncam.capture.start();
```

### `motioncam.capture.stop()`

Requests the current video/timelapse recording to stop. The call returns when the app accepts the request; use `motioncam.state` to observe finalization and the stopped state.

```js
motioncam.capture.stop();
```

### `motioncam.cameras`

Type: `MotionCamCameraInfo[]`

Returns discovered cameras and static control ranges. Reading this property asks the Android host for fresh camera info.

```js
const activeCamera = motioncam.cameras.find((camera) => camera.active);
```

### `motioncam.getCameras()`

Returns the same camera info shape as `motioncam.cameras`.

```js
const isoRange = motioncam.getCameras()[0]?.isoRange;
```

### `motioncam.lenses`

Type: `MotionCamLensInfo[]`

Returns the same app-facing lens catalog used by MotionCam's lens picker.

```js
const activeLens = motioncam.lenses.find((lens) => lens.active);
```

### `motioncam.getLenses()`

Returns the same lens catalog shape as `motioncam.lenses`.

```js
const profiles = motioncam.getLenses()[0]?.profiles ?? [];
```

### `motioncam.profiles`

Type: `MotionCamProfileInfo[]`

Returns a flattened view of user-facing lens profiles. Profiles are the selectable app concept; each profile points to a `lensId` and underlying `cameraId`.

```js
const activeProfile = motioncam.profiles.find((profile) => profile.active);
```

### `motioncam.getProfiles()`

Returns the same profile info shape as `motioncam.profiles`.

```js
const defaultProfiles = motioncam.getProfiles().filter((profile) => profile.default);
```

### `motioncam.selectProfile(profileId)`

Selects an available lens profile using the same command path as the in-app lens picker.

### `motioncam.camera`

Camera state getters and camera control methods.

### `motioncam.console`

Console sink for script log output. This is the same object exposed as global `console`.

## State Types

### `MotionCamState`

```ts
interface MotionCamState {
  schemaVersion: number;
  version: number;
  timestampMs: number;
  app: {
    captureMode: 'ZSL' | 'BURST' | 'RAW_VIDEO' | 'LOG_VIDEO' | 'TIMELAPSE';
  };
  camera: MotionCamCameraState;
  recording: {
    active: boolean;
    state: string;
    finalizing: boolean;
  };
}
```

### `MotionCamCameraState`

```ts
interface MotionCamCameraState {
  id: string | null;
  lensId: string | null;
  profileId: string | null;
  controllerState: string;
  ready: boolean;
  running: boolean;
  previewRunning: boolean;
  controls: {
    focus: {
      manual: boolean;
      distanceDiopters: number;
    };
    exposure: {
      isoManual: boolean;
      iso: number;
      shutterManual: boolean;
      shutterNs: number;
    };
    whiteBalance: {
      manual: boolean;
      temperature: number;
      tint: number;
    };
  };
}
```

State field values are app/runtime strings. Consumers should treat them as descriptive status values rather than a stable enum unless a later protocol version explicitly defines the enum set.

### `MotionCamCameraInfo`

```ts
interface MotionCamCameraInfo {
  id: string;
  physicalCameraId: string | null;
  active: boolean;
  logical: boolean;
  frontFacing: boolean;
  requiresJavaBackend: boolean;
  sensorOrientation: number | null;
  isoRange: number[] | null;
  exposureTimeRangeNs: number[] | null;
  focalLengths: number[] | null;
  apertures: number[] | null;
  minFocusDistance: number | null;
  maxFocusDistance: number | null;
  hyperFocalDistance: number | null;
  oisSupported: boolean | null;
  zoomRange: number[] | null;
  exposureCompensationRange: number[] | null;
  exposureCompensationStep: number[] | null;
  fpsRange: number[] | null;
}
```

Camera info is discovery metadata. Fields may be `null` when the app cannot resolve that metadata for a camera.

### `MotionCamProfileInfo`

```ts
interface MotionCamProfileInfo {
  id: string;
  lensId: string;
  cameraId: string;
  name: string;
  active: boolean;
  default: boolean;
  autoGenerated: boolean;
}
```

Profile info is the app-facing selection model. Use `cameraId` to join a profile to `motioncam.cameras[].id` when a client needs low-level capability ranges.

### `MotionCamLensInfo`

```ts
interface MotionCamLensInfo {
  id: string;
  label: string;
  buttonPrimaryLabel: string;
  buttonSecondaryLabel: string | null;
  frontFacing: boolean;
  fallbackCameraId: string;
  focalLengthMm: number;
  active: boolean;
  defaultProfileId: string | null;
  activeProfileId: string | null;
  profiles: MotionCamProfileInfo[];
}
```

## Camera Properties

Camera properties are read-only JavaScript getters. Each read asks the host for current state.

### `motioncam.camera.iso`

Type: `number`

Current ISO.

### `motioncam.camera.shutterNs`

Type: `number`

Current shutter duration in nanoseconds.

### `motioncam.camera.focusDistance`

Type: `number`

Current focus distance in diopters.

### `motioncam.camera.manualFocusEnabled`

Type: `boolean`

Whether manual focus is currently enabled.

### `motioncam.camera.whiteBalance`

Type: `{ temperature: number; tint: number }`

Current white-balance temperature and tint.

## Camera Methods

Camera methods return `undefined` on success. They throw if the command is invalid for the current camera state, if the app rejects the command, or if the host command times out.

### `motioncam.camera.setIso(iso)`

Sets manual ISO.

```js
motioncam.camera.setIso(800);
```

### `motioncam.camera.setShutterNs(shutterNs)`

Sets manual shutter duration in nanoseconds.

```js
motioncam.camera.setShutterNs(10_000_000);
```

### `motioncam.camera.setFocusDistance(diopters)`

Sets manual focus distance in diopters.

```js
motioncam.camera.setFocusDistance(2.0);
```

### `motioncam.camera.setWhiteBalance(temperature, tint)`

Sets manual white balance.

```js
motioncam.camera.setWhiteBalance(5600, 0);
```

### `motioncam.camera.setExposureLock(enabled)`

Enables or disables exposure lock.

```js
motioncam.camera.setExposureLock(true);
```

### `motioncam.camera.setIsoLock(enabled)`

Enables or disables ISO lock.

```js
motioncam.camera.setIsoLock(true);
```

### `motioncam.camera.setShutterLock(enabled)`

Enables or disables shutter lock.

```js
motioncam.camera.setShutterLock(true);
```

### `motioncam.camera.setFocusLock(enabled)`

Enables or disables focus lock.

```js
motioncam.camera.setFocusLock(true);
```

### `motioncam.camera.setAutoWhiteBalanceLock(enabled)`

Enables or disables auto white-balance lock.

```js
motioncam.camera.setAutoWhiteBalanceLock(true);
```

### `motioncam.camera.toggleAutoWhiteBalanceLock()`

Toggles auto white-balance lock.

```js
motioncam.camera.toggleAutoWhiteBalanceLock();
```

### `motioncam.camera.setIsoAuto()`

Returns ISO control to auto mode.

```js
motioncam.camera.setIsoAuto();
```

### `motioncam.camera.setShutterAuto()`

Returns shutter control to auto mode.

```js
motioncam.camera.setShutterAuto();
```

### `motioncam.camera.setFocusAuto()`

Returns focus control to auto mode.

```js
motioncam.camera.setFocusAuto();
```

### `motioncam.camera.setWhiteBalanceAuto()`

Returns white balance to auto mode.

```js
motioncam.camera.setWhiteBalanceAuto();
```

### `motioncam.camera.resetManual()`

Resets manual camera settings.

```js
motioncam.camera.resetManual();
```

## Console

Console methods write to the MotionCam automation console. Remote clients receive console writes as `console.entry` events after authentication.

### `console.log(...values)`

Writes a log entry with level `LOG`.

### `console.warn(...values)`

Writes a log entry with level `WARN`.

### `console.error(...values)`

Writes a log entry with level `ERROR`.

### `console.clear()`

Clears the automation console.

```js
console.log('ISO', motioncam.camera.iso);
console.warn('Focus distance', motioncam.camera.focusDistance);
```

Console arguments are converted to strings by the embedded runtime and joined with spaces.

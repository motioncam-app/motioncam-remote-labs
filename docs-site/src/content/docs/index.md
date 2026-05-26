---
title: MotionCam Developer Docs
description: Developer documentation for MotionCam JavaScript scripting and remote automation.
---

MotionCam includes an embedded JavaScript automation engine for inspecting runtime camera state and driving manual camera controls. Scripts can run locally from the in-app script console or remotely through the authenticated remote-control WebSocket protocol.

These docs cover the public `motioncam` JavaScript API, the console API, and the remote protocol used by external controllers.

## What You Can Automate

- Read camera state from `motioncam.state` or `motioncam.getState()`.
- Discover lenses and profiles from `motioncam.lenses`, `motioncam.getLenses()`, `motioncam.profiles`, or `motioncam.getProfiles()`.
- Discover low-level cameras and control ranges from `motioncam.cameras` or `motioncam.getCameras()`.
- Control manual exposure, focus, and white balance through `motioncam.camera`.
- Emit logs with `console.log()`, `console.warn()`, and `console.error()`.
- Run the same JavaScript remotely with `script.run` after pairing with the camera device.
- Stream low-resolution preview frames over the authenticated remote connection.

## Local And Remote Use

Local scripts run from MotionCam's debug script console. Remote scripts run through the remote-control server after the user enables Remote Control in the app and shares the on-screen pairing details.

Both entry points use the same automation session model: one active automation controller owns the app at a time. While a remote client is connected, local console execution is disabled. When the local console owns the session, remote clients cannot take control until that session closes.

## Runtime Model

Scripts run in MotionCam's embedded QuickJS runtime. Each automation session owns one script engine instance, and script execution is serialized on a dedicated engine thread.

Script results are returned as strings by the current bridge. `undefined` and `null` return as an empty string. Return `JSON.stringify(...)` when a remote client needs structured data.

## Start Reading

- [JavaScript Engine](scripting/overview/) explains execution, globals, and runtime limits.
- [API Reference](scripting/api-reference/) lists the current `motioncam` JavaScript surface.
- [Examples](scripting/examples/) shows practical local and remote snippets.
- [Remote Protocol](remote-control/protocol/) documents pairing, JSON messages, preview streaming, and remote script execution.

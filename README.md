# MotionCam Remote Labs

MotionCam Remote Labs is a small Android demo app that shows how to connect to
MotionCam's remote-control API, scan a pairing QR code, open a WebSocket session,
receive preview frames, and send basic camera control commands.

This repository is intended as sample code for experimenting with the MotionCam
JavaScript remote API. It is not an official production remote-control app, and
it should be treated as a reference implementation rather than a polished end-user
product.

## What It Demonstrates

- QR-code pairing with MotionCam
- WebSocket authentication
- Remote camera state requests
- JPEG preview frame handling
- Basic ISO, shutter, and white-balance controls

## Development

Build the debug app with:

```sh
./gradlew :app:compileDebugKotlin
```

Install on a connected Android device with:

```sh
./gradlew :app:installDebug
```

Start the docs site locally with:

```sh
task docs:dev
```

Build and preview the generated docs site locally with:

```sh
task docs:preview
```

Build and publish the docs site to the `gh-pages` branch with:

```sh
task docs:publish
```

## Status

This project is a demo. APIs, UI behavior, and protocol handling may change as
the MotionCam remote-control API evolves.

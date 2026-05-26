# MotionCam Developer Docs

This is the hostable documentation site for MotionCam scripting and remote automation.

## Commands

Using Task from the repository root:

```bash
task docs:install
task docs:dev
task docs:build
task docs:preview
task docs:clean
task remote:forward
task docs:remote-lab
```

Or using npm directly:

```bash
npm install
npm run dev
npm run build
npm run preview
```

The production build is emitted to `dist/` and can be hosted as static files.

## Remote Lab

The remote lab is a static browser page for driving the app through the debug WebSocket remote-control server.

```bash
task remote:forward
task docs:remote-lab
```

Open `http://127.0.0.1:4321/remote-lab/`, enter the pairing code shown in the app console, and connect to `ws://127.0.0.1:8765`. The in-app console can be open while pairing, but do not run a local console script at the same time as a remote session.

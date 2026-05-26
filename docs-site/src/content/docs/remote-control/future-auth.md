---
title: Production Auth Direction
description: Notes for evolving MotionCam remote control into stronger production local control.
---

MotionCam's current remote-control workflow is explicit and local-first:

- The user enables Remote Control in the app.
- The app advertises a local WebSocket endpoint.
- The default transport is `wss://` with an app-generated self-signed certificate.
- The app displays a short-lived pairing code and QR payload.
- One authenticated remote automation session owns the app at a time.

This is suitable for trusted local workflows and lab use, but broader production controller ecosystems should use a stronger durable trust model than a short pairing code alone.

## Target Model

Use a local-first security model where TLS protects the connection, and a separate device identity key proves that the controller is talking to the same MotionCam device it paired with.

This model is mainly for native controllers or third-party apps that can implement certificate pinning and device identity verification. It is not intended for arbitrary browser clients, because browsers cannot silently trust self-signed local TLS certificates from JavaScript.

## Identities

### Device Identity Keypair

- Long-lived.
- Generated once by the app.
- Private key never leaves the device.
- Public key is shared during pairing.
- Used as the durable trust anchor for the MotionCam device.

### TLS Keypair And Certificate

- Used for local `wss://` encryption.
- May be self-signed.
- Fingerprint is verified during pairing.
- Can rotate later if the new certificate is authenticated by the device identity key.

Do not treat the self-signed TLS certificate as the permanent device identity. Treat it as transport state.

## First Pairing

1. User enables local remote control.
2. App generates or loads the long-term device identity keypair.
3. App generates or loads a local TLS keypair and self-signed certificate.
4. App starts a local WSS server.
5. App displays a QR code or pairing code.
6. Controller scans the QR code or accepts manual pairing details.
7. Controller connects to the local WSS endpoint.
8. Controller verifies the TLS certificate fingerprint from the QR payload when possible.
9. Controller verifies the pairing token.
10. Controller stores the paired device record.

## Paired Device Record

Controllers should store:

- `device_id`
- `device_identity_public_key`
- `current_tls_cert_fingerprint`
- optional display name
- optional last known endpoint
- paired and last-seen timestamps

The durable trust anchor is `device_identity_public_key`. The TLS fingerprint is the current pinned transport certificate.

## Reconnect

1. Controller connects to `wss://ip:port`.
2. Controller verifies that the presented TLS certificate fingerprint matches `current_tls_cert_fingerprint`.
3. Server signs a fresh challenge with the device identity private key.
4. Controller verifies the signature with `device_identity_public_key`.
5. If both checks pass, the connection is trusted.

## TLS Certificate Rotation

If the TLS certificate changes, the controller must not blindly trust it.

Acceptable rotation paths:

- Strict mode: require re-pairing.
- Authenticated rotation: while connected through the old trusted certificate, receive the new certificate fingerprint signed by the device identity key.
- Recovery mode: if the old certificate is lost, require physical/user-present re-pairing.

## Pairing Payload Direction

A future high-entropy QR payload could look like this:

```json
{
  "version": 1,
  "deviceId": "mc_7QK9...",
  "name": "MotionCam on SM-S948B",
  "endpoint": "wss://192.168.43.1:8765",
  "tlsFingerprintSha256": "base64url...",
  "deviceIdentityPublicKey": "base64url...",
  "pairingToken": "base64url-random-token",
  "expiresAtMs": 1780000000000
}
```

For QR pairing, prefer a high-entropy random token. Six-digit codes are acceptable for manual entry, but should be short-lived and rate-limited.

## Browser Limitation

Direct browser-based LAN control cannot silently trust a self-signed `wss://` certificate. Browser JavaScript cannot inspect and pin a certificate before the WebSocket TLS handshake succeeds.

Keep browser tooling limited to trusted lab workflows unless a future design uses trusted public certificates, a cloud relay, or another browser-compatible trust path.

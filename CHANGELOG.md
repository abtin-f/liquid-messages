# Changelog

## 1.6.1 — 2026-09-25
First public release.

### Messaging
- Default SMS app with delivery reports, dual-SIM and tap-to-retry for failed messages
- MMS: photos, camera, files and group conversations (own OMA-MMS PDU codec)
- Send Later (scheduled messages, re-armed after reboot)
- Location sharing rendered as a map card (OpenStreetMap)
- Tappable links, e-mail addresses and phone numbers
- Tapbacks, send-with-effect animations, stickers
- Block numbers, hide alerts, copy/delete messages

### Design
- iOS 26-style interface with a real Liquid Glass engine (blur + AGSL lens shader)
- iOS search animation, New Message page sheet, push transitions, grouped Settings
- Blue/green bubbles, light/dark mode, Persian/RTL support

### Reliability
- Sideload-friendly setup: only the SMS role is required; guided "Allow restricted settings" flow
- Sent/delivered results handled by a manifest receiver (survive process death)
- Location works with "Approximate" permission and times out cleanly

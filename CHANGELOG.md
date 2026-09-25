# Changelog

## 1.7.0 — 2026-09-25
- **Reply to a message**: swipe right on any bubble or choose *Reply*; the reply shows a tappable quote that jumps to the original
- **Replies reach the other phone**: a short “↪ «quote»” first line travels with the SMS; Liquid Messages on the other side links it to the original message, other apps show a readable quote
- **Effects reach the other phone**: sent as a readable “(Sent with … effect)” line, like iPhones do over SMS — Liquid Messages hides it and plays the animation (iPhone effect names such as Slam or Loud are recognised too)
- Persian and other right-to-left text now types right-aligned with correct word order in the composer, search and “To:” fields

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

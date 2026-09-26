# Changelog

## 1.8.1

- **Inbox:** the large title and search field sit at the top again. When you scroll down, search docks at the bottom next to compose (iOS 26), and it returns to the top when you scroll back up.
- **New group:** add several people in New Message (contacts, the ⊕ picker, or typed numbers as tokens) to start a group conversation over MMS. Tap a token or press backspace to remove it.
- **Inbox:** at the top, the compose (pencil) button sits beside Filter and moves to the bottom with search when you scroll. The bottom fade is gone, and the Edit menu has **New Group**.
- **Contact Info:** "First Message" and search results open the chat scrolled to that message and highlight it. (Fixed a bug where clearing the jump request cancelled the scroll.)
- **MMS diagnostics:** every send/download step is logged to Settings › MMS Diagnostics (sizes, result codes and network state only), and a failed MMS now shows an alert instead of failing silently.

## 1.8.0

- **Recently Deleted (iOS):** deleted conversations and messages stay for 30 days. Open it from the filter menu to see days left, then Recover or Delete each item, or use Recover All / Delete All. Everything is purged automatically after 30 days, and recovery restores messages exactly, MMS photos and reactions included.
- **iOS 26 inbox:** search moved to a floating glass capsule (with dictation) at the bottom beside the compose button. Edit and Filter float at the top, and the filter menu includes "N New" and Manage Filtering.
- **iOS 26 Contact Info:** the conversation's background shows behind a centred avatar, round glass actions, and glass tabs (Info · Backgrounds · Photos · Links · Documents · Locations). You can pick your own photo as a chat background, and there are new Water and Aurora backgrounds.
- **Faster inbox:** built from the provider's threads table. Contacts are read with one bulk query instead of one per conversation, and provider-change bursts are debounced into one reload.
- **Less lag:**
  - Compose strong skipping is on and data models are marked stable.
  - The dialog layer no longer re-records the screen every frame.
- **Fix – unread messages:** messages that arrive in an open chat are marked read immediately, and that chat doesn't raise a notification.
- **Inbox:** real iOS navigation bar. Scrolling folds the search field, then the large title, into the glass toolbar; pulling down rubber-bands and restores them.
- **Pinned conversations:** a grid of large avatars (up to 9) at the top of the inbox.
- **Swipe actions and long-press menu:** swipe for Read/Unread, Pin, Hide Alerts and Delete; long-press opens a menu with a conversation preview.
- **Filter menu:** filters for All Messages, Known Senders, Unknown Senders and Unread Messages, next to Edit.
- **Alerts:** every alert, confirmation and toast is now an iOS 26 Liquid Glass alert, action sheet or HUD. Deleting a conversation or message always asks first.
- **Send Later:** iOS picker wheels for the day and time.
- **Settings (iOS 27 Settings › Apps › Messages layout):**
  - New Personalization page with a live preview: appearance (Automatic/Light/Dark), 7 bubble colours, a text-size slider, conversation backgrounds and Liquid Glass (Clear/Tinted).
  - Show Previews, Auto-Play Message Effects, Swipe to Reply, Haptics, Low Quality Image Mode, Filter Unknown Senders and Keep Messages (30 days / 1 year).
- **Conversation details:**
  - Shared Photos, Links, Documents and Locations pages, plus Search in Conversation and a background for each conversation.
  - Group members list, Edit, and Report Junk.
- **Fix – tapbacks:** each person owns their own reaction. Yours is blue and theirs is grey, and you can no longer change or remove theirs.
- **Fix – startup:** the inbox appears instantly (cached), with a much lighter provider query.

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

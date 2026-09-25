# Liquid Messages — Build & Install

> The root `README.md` is now the **Design System** readme (from the design bundle). This file preserves the Android app's build/run instructions.

## Prebuilt APK
Signed release build: **`LiquidMessages-v1.6.0.apk`** (project root).

**Recommended — install over USB:** run **`install.bat`** (enable USB debugging first; on Samsung turn Auto Blocker off). It installs the app, makes it the default SMS app and grants its permissions via adb. That sidesteps Play Protect's sideload warnings and Android 13/15+ "restricted settings", which otherwise stop a sideloaded app from becoming the SMS app.

**Installing the APK on the phone directly:** if "Set as Default" does nothing, the app shows a step-by-step guide: App info → ⋮ → *Allow restricted settings* → Try Again.

## Screenshot tests (no device needed)
```bash
gradlew :app:testDebugUnitTest --tests "*ScreenshotTest*"
# PNGs: app/build/outputs/roborazzi/
```

## Network note (dependency downloads)
`dl.google.com` is tampered on this network. `settings.gradle.kts` lists the Tencent and Huawei Google-Maven mirrors first, so builds resolve without the local proxy.

## Build from source
Toolchain: **JDK 17 + Android SDK 34 + Gradle 8.7**. Font: Inter (SIL OFL, `licenses/Inter-OFL.txt`) stands in for SF Pro, which Apple doesn't license for Android (the wrapper is included).

```bash
./gradlew assembleDebug        # or gradlew.bat on Windows
# output: app/build/outputs/apk/debug/app-debug.apk
```

Set `local.properties` to your SDK (`sdk.dir=...`); Android Studio writes this automatically. Open the folder in Android Studio and **Run ▶** to iterate.

> **Network note (this machine):** direct `dl.google.com` access is blocked here, so the build JVM is routed through the local proxy (`127.0.0.1:10808`) with TLS pinned to 1.2 — see `~/.gradle` / `GRADLE_OPTS`. If Gradle can't resolve `com.android.application`, configure that proxy (or Android Studio's proxy settings).

## Set as the default SMS app
1. Launch the app → **Set as Default** (system `RoleManager.ROLE_SMS` dialog) → confirm.
2. **Continue** → grant SMS / Contacts / Notifications permissions.
3. Or: **Settings ▸ Apps ▸ Default apps ▸ SMS app**, or `adb shell cmd role add-role-holder android.app.role.SMS com.liquidglass.messages`.

## Design System
The `tokens/`, `components/`, `ui_kits/`, `cards/`, `styles.css`, and `SKILL.md` at the root are the **Liquid Messages Design System** (web-native tokens + UI kit). The Android theme (`app/.../ui/theme/`) is kept in token-parity with `tokens/colors.css`, `typography.css`, and `spacing.css`.

@echo off
setlocal
rem ------------------------------------------------------------------
rem  Liquid Messages - one-click USB install (Windows)
rem
rem  Installing over USB with adb avoids BOTH problems of installing an
rem  APK file on the phone itself:
rem    * Play Protect / "unsafe app" install warnings
rem    * Android 13/15+ "restricted settings", which stops a sideloaded
rem      app from becoming the default SMS app
rem  It also makes the app the default SMS app and grants its
rem  permissions, so it opens straight into your conversations.
rem
rem  On the phone, once:
rem    1. Settings > About phone > Software information > tap
rem       "Build number" 7 times (enables Developer options)
rem    2. Settings > Developer options > USB debugging: ON
rem    3. Samsung: Settings > Security and privacy > Auto Blocker: OFF
rem       (it blocks USB commands)
rem    4. Plug in the cable and tap "Allow" on the USB debugging prompt
rem ------------------------------------------------------------------

set "HERE=%~dp0"
set "ADB=%HERE%..\.android-toolchain\sdk\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=adb"
set "APK=%HERE%LiquidMessages-v1.6.0.apk"
set "PKG=com.liquidglass.messages"

if not exist "%APK%" (
  echo [x] APK not found: %APK%
  goto :end
)

echo [1/4] Waiting for the phone (unlock it and allow USB debugging)...
"%ADB%" wait-for-device

echo [2/4] Installing %APK% ...
"%ADB%" install -r "%APK%"
if errorlevel 1 (
  echo.
  echo [x] Install failed. If it says INSTALL_FAILED_UPDATE_INCOMPATIBLE,
  echo     an older copy signed with a different key is installed. Uninstall
  echo     "Liquid Messages" from the phone first, then run this again.
  goto :end
)

echo [3/4] Making Liquid Messages the default SMS app...
"%ADB%" shell cmd role add-role-holder android.app.role.SMS %PKG% 0
"%ADB%" shell appops set %PKG% ACCESS_RESTRICTED_SETTINGS allow >nul 2>&1

echo [4/4] Granting contacts and notification permissions...
"%ADB%" shell pm grant %PKG% android.permission.READ_CONTACTS >nul 2>&1
"%ADB%" shell pm grant %PKG% android.permission.POST_NOTIFICATIONS >nul 2>&1

"%ADB%" shell monkey -p %PKG% -c android.intent.category.LAUNCHER 1 >nul 2>&1
echo.
echo Done - Liquid Messages is open on your phone.

:end
echo.
pause

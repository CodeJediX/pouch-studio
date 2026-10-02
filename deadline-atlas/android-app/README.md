# Deadline Atlas Android

Native Android WebView shell for the hosted Deadline Atlas application.

## Requirements

- Android Studio JBR 21 or another supported JDK
- Android SDK 35

## Build

```powershell
.\gradlew.bat assembleRelease
```

The installable APK is generated at `app/build/outputs/apk/release/app-release.apk`.

The app preserves Supabase sessions, supports the site's document picker, opens external links outside the app, and routes JSON/ICS exports through Android's system save dialog.

Deadline notifications are scheduled natively so they can appear while the app is closed. The default reminders are 2 days, 1 day, 12 hours, and 2 hours before each active deadline; users can add or remove reminder times in the web interface. Android 13 and newer asks for notification permission, and the test button at the bottom of the app confirms that alerts are enabled.

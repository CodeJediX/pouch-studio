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

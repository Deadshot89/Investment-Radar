# Android App

- Kotlin / Jetpack Compose
- minSdk 23
- targetSdk 36
- compileSdk 36
- AGP 8.13.2
- Gradle 8.13
- Kotlin 2.3.21
- Compose BOM 2026.06.00 (Compose 1.11.4)
- FCM Topic: `investment-alerts`
- Live-Refresh in geoeffneter App: alle 60 Sekunden

Wenn Firebase-Werte fehlen, startet die App trotzdem; Push wird dann als `SETUP` angezeigt.

- Ab 1.1.9: signierte Release-APK mit dauerhaftem Keystore aus GitHub Secrets
- versionCode 10 / versionName 1.1.9

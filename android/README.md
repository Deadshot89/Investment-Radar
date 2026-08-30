# Android App

- Kotlin / Jetpack Compose
- minSdk 23
- targetSdk 36
- compileSdk 37
- FCM Topic: `investment-alerts`
- Live-Refresh in geoeffneter App: alle 60 Sekunden

Wenn Firebase-Werte fehlen, startet die App trotzdem; Push wird dann als `SETUP` angezeigt.

Der Repo-Build verwendet GitHub Actions mit Gradle 9.5.0 direkt. Ein lokaler Gradle-Wrapper ist bewusst nicht beigelegt; Android Studio Quail 3 kann das Projekt mit Gradle 9.5.0 synchronisieren.

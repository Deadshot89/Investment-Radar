# Hotfix 1.1.3

Android-Build auf eine konservative, API-36-kompatible Toolchain umgestellt:

- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- Kotlin Android + Compose Compiler: 2.3.21
- compileSdk / targetSdk: 36
- Compose BOM: 2026.06.01 (Compose 1.11.4)
- Material3: 1.4.0
- Java/Kotlin JVM target: 17

Warum: Version 1.1.3 verwendete AGP 9.3 mit Built-in Kotlin. Diese Kombination hat bereits mehrere Plugin-/DSL-Kompatibilitätsfehler verursacht. 1.1.3 verwendet wieder den klassischen Kotlin-Android-Pluginpfad auf einer offiziell zu API 36 passenden AGP-Version.

Der GitHub-Workflow baut jetzt mit Gradle 8.13 und gibt bei einem Fehler einen vollständigen Stacktrace aus.

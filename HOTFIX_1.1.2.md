# Hotfix 1.1.3

Behoben:

1. AGP 9.x + Kotlin Konflikt
   - `org.jetbrains.kotlin.android` entfernt.
   - AGP 9.x Built-in Kotlin wird verwendet.
   - `android.builtInKotlin=false` entfernt.
   - alte `android.kotlinOptions` Konfiguration entfernt; Java/Kotlin nutzen targetCompatibility 17.

2. API-36-Kompatibilitaet stabilisiert
   - Compose BOM von `2026.08.00` auf `2026.06.01` gepinnt.
   - Lifecycle von `2.11.0` auf `2.10.0` gepinnt.
   - `compileSdk = 36` / `targetSdk = 36` bleiben bestehen.
   - Grund: Compose 1.12.x und neuere Lifecycle-Compose-Artefakte verlangen API 37; der GitHub Runner konnte Android 37 zuvor nicht installieren.

3. App-Version
   - versionCode 4
   - versionName 1.1.3

GitHub:
- ZIP-Inhalt ueber das bestehende Repository kopieren/ersetzen.
- Danach `Build Android APK` erneut starten.

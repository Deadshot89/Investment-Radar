# Investment Radar

Investment Radar ist eine Android-App für die persönliche Beobachtung, Analyse und Verwaltung von Aktien und ETFs. Die App verbindet Live-Marktdaten mit einem regelbasierten Advisor, einem monatlichen Kaufbudget, Depot-/Watchlist-Funktionen und manueller Transaktionserfassung.

**Aktueller Android-Stand:** 2.5.24 / versionCode 94  
**Backend:** 2.1.0  
**API-Schema:** 2026-09-14.1

## Kernfunktionen

- Android-App mit Kotlin und Jetpack Compose
- Live-Dashboard für Aktien und ETFs
- Radar-Universum mit Zielgröße von mindestens 2.000 Trade-Republic-verifizierten Instrumenten
- Analyse V2 mit Qualität, Bewertung, Wachstum, Momentum, Risiko und Datenqualität
- klare persönliche Aktionen wie NACHKAUFEN, HALTEN, REDUZIEREN und VERKAUFEN
- monatliches Kaufbudget mit strikt getrenntem App-Cash
- bestätigte Käufe reduzieren das verfügbare Monatsbudget
- Verkaufserlöse werden als privat verwendet dokumentiert und erhöhen weder App-Cash noch Kaufbudget
- Funktion „ICH BRAUCHE GELD“ mit getrenntem App-Cash- und Verkaufsanteil
- direkte Bearbeitung einer Empfehlung mit vorbefülltem Betrag und – bei belastbarem Kurs – Stückzahl
- Depot, Watchlist, Sparplan-Kontext und Exit-Strategien
- Trade-Republic-Weiterleitung
- Push-Benachrichtigungen über Firebase Cloud Messaging
- In-App-Update über signierte Release-APK
- Azure-Functions-Backend
- optionale Google-Sheets-Anbindung
- GitHub-Actions-Gates für Contracts, JVM-Tests, signierte APK und instrumentierte UI-Tests

## Architektur

```text
Android App
   |
   v
Azure Functions API
   |-- Marktdaten / Historie / Fundamentaldaten
   |-- Analyse V2 / Radar
   |-- Trade-Republic-Instrumentuniversum
   |-- Firebase Cloud Messaging
   |-- Azure Blob State
   '-- optionale Google-Sheets-Anbindung
```

Geheime API-Schlüssel und Firebase-Service-Zugangsdaten liegen ausschließlich im Backend bzw. in GitHub/Azure-Secrets. Sie werden nicht in der Android-App eingecheckt.

## Daten- und Budgetregeln

Investment Radar unterscheidet bewusst zwischen Kaufbudget und sonstigem Geld:

- Das monatliche Kaufbudget ist der einzige Rahmen für neue Kaufempfehlungen.
- Bestätigte Käufe reduzieren diesen Rahmen.
- Übertrag und Zusatz-Cash erhöhen das Kaufbudget nicht automatisch.
- Verkaufserlöse werden für private Verwendung erfasst und nicht als neues App-Cash behandelt.
- Historische Depotwerte oder gespeicherte Snapshots dürfen nicht als aktuelle Marktwerte ausgegeben werden.
- Fehlende oder unzuverlässige Daten werden sichtbar als Datenqualitätsproblem behandelt.

Die App führt **keine Orders automatisch aus**.

## Empfehlungen bearbeiten

Im Dashboard werden Instrument, Signal und Betrag getrennt dargestellt. Über **Bearbeiten** kann die konkrete Ausführung direkt angepasst werden:

- Kauf- oder Verkaufsmodus wird aus der Aktion abgeleitet.
- empfohlener Betrag wird vorbefüllt
- Stückzahl wird bei vorhandenem belastbarem EUR-Kurs berechnet
- Betrag und Stückzahl bleiben vor der Bestätigung editierbar
- die zugrunde liegende automatische Analyse wird dadurch nicht still überschrieben

## Release- und Qualitätsgates

Vor einem Android-Release prüft die CI unter anderem:

1. Source-/UI-Contracts
2. Android JVM Unit Tests
3. signierten Release-Build
4. APK-Signatur
5. Live-Backend-Kompatibilität
6. Backend-Version, API-Schema und exakte Backend-Revision
7. Radar-Zielgröße von mindestens 2.000 Instrumenten
8. instrumentierte UI-Smoke-Tests auf Phone- und Tablet-Konfigurationen

Produktive APK-Veröffentlichungen sind auf `main` beschränkt.

## Schnellstart

1. `SETUP.md` abarbeiten.
2. Backend über den Workflow **Deploy Backend** veröffentlichen.
3. `${INVESTMENT_API_BASE_URL}/api/health` prüfen.
4. Android-Build über **Build Android APK** starten.
5. Signierte APK bzw. das In-App-Update verwenden.

## Dauerhafte Android-Signierung

Seit 1.1.9 werden produktive APKs mit einem dauerhaften Keystore signiert. Der private Keystore liegt nicht im Repository.

Benötigte GitHub Secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Zusätzlich benötigt der Android-Build Firebase-Konfiguration und eine gültige `INVESTMENT_API_BASE_URL`.

Der einmal verwendete Produktions-Keystore muss dauerhaft erhalten bleiben, sonst lassen sich spätere APKs nicht als Update installieren.

## Hinweis

Investment Radar ist ein Analyse- und Entscheidungswerkzeug. Es führt keine automatischen Trades aus und garantiert keine Rendite.

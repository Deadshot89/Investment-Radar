# Investment Radar 1.1.15 – In-App-Updates

## Neu
- Der Update-Button prüft GitHub Releases auf eine neuere Investment-Radar-Version.
- Der signierte Android-Build veröffentlicht die Release-APK automatisch als `investment-radar.apk` im GitHub Release der jeweiligen Version.
- Die App lädt eine neue APK selbst über Android DownloadManager herunter.
- Danach öffnet sie automatisch den Android-Paketinstaller.
- Ab Android 8 muss einmalig "Aus dieser Quelle zulassen" für Investment Radar aktiviert werden.
- Die normale Android-Bestätigung "Aktualisieren" bleibt aus Sicherheitsgründen erforderlich.
- Portfolio-Daten bleiben bei normalen Updates erhalten.

## Voraussetzung
Die Release-API und das Release-Asset müssen vom Handy erreichbar sein. Bei einem privaten GitHub-Repository ist dafür später eine serverseitige Update-Verteilung über Azure nötig; ein GitHub-Zugriffstoken wird bewusst nicht in die APK eingebaut.

## Version
- versionCode: 16
- versionName: 1.1.15

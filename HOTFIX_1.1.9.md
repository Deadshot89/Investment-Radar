# Hotfix 1.1.9 – dauerhafte Android-Signierung

## Ursache
Die bisherigen GitHub-Builds erzeugten `app-debug.apk`. Auf einem frischen GitHub-Runner wird dabei ein neuer temporärer Debug-Keystore erzeugt. Deshalb hatten aufeinanderfolgende APKs unterschiedliche Signaturen und Android verweigerte das Aktualisieren einer bereits installierten Version.

## Änderung
- Release-APK statt Debug-APK.
- Fester Keystore wird ausschließlich aus GitHub Secrets geladen.
- Der private Keystore wird nicht im Repository gespeichert.
- Build bricht ab, wenn eines der Signing-Secrets fehlt.
- `apksigner verify` prüft die fertige APK vor dem Upload.
- Version: 1.1.9 / versionCode 10.

## Einmaliger Hinweis
Die bereits installierte ältere App muss einmal deinstalliert werden, weil deren zufälliger Debug-Schlüssel nicht wiederherstellbar ist. Ab der ersten Installation von 1.1.9 können spätere Versionen über die bestehende App aktualisiert werden, solange derselbe Keystore verwendet wird.

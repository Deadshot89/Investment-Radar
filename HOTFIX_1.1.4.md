# Hotfix 1.1.4

- Compose BOM von nicht verifizierter `2026.06.01` auf die offiziell dokumentierte `2026.06.00` korrigiert.
- Material3-Version nicht mehr separat gepinnt; sie wird jetzt konsistent über die Compose BOM aufgelöst.
- Android-Version auf 1.1.4 / versionCode 6 erhöht.
- GitHub-Workflow schreibt bei Fehlern die relevanten Gradle-Zeilen in die Job Summary und lädt den vollständigen Build-Log als Artifact hoch.

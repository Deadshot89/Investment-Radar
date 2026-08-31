# Hotfix 1.1.5

## Ursache
Der Android-Build brach bei CheckAarMetadata ab, weil `androidx.core:core-ktx:1.19.0` Android Gradle Plugin 9.1 oder höher verlangt, das Projekt aber bewusst AGP 8.13.2 verwendet.

## Änderung
Nur die inkompatible Core-Abhängigkeit wurde geändert:

- vorher: `androidx.core:core-ktx:1.19.0`
- jetzt: `androidx.core:core-ktx:1.17.0`

AGP 8.13.2, Gradle 8.13, Kotlin 2.3.21 und compileSdk 36 bleiben unverändert.

## Warum 1.17.0
Core 1.17.0 ist auf API 36 ausgerichtet und verlangt Kotlin Gradle Plugin 2.0.0 oder höher. Das Projekt verwendet Kotlin 2.3.21.

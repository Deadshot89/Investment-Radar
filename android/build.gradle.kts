plugins {
    id("com.android.application") version "9.3.0" apply false
    // AGP 9.x kompiliert Kotlin bereits selbst. Kein org.jetbrains.kotlin.android mehr.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

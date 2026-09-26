plugins {
    id("com.android.application")
}

android {
    namespace = "de.tobias.launcher.recovery"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.tobias.launcher.recovery"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-recovery"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

plugins {
    id("com.android.application")
}

val releaseSigningVariables = listOf(
    "LAUNCHER_KEYSTORE_PATH",
    "LAUNCHER_KEYSTORE_PASSWORD",
    "LAUNCHER_KEY_ALIAS",
    "LAUNCHER_KEY_PASSWORD",
)

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

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("LAUNCHER_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("LAUNCHER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LAUNCHER_KEY_ALIAS")
                keyPassword = System.getenv("LAUNCHER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val verifyReleaseSigningInputs = tasks.register("verifyReleaseSigningInputs") {
    doLast {
        val missing = releaseSigningVariables.filter { System.getenv(it).isNullOrBlank() }
        check(missing.isEmpty()) {
            "Missing required release signing environment variables: ${missing.joinToString(", ")}"
        }

        val keystore = file(System.getenv("LAUNCHER_KEYSTORE_PATH"))
        check(keystore.isFile) {
            "Release keystore does not exist: ${keystore.absolutePath}"
        }
    }
}

afterEvaluate {
    tasks.named("preReleaseBuild").configure {
        dependsOn(verifyReleaseSigningInputs)
    }
}

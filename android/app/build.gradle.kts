import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun prop(name: String, fallback: String = ""): String =
    providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() } ?: fallback

android {
    namespace = "de.tobias.investmentradar"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.tobias.investmentradar"
        minSdk = 23
        targetSdk = 36
        versionCode = 41
        versionName = "1.3.0"

        buildConfigField("String", "API_BASE_URL", "\"${prop("INVESTMENT_API_BASE_URL", "https://YOUR-FUNCTION-APP.azurewebsites.net").trimEnd('/')}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${prop("FIREBASE_APP_ID")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${prop("FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${prop("FIREBASE_PROJECT_ID")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${prop("FIREBASE_SENDER_ID")}\"")
        buildConfigField("String", "GITHUB_REPOSITORY", "\"${prop("GITHUB_REPOSITORY")}\"")
    }

    signingConfigs {
        create("release") {
            val keystorePath = prop("ANDROID_KEYSTORE_PATH")
            require(keystorePath.isNotBlank()) { "ANDROID_KEYSTORE_PATH fehlt" }
            storeFile = file(keystorePath)
            storePassword = prop("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = prop("ANDROID_KEY_ALIAS")
            keyPassword = prop("ANDROID_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")

    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-messaging")
}

// Build trigger: Investment Radar 1.3.0

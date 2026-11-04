plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("com.google.gms.google-services")
}

val apexApiBaseUrl = providers.gradleProperty("APEX_API_BASE_URL").orElse("").get()
val apexKeystoreFile = providers.gradleProperty("APEX_KEYSTORE_FILE").orElse("").get()
val apexKeystorePassword = providers.gradleProperty("APEX_KEYSTORE_PASSWORD").orElse("").get()
val apexKeyAlias = providers.gradleProperty("APEX_KEY_ALIAS").orElse("").get()
val apexKeyPassword = providers.gradleProperty("APEX_KEY_PASSWORD").orElse("").get()

android {
    namespace = "com.apex.exchange"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("apex") {
            if (apexKeystoreFile.isNotBlank()) {
                storeFile = file(apexKeystoreFile)
                storePassword = apexKeystorePassword
                keyAlias = apexKeyAlias
                keyPassword = apexKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (apexKeystoreFile.isNotBlank()) signingConfig = signingConfigs.getByName("apex")
        }
    }

    buildFeatures { buildConfig = true }

    defaultConfig {
        buildConfigField("String", "APEX_API_BASE_URL", "\"$apexApiBaseUrl\"")
        applicationId = "com.apex.exchange"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
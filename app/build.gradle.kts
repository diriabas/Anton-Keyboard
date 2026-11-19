plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.diriabas.antonkeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.diriabas.antonkeyboard"
        minSdk = 23
        targetSdk = 35
        versionCode = 71
        versionName = "0.9.5"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

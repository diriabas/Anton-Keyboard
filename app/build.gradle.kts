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
        versionCode = 68
        versionName = "0.9.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

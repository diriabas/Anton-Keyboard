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
        versionCode = 73
        versionName = "0.9.7"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

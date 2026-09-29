plugins {
    id("com.android.application")
}

android {
    namespace = "com.roadprints.capture"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.roadprints.capture"
        minSdk = 26
        targetSdk = 35
        versionCode = 38
        versionName = "0.24.2"
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-location:21.3.0")
}

val stableKeystore = System.getenv("ROADPRINTS_DEBUG_KEYSTORE")
if (stableKeystore != null) {
    android.signingConfigs.create("stableDebug") {
        storeFile = file(stableKeystore)
        storePassword = "roadprints-debug-password"
        keyAlias = "roadprints-debug"
        keyPassword = "roadprints-debug-password"
    }
    android.buildTypes.getByName("debug") {
        signingConfig = android.signingConfigs.getByName("stableDebug")
    }
}

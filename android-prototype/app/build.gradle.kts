plugins {
    id("com.android.application")
}

android {
    namespace = "com.roadprints.capture"
    compileSdk = 35

    signingConfigs {
        create("stableDebug") {
            storeFile = file(System.getenv("ROADPRINTS_DEBUG_KEYSTORE")
                ?: "${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "roadprints-debug-password"
            keyAlias = "roadprints-debug"
            keyPassword = "roadprints-debug-password"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
    }

    defaultConfig {
        applicationId = "com.roadprints.capture"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1"
    }
}

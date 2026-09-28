plugins {
    id("com.android.application")
}

android {
    namespace = "com.roadprints.capture"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.roadprints.capture"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1"
    }
}

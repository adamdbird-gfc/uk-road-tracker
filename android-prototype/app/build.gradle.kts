plugins {
    id("com.android.application")
}

android {
    namespace = "com.roadprints.capture"
    compileSdk = 35
    sourceSets.getByName("main").assets.srcDir("../../canonical-a-roads-v5")
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "com.roadprints.capture"
        minSdk = 26
        targetSdk = 35
        versionCode = 144
        versionName = "0.25.98"
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
}

val stableKeystore = System.getenv("ROADPRINTS_DEBUG_KEYSTORE")
val stableStorePassword = System.getenv("ROADPRINTS_DEBUG_STORE_PASSWORD")
val stableKeyAlias = System.getenv("ROADPRINTS_DEBUG_KEY_ALIAS")
val stableKeyPassword = System.getenv("ROADPRINTS_DEBUG_KEY_PASSWORD")
if (stableKeystore != null && stableStorePassword != null && stableKeyAlias != null && stableKeyPassword != null) {
    android.signingConfigs.create("stableDebug") {
        storeFile = file(stableKeystore)
        storePassword = stableStorePassword
        keyAlias = stableKeyAlias
        keyPassword = stableKeyPassword
    }
    android.buildTypes.getByName("debug") { signingConfig = android.signingConfigs.getByName("stableDebug") }
}

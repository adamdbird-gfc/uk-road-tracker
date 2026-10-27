plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.roadprints.capture"
    compileSdk = 35
    androidResources { noCompress += "bin" }
    sourceSets.getByName("main").assets.srcDir("../../canonical-a-roads-v5")
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "com.roadprints.capture"
        minSdk = 26
        targetSdk = 35
        versionCode = 264
        versionName = "0.25.218"
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")
    implementation("androidx.core:core:1.15.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
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

// A failed external serving gate still permits all offline regression tests.
// Packaging never sets this property and always runs the complete suite.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Mock Transport fixtures still work. Any real public reference connection fails before opening a URL.
    systemProperty("roadprints.referenceNetworkDisabled", "true")
    // Gradle daemon system properties are not implicitly inherited by forked Test JVMs.
    providers.systemProperty("robolectric.offline").orNull?.let {
        systemProperty("robolectric.offline", it)
    }
    providers.systemProperty("robolectric.dependency.dir").orNull?.let {
        systemProperty("robolectric.dependency.dir", it)
    }
    if (providers.gradleProperty("ncnOfflineChecks").orNull == "true") {
        exclude("**/NcnNationalTest*", "**/NcnUiTest*")
    }
}


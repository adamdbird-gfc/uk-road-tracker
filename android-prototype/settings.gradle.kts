import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
        // Google's public Maven Central mirror avoids rate limiting on shared CI runners.
        // Keep Maven Central as fallback for artifacts not yet mirrored.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google's public Maven Central mirror avoids rate limiting on shared CI runners.
        // Keep Maven Central as fallback for artifacts not yet mirrored.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        mavenCentral()
    }
}

rootProject.name = "roadprints-capture-prototype"
include(":app")

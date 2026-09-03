import java.util.Properties

// Cloud agents and CI set ANDROID_SDK_ROOT; developers may use local.properties.
// Generate sdk.dir automatically so Gradle does not require manual local.properties setup.
val localPropertiesFile = file("local.properties")
if (!localPropertiesFile.exists()) {
    val sdkFromEnv = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
    val sdkFromProject = file(".android-sdk").takeIf { it.isDirectory }?.absolutePath
    val sdkDir = sdkFromEnv ?: sdkFromProject
    if (sdkDir != null) {
        Properties().apply {
            setProperty("sdk.dir", sdkDir.replace('\\', '/'))
            localPropertiesFile.outputStream().use { store(it, "Generated for Gradle (ANDROID_SDK_ROOT / .android-sdk)") }
        }
    }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Moment"
include(":app")

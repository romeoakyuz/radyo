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
        maven { url = java.net.URI("https://dl.google.com/dl/android/maven2/") }
        maven { url = java.net.URI("https://repo1.maven.org/maven2/") }
    }
}
rootProject.name = "Radyo"
include(":app")

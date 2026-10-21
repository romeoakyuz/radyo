buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://dl.google.com/dl/android/maven2/") }
        maven { url = java.net.URI("https://repo1.maven.org/maven2/") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
    }
}

plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
}

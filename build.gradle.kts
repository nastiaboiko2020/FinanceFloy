// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.google.gms:google-services:4.4.2") // Плагін Google Services для Firebase
        classpath("com.android.tools.build:gradle:8.2.0") // Плагін Android
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22") // Плагін Kotlin
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
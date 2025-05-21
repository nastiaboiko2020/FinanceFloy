plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.financeflow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.financeflow"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "GROK_API_KEY", "\"${findProperty("GROK_API_KEY") ?: ""}\"")
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "GROK_API_KEY", "\"${findProperty("GROK_API_KEY") ?: ""}\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11" // Уніфікуємо з Java 11
    }
}

dependencies {
    // Основні бібліотеки
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM для уніфікації версій
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3") // Material3 для компонентів
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended") // Розширені іконки

    // Додаткові бібліотеки
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0") // Оновлено до новішої версії
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0") // Оновлено до новішої версії
    implementation("androidx.navigation:navigation-compose:2.7.7") // Оновлено до новішої версії
    implementation ("com.google.accompanist:accompanist-pager:0.28.0")
    // Parcelize
    implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime:1.9.22")

    // HTTP-запити
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // Оновлено до новішої версії
    implementation("com.google.code.gson:gson:2.11.0") // Оновлено до новішої версії
    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")
    // Тестування
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
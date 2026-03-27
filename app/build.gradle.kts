import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Leer credenciales desde local.properties (no se sube a git)
val localProperties = Properties()
rootProject.file("local.properties").let { file ->
    if (file.exists()) file.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.example.syncengine"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.syncengine"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inyectar claves de Supabase en BuildConfig
        buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${localProperties.getProperty("SUPABASE_KEY", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Opt-in global para APIs internas de serialización usadas por Supabase Postgrest
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.InternalSerializationApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
        )
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.InternalSerializationApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
        )
    }
}

dependencies {
    // AndroidX & Compose (catálogo de versiones)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.supabase.gotrue.kt)
    implementation(libs.supabase.storage.kt)

    // Coil (cargar imágenes en Compose)
    implementation(libs.coil.compose)

    // Ktor
    implementation(libs.ktor.client.android)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // ViewModel Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
}
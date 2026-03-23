import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

// Función para obtener la versión desde git tags
// Usa --sort=-version:refname para obtener el tag de versión más alta
// que apunte al HEAD actual o sea ancestro de HEAD
fun getVersionFromGit(): String {
    return try {
        // Primero intentar obtener todos los tags que apuntan al HEAD actual, ordenados por versión desc
        val process = Runtime.getRuntime().exec(arrayOf("git", "tag", "--sort=-version:refname", "--points-at", "HEAD"))
        val tags = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        val bestTag = tags.lines().firstOrNull { it.startsWith("v") }?.removePrefix("v")
        if (!bestTag.isNullOrEmpty()) {
            bestTag
        } else {
            // Fallback: git describe clásico
            val p2 = Runtime.getRuntime().exec(arrayOf("git", "describe", "--tags", "--abbrev=0"))
            val version = p2.inputStream.bufferedReader().readText().trim().removePrefix("v")
            p2.waitFor()
            if (version.isEmpty()) "1.0.0" else version
        }
    } catch (e: Exception) {
        println("Warning: Could not read git tag, using default version 1.0.0")
        "1.0.0"
    }
}

// Función para generar versionCode desde versionName
// Formato: 1.4.1 -> 10401 (1*10000 + 4*100 + 1)
fun versionCodeFromName(versionName: String): Int {
    return try {
        val parts = versionName.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        (major * 10000) + (minor * 100) + patch
    } catch (e: Exception) {
        println("Warning: Could not parse version, using versionCode 1")
        1
    }
}

// Cargar credenciales del keystore desde archivo local (no versionado)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.mommys.app"
    compileSdk = 34

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.mommys.app"
        minSdk = 24
        targetSdk = 34
        
        // Versión automática desde git tags
        val autoVersion = getVersionFromGit()
        versionName = autoVersion
        versionCode = versionCodeFromName(autoVersion)
        
        println("Building version: $versionName (code: $versionCode)")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Solo incluir los idiomas que la app soporta
        // Esto elimina strings huérfanas de librerías en otros locales
        resourceConfigurations += listOf("en", "es", "de", "fr", "ja", "pt", "ru", "zh")
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    
    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    
    // Image Loading
    implementation(libs.glide)
    implementation(libs.glide.okhttp)
    kapt(libs.glide.compiler)
    
    // UI Components
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.photoview)
    implementation(libs.flexbox)
    
    // Video Player
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    
    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    
    // Navigation
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    
    // Biometric
    implementation(libs.androidx.biometric)
    
    // Preferences
    implementation(libs.androidx.preference)
    
    // AppLovin MAX SDK for ads
    implementation(libs.applovin.sdk)
}

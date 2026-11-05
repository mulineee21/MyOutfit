import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun resolveBuildProp(key: String, default: String = ""): String {
    return (localProps.getProperty(key)
        ?: project.findProperty(key)?.toString()
        ?: System.getenv(key)
        ?: default).trim()
}

android {
    namespace = "com.example.myoutfit"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.joelmolina.myoutfit"
        minSdk = 30
        targetSdk = 37
        versionCode = 4
        versionName = "2.0"

        buildConfigField("String", "APP_AUTHOR", "\"Joel Molina\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${resolveBuildProp("GEMINI_API_KEY")}\"")
        buildConfigField("String", "SOCIAL_API_BASE_URL", "\"${resolveBuildProp("SOCIAL_API_BASE_URL", "https://myoutfit.duckdns.org")}\"")
        buildConfigField("String", "SOCIAL_LAN_FALLBACK_IP", "\"${resolveBuildProp("SOCIAL_LAN_FALLBACK_IP", "192.168.1.159")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${resolveBuildProp("GOOGLE_WEB_CLIENT_ID", "828352249255-hcgnjqc81ulgl06il3uq1elaqdhlm9it.apps.googleusercontent.com")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("keystore/myoutfit-joelmolina.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "myoutfit_joelmolina"
                keyAlias = "joelmolina"
                keyPassword = "myoutfit_joelmolina"
            }
        }
    }

    buildTypes {
        debug {
            val keystoreFile = rootProject.file("keystore/myoutfit-joelmolina.jks")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.retrofit)
    implementation(libs.google.generativeai)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.restaurant.sushimei.frontend"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.restaurant.sushimei.frontend"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            var debugUrl = providers.gradleProperty("SUSHIMEI_DEBUG_BASE_URL").orNull ?: "http://10.0.2.2:8080/"
            if (!debugUrl.endsWith("/")) {
                debugUrl += "/"
            }
            buildConfigField("String", "BASE_URL", "\"${debugUrl}\"")
        }
        release {
            val releaseUrl = providers.gradleProperty("SUSHIMEI_BASE_URL").orNull
            val isReleaseBuild = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            if (isReleaseBuild && releaseUrl == null) {
                throw GradleException("SUSHIMEI_BASE_URL property is required for release builds.")
            }
            if (releaseUrl != null && !releaseUrl.startsWith("https://")) {
                throw GradleException("SUSHIMEI_BASE_URL must use https:// for release builds.")
            }
            if (releaseUrl != null && (releaseUrl.contains("localhost") || releaseUrl.contains("10.0.2.2") || releaseUrl.contains("127.0.0.1"))) {
                throw GradleException("SUSHIMEI_BASE_URL cannot use local development addresses in release builds.")
            }
            buildConfigField("String", "BASE_URL", "\"${releaseUrl ?: "https://api.invalid"}\"")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.retrofit)
    implementation(libs.converter.gson)

    // Corrutinas para asincronismo
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.coil.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
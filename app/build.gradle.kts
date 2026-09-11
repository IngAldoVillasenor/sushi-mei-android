plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.cardovia.merkon.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.cardovia.merkon.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    buildTypes {
        debug {
            var debugUrl = providers.gradleProperty("MERKON_DEBUG_BASE_URL").orNull ?: providers.gradleProperty("SUSHIMEI_DEBUG_BASE_URL").orNull ?: "http://10.0.2.2:8080/"
            if (!debugUrl.endsWith("/")) {
                debugUrl += "/"
            }
            buildConfigField("String", "BASE_URL", "\"${debugUrl}\"")

            val debugHost = providers.gradleProperty("MERKON_VERIFICATION_HOST").orNull ?: "verification.local"
            manifestPlaceholders["verificationHost"] = debugHost
            buildConfigField("String", "VERIFICATION_HOST", "\"${debugHost}\"")
        }
        release {
            val releaseUrl = providers.gradleProperty("MERKON_BASE_URL").orNull ?: providers.gradleProperty("SUSHIMEI_BASE_URL").orNull
            val isReleaseBuild = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            if (isReleaseBuild && releaseUrl == null) {
                throw GradleException("MERKON_BASE_URL (or SUSHIMEI_BASE_URL) property is required for release builds.")
            }
            if (releaseUrl != null && !releaseUrl.startsWith("https://")) {
                throw GradleException("MERKON_BASE_URL (or SUSHIMEI_BASE_URL) must use https:// for release builds.")
            }
            if (releaseUrl != null && (releaseUrl.contains("localhost") || releaseUrl.contains("10.0.2.2") || releaseUrl.contains("127.0.0.1"))) {
                throw GradleException("MERKON_BASE_URL (or SUSHIMEI_BASE_URL) cannot use local development addresses in release builds.")
            }
            buildConfigField("String", "BASE_URL", "\"${releaseUrl ?: "https://api.invalid"}\"")

            val releaseHostRaw = providers.gradleProperty("MERKON_VERIFICATION_HOST").orNull
            if (isReleaseBuild && releaseHostRaw == null) {
                throw GradleException("MERKON_VERIFICATION_HOST property is required for release builds.")
            }
            if (isReleaseBuild && releaseHostRaw != null) {
                if (releaseHostRaw.isBlank()) throw GradleException("MERKON_VERIFICATION_HOST cannot be blank.")
                if (releaseHostRaw != releaseHostRaw.trim()) throw GradleException("MERKON_VERIFICATION_HOST cannot contain leading or trailing whitespace.")
                val releaseHost = releaseHostRaw.lowercase()
                if (releaseHost.any { it <= ' ' }) throw GradleException("MERKON_VERIFICATION_HOST cannot contain whitespace or control characters.")
                if (releaseHost.contains("://") || releaseHost.contains("/") || releaseHost.contains(":") || releaseHost.contains("?") || releaseHost.contains("#") || releaseHost.contains("@")) {
                    throw GradleException("MERKON_VERIFICATION_HOST must be a plain ASCII hostname only, without scheme, port, path, query, fragment, or userinfo.")
                }
                if (!releaseHost.contains(".")) {
                    throw GradleException("MERKON_VERIFICATION_HOST must be a multi-label hostname in release builds.")
                }
                if (releaseHost.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
                    throw GradleException("MERKON_VERIFICATION_HOST cannot be an IP address in release builds.")
                }
                val localSuffixes = listOf(".localhost", ".local", ".invalid", ".test", ".example")
                if (localSuffixes.any { releaseHost.endsWith(it) }) {
                    throw GradleException("MERKON_VERIFICATION_HOST cannot use reserved/local development DNS suffixes in release builds.")
                }
                val labels = releaseHost.split(".")
                if (labels.size < 2) throw GradleException("MERKON_VERIFICATION_HOST must contain at least one dot (a valid TLD).")
                for (label in labels) {
                    if (label.isEmpty()) throw GradleException("MERKON_VERIFICATION_HOST cannot contain empty DNS labels.")
                    if (label.startsWith("-") || label.endsWith("-")) throw GradleException("MERKON_VERIFICATION_HOST DNS labels cannot start or end with a hyphen.")
                    if (!label.matches(Regex("^[a-z0-9-]+$"))) throw GradleException("MERKON_VERIFICATION_HOST DNS labels must contain only alphanumeric characters and hyphens.")
                }
            }
            val safeReleaseHost = releaseHostRaw ?: "verification.invalid"
            manifestPlaceholders["verificationHost"] = safeReleaseHost
            buildConfigField("String", "VERIFICATION_HOST", "\"${safeReleaseHost}\"")

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
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
}
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

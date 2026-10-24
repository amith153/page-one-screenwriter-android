import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Signing key for APKs you share. Keep keystore.properties + app/pageone-release.jks safe and backed up:
// every future update must be signed with the same key or phones will refuse to update.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Built-in live-sync server. Kept out of source control in collab.properties.
val collabProps = Properties().apply {
    val f = rootProject.file("collab.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.amit.pageone"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.amit.pageone"
        minSdk = 26          // Android 8.0+ — installs on almost every phone in use
        targetSdk = 36
        versionCode = 11
        versionName = "3.3"

        resValue("string", "collab_project_id", collabProps.getProperty("projectId", ""))
        resValue("string", "collab_api_key", collabProps.getProperty("apiKey", ""))
        resValue("string", "collab_app_id", collabProps.getProperty("appId", ""))
        // Optional: a free TMDB API key (themoviedb.org → Settings → API) for daily film stills.
        resValue("string", "tmdb_key", collabProps.getProperty("tmdbKey", ""))
        // Optional: an OMDb key (omdbapi.com) — film details + posters as a fallback source.
        resValue("string", "omdb_key", collabProps.getProperty("omdbKey", ""))
    }

    signingConfigs {
        create("share") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Shrinks the APK from ~75 MB to ~10 MB so it's easy to send.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("share")
        }
        debug {
            // Same key as release, so debug and release builds can update each other
            // and Google sign-in (Drive) only needs one SHA-1 registered.
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("share")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        resValues = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Live collaboration (configured at runtime from Settings — no google-services.json needed)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)

    // Google Drive backup (each user signs in with their own Google account)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Clickable notes and reference links in annotated PDFs
    implementation(libs.pdfbox)

    // JVM unit tests for the pure-Kotlin core (app/src/test)
    testImplementation(libs.junit)
    testImplementation(libs.orgjson) // real org.json on the JVM (Android's is a stub in unit tests)
}

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val appId = "com.statushub"

// Firebase activates only when google-services.json is registered for this app id,
// otherwise the google-services plugin would fail the build.
val firebaseConfigured = file("google-services.json").let { it.exists() && it.readText().contains("\"$appId\"") }
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
} else {
    logger.warn("google-services.json has no client for $appId: analytics and crash reporting are disabled")
}

// AdMob app id for release builds. A missing or wrong id crashes the app at startup,
// so release builds fail early instead of shipping a broken app.
val admobAppId: String = (project.findProperty("ADMOB_APP_ID") as String?).orEmpty()
tasks.matching { it.name.startsWith("assembleRelease") || it.name.startsWith("bundleRelease") }.configureEach {
    doFirst {
        require(Regex("ca-app-pub-\\d+~\\d+").matches(admobAppId)) {
            "Set ADMOB_APP_ID=ca-app-pub-XXXXXXXX~YYYYYYYY in gradle.properties before building a release"
        }
    }
}

// Upload key (keystore/ is git-ignored). Release builds are signed only when it exists.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.statushub"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = appId
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Google's sample AdMob app id: debug builds only ever show test ads
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        }
        release {
            // Real AdMob app id (AdMob → Apps → App settings → "App ID", contains '~'), set in gradle.properties
            manifestPlaceholders["admobAppId"] = admobAppId
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlinOptions {
        jvmTarget = "11"
    }
    androidResources {
        // Lets users pick the app language in Android 13+ system settings
        generateLocaleConfig = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // ExoPlayer for video playback
    implementation(libs.exoplayer)
    
    // AdMob + Google consent (UMP) for EEA/UK users
    implementation(libs.google.admob)
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")
    // Detects app foreground/background for app-open ads
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    
    // Permissions
    implementation(libs.accompanist.permissions)
    
    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    
    // Network - OkHttp for downloading videos
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Analytics + crash reporting
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // In-app rating prompt
    implementation(libs.play.review.ktx)

    // Background check for new statuses
    implementation(libs.androidx.work.runtime.ktx)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
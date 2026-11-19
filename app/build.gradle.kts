plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.autosplit.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.elioux.launcher"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // Kunci tanda tangan tetap agar update APK bisa dipasang di atas versi sebelumnya.
    // Kunci ini hanya untuk aplikasi pribadi yang dipasang manual (bukan Play Store).
    signingConfigs {
        create("autosplit") {
            storeFile = file("autosplit.keystore")
            storePassword = "autosplit"
            keyAlias = "autosplit"
            keyPassword = "autosplit"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("autosplit")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("autosplit")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

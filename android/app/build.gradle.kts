plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after Android & Kotlin.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.nastools.app"
    compileSdk = 36          // 拉到 36 以满足 flutter_plugin_android_lifecycle / shared_preferences_android 的要求
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "com.nastools.app"
        minSdk = 24             // Android 7.0 —— SAF tree URI 起步
        targetSdk = 34          // Android 14
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}

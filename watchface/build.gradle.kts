plugins {
    id("com.android.application")
}

// Cadran au format Watch Face Format (XML, sans code) — requis sur Wear OS récent.
android {
    namespace = "fr.stephane.socialbattery.watchface"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.stephane.socialbattery.watchface"
        minSdk = 33
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1) + 100
        versionName = "2.0"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
}

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
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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

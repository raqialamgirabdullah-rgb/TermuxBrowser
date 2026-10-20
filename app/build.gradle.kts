plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rahcare.termuxbrowser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rahcare.termuxbrowser"
        minSdk = 24
        targetSdk = 34
        versionCode = (findProperty("vc") as String?)?.toIntOrNull() ?: 1
        versionName = "0.1"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // ওপেন-সোর্স প্রজেক্ট: CI-তে ডিবাগ কী দিয়ে সাইন হয়, তাই প্রতিটি বিল্ড আগেরটার ওপর আপডেট হয়
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.centralbrain.demo"
    compileSdk = 36
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.centralbrain.demo"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        aidl = false
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":central-brain-sdk"))
}

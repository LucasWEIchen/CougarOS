plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.centralbrain.sdk"
    compileSdk = 36
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")
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
    testImplementation(libs.junit)
}

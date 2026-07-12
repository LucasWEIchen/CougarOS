plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.centralbrain.nativebridge"
    compileSdk = 36
    buildToolsVersion = "37.0.0"
    ndkVersion = "27.3.13750724"

    defaultConfig {
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                targets += "central_brain_native"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
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

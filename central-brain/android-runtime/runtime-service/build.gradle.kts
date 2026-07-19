plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.centralbrain.runtime"
    compileSdk = 36
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.centralbrain.runtime"
        minSdk = 33
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0-b3"

        javaCompileOptions {
            annotationProcessorOptions {
                argument("room.schemaLocation", "$projectDir/schemas")
                argument("room.incremental", "true")
                argument("room.generateKotlin", "false")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "OLLAMA_DEVELOPMENT_ENABLED", "true")
            buildConfigField("String", "OLLAMA_BASE_URL", "\"http://127.0.0.1:11434\"")
            buildConfigField("String", "OLLAMA_MODEL", "\"qwen3.5:27b-optimized\"")
        }
        getByName("release") {
            buildConfigField("boolean", "OLLAMA_DEVELOPMENT_ENABLED", "false")
            buildConfigField("String", "OLLAMA_BASE_URL", "\"http://169.254.208.110:11434\"")
            buildConfigField("String", "OLLAMA_MODEL", "\"UNCONFIGURED\"")
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":central-brain-sdk"))
    implementation(project(":native-runtime"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.gson)
    annotationProcessor(libs.androidx.room.compiler)
    testImplementation(libs.junit)
}

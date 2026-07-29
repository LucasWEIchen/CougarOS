plugins {
    alias(libs.plugins.android.application)
}

val developmentOllama = providers.gradleProperty("centralBrainDevelopmentOllama")
    .map { it.equals("true", ignoreCase = true) }
    .getOrElse(false)

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
            buildConfigField(
                "String",
                "MODEL_GATEWAY_PROFILE",
                if (developmentOllama) "\"development_wsl_ollama\""
                else "\"direct_model_service\""
            )
            buildConfigField(
                "boolean",
                "OLLAMA_DEVELOPMENT_ENABLED",
                developmentOllama.toString()
            )
            buildConfigField("String", "OLLAMA_BASE_URL", "\"http://127.0.0.1:11434\"")
            buildConfigField("String", "OLLAMA_MODEL", "\"qwen3.5:27b-optimized\"")
            buildConfigField(
                "boolean",
                "DIRECT_MODEL_SERVICE_ENDPOINT_CONFIGURED",
                (!developmentOllama).toString()
            )
            buildConfigField(
                "boolean",
                "DIRECT_MODEL_SERVICE_ROUTING_ENABLED",
                "false"
            )
            buildConfigField(
                "String",
                "DIRECT_MODEL_SERVICE_BASE_URL",
                "\"http://169.254.208.110:11434\""
            )
            buildConfigField("boolean", "OPENCLAW_TARGET_ENDPOINT_CONFIGURED", "false")
            buildConfigField(
                "boolean",
                "OPENCLAW_TARGET_ROUTING_ENABLED",
                "false"
            )
            buildConfigField(
                "boolean",
                "OPENCLAW_DEVELOPMENT_ROUTING_ENABLED",
                "false"
            )
            buildConfigField(
                "String",
                "OPENCLAW_BASE_URL",
                "\"\""
            )
            buildConfigField(
                "int",
                "OPENCLAW_PROTOCOL_VERSION",
                "0"
            )
        }
        getByName("release") {
            buildConfigField(
                "String",
                "MODEL_GATEWAY_PROFILE",
                "\"direct_model_service\""
            )
            buildConfigField("boolean", "OLLAMA_DEVELOPMENT_ENABLED", "false")
            buildConfigField("String", "OLLAMA_BASE_URL", "\"http://169.254.208.110:11434\"")
            buildConfigField("String", "OLLAMA_MODEL", "\"UNCONFIGURED\"")
            buildConfigField(
                "boolean",
                "DIRECT_MODEL_SERVICE_ENDPOINT_CONFIGURED",
                "true"
            )
            buildConfigField(
                "boolean",
                "DIRECT_MODEL_SERVICE_ROUTING_ENABLED",
                "false"
            )
            buildConfigField(
                "String",
                "DIRECT_MODEL_SERVICE_BASE_URL",
                "\"http://169.254.208.110:11434\""
            )
            buildConfigField("boolean", "OPENCLAW_TARGET_ENDPOINT_CONFIGURED", "false")
            buildConfigField("boolean", "OPENCLAW_TARGET_ROUTING_ENABLED", "false")
            buildConfigField("boolean", "OPENCLAW_DEVELOPMENT_ROUTING_ENABLED", "false")
            buildConfigField("String", "OPENCLAW_BASE_URL", "\"\"")
            buildConfigField("int", "OPENCLAW_PROTOCOL_VERSION", "0")
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

plugins {
    alias(libs.plugins.android.application)
}

val targetOpenClaw = providers.gradleProperty("centralBrainTargetOpenClaw")
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
                if (targetOpenClaw) "\"target_openclaw_transitional\""
                else "\"development_wsl_ollama\""
            )
            buildConfigField(
                "boolean",
                "OLLAMA_DEVELOPMENT_ENABLED",
                (!targetOpenClaw).toString()
            )
            buildConfigField("String", "OLLAMA_BASE_URL", "\"http://127.0.0.1:11434\"")
            buildConfigField("String", "OLLAMA_MODEL", "\"qwen3.5:27b-optimized\"")
            buildConfigField("boolean", "OPENCLAW_TARGET_ENDPOINT_CONFIGURED", "true")
            buildConfigField(
                "boolean",
                "OPENCLAW_TARGET_ROUTING_ENABLED",
                targetOpenClaw.toString()
            )
            buildConfigField(
                "String",
                "OPENCLAW_BASE_URL",
                "\"ws://169.254.208.110:18789\""
            )
            buildConfigField("int", "OPENCLAW_PROTOCOL_VERSION", "3")
        }
        getByName("release") {
            buildConfigField(
                "String",
                "MODEL_GATEWAY_PROFILE",
                "\"target_openclaw_transitional\""
            )
            buildConfigField("boolean", "OLLAMA_DEVELOPMENT_ENABLED", "false")
            buildConfigField("String", "OLLAMA_BASE_URL", "\"http://169.254.208.110:11434\"")
            buildConfigField("String", "OLLAMA_MODEL", "\"UNCONFIGURED\"")
            buildConfigField("boolean", "OPENCLAW_TARGET_ENDPOINT_CONFIGURED", "true")
            buildConfigField("boolean", "OPENCLAW_TARGET_ROUTING_ENABLED", "false")
            buildConfigField(
                "String",
                "OPENCLAW_BASE_URL",
                "\"ws://169.254.208.110:18789\""
            )
            buildConfigField("int", "OPENCLAW_PROTOCOL_VERSION", "3")
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

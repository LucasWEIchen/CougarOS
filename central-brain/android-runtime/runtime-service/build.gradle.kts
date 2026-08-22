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
        versionCode = 4
        versionName = "0.4.0-b4"

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
                else "\"development_ty1100_vllm\""
            )
            buildConfigField(
                "boolean",
                "VLLM_DEVELOPMENT_ENABLED",
                (!targetOpenClaw).toString()
            )
            buildConfigField("String", "VLLM_BASE_URL", "\"http://127.0.0.1:10030\"")
            buildConfigField("String", "VLLM_MODEL", "\"Qwen3.5-9B-AWQ\"")
            buildConfigField("String", "VLLM_GENERAL_BASE_URL", "\"http://127.0.0.1:10030\"")
            buildConfigField("String", "VLLM_GENERAL_MODEL", "\"Qwen3.5-9B-AWQ\"")
            buildConfigField("int", "VLLM_GENERAL_CONTEXT_TOKENS", "8192")
            buildConfigField("String", "VLLM_SMOKING_BASE_URL", "\"http://127.0.0.1:10031\"")
            buildConfigField("String", "VLLM_SMOKING_MODEL", "\"Qwen3.5-2B-AWQ\"")
            buildConfigField("int", "VLLM_SMOKING_CONTEXT_TOKENS", "4096")
            buildConfigField("boolean", "VLLM_MODEL_ROUTING_ENABLED", "true")
            buildConfigField("boolean", "VLLM_PREWARM_REQUIRED", "true")
            buildConfigField(
                "boolean",
                "OLLAMA_DEVELOPMENT_ENABLED",
                "false"
            )
            buildConfigField("String", "OLLAMA_BASE_URL", "\"http://127.0.0.1:11434\"")
            buildConfigField("String", "OLLAMA_MODEL", "\"UNCONFIGURED\"")
            buildConfigField("boolean", "OPENCLAW_TARGET_ENDPOINT_CONFIGURED", "true")
            buildConfigField(
                "boolean",
                "OPENCLAW_TARGET_ROUTING_ENABLED",
                targetOpenClaw.toString()
            )
            buildConfigField(
                "boolean",
                "OPENCLAW_DEVELOPMENT_ROUTING_ENABLED",
                "false"
            )
            buildConfigField(
                "String",
                "OPENCLAW_BASE_URL",
                if (targetOpenClaw) "\"ws://169.254.208.110:18789\""
                else "\"ws://127.0.0.1:18789\""
            )
            buildConfigField(
                "int",
                "OPENCLAW_PROTOCOL_VERSION",
                "3"
            )
        }
        getByName("release") {
            buildConfigField(
                "String",
                "MODEL_GATEWAY_PROFILE",
                "\"target_openclaw_transitional\""
            )
            buildConfigField("boolean", "VLLM_DEVELOPMENT_ENABLED", "false")
            buildConfigField("String", "VLLM_BASE_URL", "\"UNCONFIGURED\"")
            buildConfigField("String", "VLLM_MODEL", "\"UNCONFIGURED\"")
            buildConfigField("String", "VLLM_GENERAL_BASE_URL", "\"UNCONFIGURED\"")
            buildConfigField("String", "VLLM_GENERAL_MODEL", "\"UNCONFIGURED\"")
            buildConfigField("int", "VLLM_GENERAL_CONTEXT_TOKENS", "0")
            buildConfigField("String", "VLLM_SMOKING_BASE_URL", "\"UNCONFIGURED\"")
            buildConfigField("String", "VLLM_SMOKING_MODEL", "\"UNCONFIGURED\"")
            buildConfigField("int", "VLLM_SMOKING_CONTEXT_TOKENS", "0")
            buildConfigField("boolean", "VLLM_MODEL_ROUTING_ENABLED", "false")
            buildConfigField("boolean", "VLLM_PREWARM_REQUIRED", "false")
            buildConfigField("boolean", "OLLAMA_DEVELOPMENT_ENABLED", "false")
            buildConfigField("String", "OLLAMA_BASE_URL", "\"http://169.254.208.110:11434\"")
            buildConfigField("String", "OLLAMA_MODEL", "\"UNCONFIGURED\"")
            buildConfigField("boolean", "OPENCLAW_TARGET_ENDPOINT_CONFIGURED", "true")
            buildConfigField("boolean", "OPENCLAW_TARGET_ROUTING_ENABLED", "false")
            buildConfigField("boolean", "OPENCLAW_DEVELOPMENT_ROUTING_ENABLED", "false")
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

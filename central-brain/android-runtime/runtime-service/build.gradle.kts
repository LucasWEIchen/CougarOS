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

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val parserSecurityFuzzRuntime by configurations.creating

dependencies {
    implementation(project(":central-brain-sdk"))
    implementation(project(":native-runtime"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.gson)
    annotationProcessor(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    add(parserSecurityFuzzRuntime.name, libs.jazzer)
}

val prepareParserSecurityFuzzCorpus by tasks.registering(Sync::class) {
    from("src/test/resources/fuzz/p9-w03f/parser-security")
    into(layout.buildDirectory.dir("fuzz/p9-w03f/corpus"))
    outputs.upToDateWhen { false }
}

val debugUnitTests = tasks.withType<Test>().matching { it.name == "testDebugUnitTest" }

tasks.register<JavaExec>("fuzzParserSecurity") {
    group = "verification"
    description = "Runs the bounded P9-W03f Jazzer parser-security campaign."
    dependsOn(debugUnitTests, prepareParserSecurityFuzzCorpus)
    mainClass.set("com.code_intelligence.jazzer.Jazzer")
    workingDir(projectDir)
    environment("CENTRAL_BRAIN_FUZZ_STATS", "true")
    jvmArgs("-Xmx1024m")
    outputs.upToDateWhen { false }

    doFirst {
        classpath = debugUnitTests.single().classpath + parserSecurityFuzzRuntime
        val fuzzRoot = layout.buildDirectory.dir("fuzz/p9-w03f").get().asFile
        val corpus = fuzzRoot.resolve("corpus")
        val artifacts = fuzzRoot.resolve("artifacts")
        artifacts.mkdirs()
        val seconds = providers.gradleProperty("centralBrainFuzzSeconds")
            .orElse("20")
            .get()
        args = listOf(
            "--target_class=com.centralbrain.runtime.security.ParserSecurityFuzzTarget",
            "-max_total_time=$seconds",
            "-timeout=5",
            "-rss_limit_mb=2048",
            "-max_len=65538",
            "-print_final_stats=1",
            "-artifact_prefix=${artifacts.absolutePath}/",
            corpus.absolutePath
        )
    }
}

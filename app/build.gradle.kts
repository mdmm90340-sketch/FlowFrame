import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

apply(from = rootProject.file("gradle/patch-ffmpeg.gradle.kts"))

val signingPropertiesFile = providers.gradleProperty("flowframe.signingProperties")
    .orElse(providers.environmentVariable("FLOWFRAME_SIGNING_PROPERTIES"))
    .orNull
    ?.let(::File)
    ?: rootProject.file("keystore.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use { load(it) }
    }
}

val testAbi = providers.gradleProperty("flowframe.testAbi").orNull
require(testAbi == null || testAbi == "x86_64") {
    "flowframe.testAbi only supports x86_64; omit it for the arm64-v8a production build."
}
val targetAbi = testAbi ?: "arm64-v8a"
val instrumentationBuildType = providers.gradleProperty("flowframe.testBuildType").orElse("debug").get()
require(instrumentationBuildType in setOf("debug", "release")) { "Unsupported instrumentation build type" }

android {
    namespace = "com.flowframe.app"
    compileSdk = 35
    testBuildType = instrumentationBuildType

    defaultConfig {
        applicationId = "com.flowframe.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = "1.2.2"
        buildConfigField("String", "NATIVE_ABI", "\"$targetAbi\"")
        testInstrumentationRunner = if (instrumentationBuildType == "release") {
            "com.flowframe.app.ReleaseNativeInstrumentation"
        } else "androidx.test.runner.AndroidJUnitRunner"
        testProguardFiles("proguard-test-rules.pro")

        vectorDrawables.useSupportLibrary = true
        ndk {
            abiFilters += targetAbi
        }
    }

    signingConfigs {
        if (signingPropertiesFile.isFile) {
            create("release") {
                val configuredStoreFile = File(signingProperties.getProperty("storeFile"))
                storeFile = if (configuredStoreFile.isAbsolute) {
                    configuredStoreFile
                } else {
                    signingPropertiesFile.parentFile.resolve(configuredStoreFile)
                }
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    val ytdlVersion = "0.18.1"

    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("io.github.junkfood02.youtubedl-android:library:$ytdlVersion")
    // The library dependency supplies the unchanged FFmpeg POM's common/AndroidX/IO dependencies.
    implementation(files(tasks.named("prepareFfmpeg16k")))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("com.google.errorprone:error_prone_annotations:2.18.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

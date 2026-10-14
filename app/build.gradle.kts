plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nousresearch.hermesmobile"
    compileSdk = 37

    defaultConfig {
        // The package each user registers in their own Firebase project: keep it stable across releases.
        // Forks can override it with -Phermes.applicationId=… (their users then register that package).
        applicationId = providers.gradleProperty("hermes.applicationId").orElse("io.github.adrianlaborda.hermesmobile").get()
        minSdk = 26
        targetSdk = 36
        versionCode = 36
        versionName = "0.3.0-beta.2"

        for (name in listOf("google_app_id", "gcm_defaultSenderId", "google_api_key", "project_id")) {
            resValue("string", name, providers.gradleProperty("firebase.$name").orElse("").get())
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    // Release signing stays outside the repo: the keystore path comes from ~/.gradle/gradle.properties
    // (hermes.signing.storeFile) and its password from HERMES_SIGNING_PASSWORD (scripts/build-release.sh
    // reads it from the macOS Keychain). Without them the release APK is built unsigned, as in CI.
    val signingStore = providers.gradleProperty("hermes.signing.storeFile").orNull
    val signingPassword = providers.environmentVariable("HERMES_SIGNING_PASSWORD").orNull
    if (signingStore != null && signingPassword != null) {
        signingConfigs.create("release") {
            storeFile = file(signingStore)
            storeType = "PKCS12"
            storePassword = signingPassword
            keyAlias = providers.gradleProperty("hermes.signing.keyAlias").orElse("hermes-mobile").get()
            keyPassword = signingPassword
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    bundle { language { enableSplit = false } }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    implementation("com.squareup.okhttp3:okhttp:5.5.0")

    implementation("org.commonmark:commonmark:0.30.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.30.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.30.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

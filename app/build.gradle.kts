plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pannulabs.smarttrade"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pannulabs.smarttrade"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.1"
    }

    // One fixed release key, so every new APK installs over the previous one as an update.
    // CI writes the keystore from the SIGNING_KEYSTORE_B64 / SIGNING_PASSWORD repo secrets.
    val keystoreFile = System.getenv("SIGNING_KEYSTORE")?.let { file(it) }
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    signingConfigs {
        if (keystoreFile != null && keystoreFile.exists() && !signingPassword.isNullOrEmpty()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = signingPassword
                keyAlias = "smarttrade"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Local builds without the key fall back to the debug key (those cannot update a CI-installed app).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

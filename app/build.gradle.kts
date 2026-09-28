import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

fun signingValue(propertyName: String, envName: String): String? =
    localProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envName)?.takeIf { it.isNotBlank() }

val meiocrSigningStoreFile = signingValue(
    "meiocr.signing.storeFile",
    "MEIOCR_SIGNING_STORE_FILE"
)?.let(::file)
val meiocrSigningStorePassword = signingValue(
    "meiocr.signing.storePassword",
    "MEIOCR_SIGNING_STORE_PASSWORD"
)
val meiocrSigningKeyAlias = signingValue(
    "meiocr.signing.keyAlias",
    "MEIOCR_SIGNING_KEY_ALIAS"
)
val meiocrSigningKeyPassword = signingValue(
    "meiocr.signing.keyPassword",
    "MEIOCR_SIGNING_KEY_PASSWORD"
) ?: meiocrSigningStorePassword

val hasMeiocrSigning =
    meiocrSigningStoreFile?.isFile == true &&
        meiocrSigningStorePassword != null &&
        meiocrSigningKeyAlias != null &&
        meiocrSigningKeyPassword != null

plugins {
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "de.haberland.meiocrworkout"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.haberland.meiocrworkout"
        minSdk = 28
        targetSdk = 36
        versionCode = 13
        versionName = "0.10.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasMeiocrSigning) {
            create("meiocrStable") {
                storeFile = requireNotNull(meiocrSigningStoreFile)
                storePassword = requireNotNull(meiocrSigningStorePassword)
                keyAlias = requireNotNull(meiocrSigningKeyAlias)
                keyPassword = requireNotNull(meiocrSigningKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            if (hasMeiocrSigning) {
                signingConfig = signingConfigs.getByName("meiocrStable")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // Crash reporting only. Firebase Analytics is intentionally not included.
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    // Play In-App Updates still pulls Fragment APIs transitively; pin a modern
    // Fragment version so Activity Result APIs are safe and release lint can verify it.
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0") // viewModelScope, used by AppViewModel
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // runTest / StandardTestDispatcher for AppViewModelTest (viewModelScope always
    // dispatches on Dispatchers.Main, which needs a test dispatcher in unit tests).
    // Version isn't pinned to anything load-bearing here - align it with whatever
    // kotlinx-coroutines-core version Compose/lifecycle-viewmodel-ktx already resolve
    // to transitively, if Gradle flags a conflict.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

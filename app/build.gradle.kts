plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

fun versionCodeFrom(versionName: String): Int {
    val match = Regex(
        """^(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)(?:\.(\d+))?)?$""",
    ).matchEntire(versionName) ?: error(
        "versionName must use major.minor.patch with an optional alpha, beta, or rc suffix: $versionName",
    )

    val (majorText, minorText, patchText, qualifier, sequenceText) = match.destructured
    val major = majorText.toInt()
    val minor = minorText.toInt()
    val patch = patchText.toInt()
    val sequence = sequenceText.ifEmpty { "0" }.toInt()

    require(major in 0..2099) { "versionName major version must be between 0 and 2099" }
    require(minor in 0..99) { "versionName minor version must be between 0 and 99" }
    require(patch in 0..99) { "versionName patch version must be between 0 and 99" }
    require(sequence in 0..29) { "versionName prerelease sequence must be between 0 and 29" }

    val qualifierCode = when (qualifier) {
        "alpha" -> sequence
        "beta" -> 30 + sequence
        "rc" -> 60 + sequence
        else -> 99
    }
    val versionCode = major * 1_000_000 + minor * 10_000 + patch * 100 + qualifierCode
    require(versionCode > 0) { "versionName must produce a positive versionCode" }
    return versionCode
}

val releaseKeystorePath = providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull
val appVersionName = providers.gradleProperty("versionName").orNull ?: "1.3.0"
val appVersionCode = providers.gradleProperty("versionCode").orNull?.toInt()
    ?: versionCodeFrom(appVersionName)

android {
    namespace = "com.yamibo.pocket300"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.yamibo.pocket300"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coil.compose)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

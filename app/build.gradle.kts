import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing: keystore.properties on your machine, or SIGNING_* environment variables in CI (release.yml).
// With neither, the release build is unsigned. The key itself is never committed.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(property: String, env: String): String? = keystoreProps.getProperty(property) ?: System.getenv(env)

val signing = mapOf(
    "storeFile" to signingValue("storeFile", "SIGNING_STORE_FILE"),
    "storePassword" to signingValue("storePassword", "SIGNING_STORE_PASSWORD"),
    "keyAlias" to signingValue("keyAlias", "SIGNING_KEY_ALIAS"),
    "keyPassword" to signingValue("keyPassword", "SIGNING_KEY_PASSWORD"),
)
val canSign = signing.values.none { it.isNullOrEmpty() }

android {
    namespace = "com.ahmedalsinan.dumbcallerid"
    compileSdk = 36

    // For the update_url resource below.
    buildFeatures {
        resValues = true
    }

    defaultConfig {
        applicationId = "com.ahmedalsinan.dumbcallerid"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
        // Where Updates.kt looks for new versions: this repository's latest GitHub release.
        resValue("string", "update_url", "https://api.github.com/repos/tekno0ryder/dumb-caller-id/releases/latest")
        // scripts/try-update.sh builds a "newer" copy with these, to try updating without a real release.
        providers.gradleProperty("tryVersionCode").orNull?.let { versionCode = it.toInt() }
        providers.gradleProperty("tryVersionName").orNull?.let { versionName = it }
    }

    signingConfigs {
        create("release") {
            if (canSign) {
                storeFile = file(signing.getValue("storeFile")!!)
                storePassword = signing.getValue("storePassword")
                keyAlias = signing.getValue("keyAlias")
                keyPassword = signing.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        // Debug builds look for updates on this computer instead (scripts/try-update.sh), never on GitHub.
        debug {
            resValue("string", "update_url", "http://127.0.0.1:8765/latest.json")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (canSign) signingConfig = signingConfigs.getByName("release")
        }
    }

    // Any new lint warning fails the build; the accepted ones are listed in lint.xml.
    lint {
        warningsAsErrors = true
        abortOnError = true
    }

    // Robolectric runs Android code in plain unit tests, with the app's real resources.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}

// DeviceTest and UpdatesTest read these files directly, so editing them must re-run the tests rather than reuse
// old results.
tasks.withType<Test>().configureEach {
    inputs.file("src/main/AndroidManifest.xml")
    inputs.file("build.gradle.kts") // UpdatesTest checks the update addresses in it
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** This release's version code; CI debug builds build on it (see androidComponents). */
val BASE_VERSION_CODE = 32
/** The run number of an optimised build from main (android-ci.yml), else null. */
val plusBuild: Int? = System.getenv("PLUS_BUILD")?.toIntOrNull()

android {
    namespace = "com.tx24.spicyplayer"
    compileSdk = 37

    /** A value from the git-ignored root `.env`, else the environment (CI). */
    fun localEnv(name: String): String {
        val dotEnv = rootProject.file(".env")
        val fromFile = if (!dotEnv.isFile) null else dotEnv.useLines { lines ->
            lines.map(String::trim)
                .firstOrNull { it.startsWith("$name=") }
                ?.substringAfter('=')?.trim()?.trim('"', '\'')
        }
        return fromFile ?: System.getenv(name).orEmpty()
    }

    defaultConfig {
        // Spicy Lyrics Plus has its own ID, so it installs beside the official Spicy Lyrics Mobile.
        applicationId = "com.behnoud.spicylyrics"
        minSdk = 23
        targetSdk = 36
        // CI debug builds count up by run: see androidComponents below.
        versionCode = BASE_VERSION_CODE
        versionName = "0.20.0"
        // A publishable (sl_pk_) key, made to ship in clients: SL rate-limits it per viewer IP.
        val clientKey = localEnv("SPICY_LYRICS_CLIENT_KEY")
        require(clientKey.isBlank() || Regex("sl_pk_[A-Za-z0-9_-]+").matches(clientKey)) {
            "SPICY_LYRICS_CLIENT_KEY must be a publishable sl_pk_ key (and nothing else on its line in .env)"
        }
        val key = clientKey.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "SPICY_LYRICS_CLIENT_KEY", "\"$key\"")
        // The project's own Umami site for anonymous usage stats. Blank (forks, local builds
        // without it) sends nothing.
        val umamiSite = localEnv("UMAMI_WEBSITE_ID")
        require(umamiSite.isBlank() || Regex("[0-9a-f-]{36}").matches(umamiSite)) { "UMAMI_WEBSITE_ID must be a UUID" }
        buildConfigField("String", "UMAMI_WEBSITE_ID", "\"$umamiSite\"")
        // Only release builds send; see the build types.
        buildConfigField("boolean", "USAGE_STATS", "false")
    }

    // CI debug builds share one fixed debug key, kept as the DEBUG_KEYSTORE_BASE64 repository
    // secret (the workflow writes it to DEBUG_KEYSTORE_FILE; standard "android" passwords), so
    // each new build installs over the last instead of needing an uninstall. Without the secret,
    // and on local builds, the machine's own debug key signs.
    val ciDebugStore = System.getenv("DEBUG_KEYSTORE_FILE")?.takeIf { it.isNotBlank() && file(it).isFile }
    if (ciDebugStore != null) {
        signingConfigs.getByName("debug") {
            storeFile = file(ciDebugStore)
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            // Its own app next to the release one, so testing never touches the installed release.
            applicationIdSuffix = ".debug"
            // Each CI build a higher version than the last, so it installs as an update.
            versionNameSuffix = System.getenv("GITHUB_RUN_NUMBER")?.let { "-debug.$it" } ?: "-debug"
            resValue("string", "app_name", "Spicy Lyrics Plus Debug")
        }
        getByName("release") {
            // Shrunk and optimised: Compose runs noticeably slower unoptimised (debug builds are
            // also interpreted at first), so judge smoothness on this build, not on debug.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without the release keystore (a local build), sign with the debug key so it installs
            // over an earlier local release build. On CI only for the builds from main (PLUS_BUILD
            // set, with the shared debug key when the secret is there): a tagged release must
            // still fail unsigned.
            if (System.getenv("CI") == null || plusBuild != null) signingConfig = signingConfigs.getByName("debug")
            // A build from main counts up by run, so the app offers each one as an update.
            plusBuild?.let { versionNameSuffix = "-plus.$it" }
            // Off in this fork: the usage statistics went to the original app's own server.
            buildConfigField("boolean", "USAGE_STATS", "false")
        }
        // The release build as its own app, for measuring performance next to the installed release.
        create("profile") {
            initWith(getByName("release"))
            applicationIdSuffix = ".profile"
            versionNameSuffix = "-profile"
            buildConfigField("boolean", "USAGE_STATS", "false")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            resValue("string", "app_name", "Spicy Lyrics Plus Profile")
        }
    }

    val releaseStore = System.getenv("ANDROID_RELEASE_KEYSTORE")
    if (!releaseStore.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = System.getenv("ANDROID_RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_RELEASE_KEY_PASSWORD")
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }

    // Android logging/clock calls are no-ops in JVM tests, so provider code runs there unchanged.
    testOptions {
        unitTests.isReturnDefaultValues = true
        // PublicDocsTest reads these, so an edit to them must rerun the tests.
        unitTests.all {
            it.inputs.files(rootProject.file("CLAUDE.md"), rootProject.fileTree("docs"))
                .withPropertyName("publicDocs")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    packaging.resources.excludes += "/META-INF/{CONTRIBUTORS.md,LICENSE.md,NOTICE.md,README.md}"

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    // Shizuku: grants the permission that hides the status bar clock behind the status bar lyrics.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    // Backdrop blur for SL's glass controls (real blur on Android 12+, a tint below).
    implementation("dev.chrisbanes.haze:haze:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Animated covers: Apple Music's looping HLS artwork, muted, with a disk cache.
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-database:1.11.1")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("javax.inject:javax.inject:1")
    // On-device translation with Gemini Nano, where the phone has it (AICore, Android 8+).
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
}

// CI debug builds: this release's version code times 10,000 plus the run number, so every
// GitHub build is newer than the last and Android's installer offers it as an update.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: return@onVariants
        variant.outputs.forEach { output ->
            output.versionCode.set(BASE_VERSION_CODE * 10_000 + run)
        }
    }
    // Optimised builds from main, the same way, so each installs over the last.
    onVariants(selector().withBuildType("release")) { variant ->
        val run = plusBuild ?: return@onVariants
        variant.outputs.forEach { output ->
            output.versionCode.set(BASE_VERSION_CODE * 10_000 + run)
        }
    }
}

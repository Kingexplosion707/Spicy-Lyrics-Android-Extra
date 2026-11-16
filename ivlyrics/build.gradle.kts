// ivLyrics Android's shared library (github.com/ivLis-Studio/ivLyrics-Android, shared/), brought in
// as it is. Spicy calls it only through SpicyBridge. Local changes: this build file (no Local AI
// native build step), no src/main/cpp, no launcher icons. IVLYRICS_REF in .github/ivlyrics.env is
// the release it was taken from.
plugins {
    id("com.android.library")
}

android {
    namespace = "kr.ivlis.ivlyricsandroid"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.9.0")
}

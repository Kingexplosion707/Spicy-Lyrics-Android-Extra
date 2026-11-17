import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Music Assistant's Android app, as a library inside Spicy Lyrics Plus: its screens, its
// media service and Android Auto, opened from Spicy (see SpicyMusicAssistant.kt).
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.music_assistant.client"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("proguard-rules.pro")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":composeApp"))
    implementation(libs.compose.components.resources)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.media)
    implementation(libs.androidx.car.app)
    implementation(libs.coil)
    implementation(libs.kermit)
}

@CacheableTask
abstract class GenerateLocalesConfig : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourcesDir: DirectoryProperty

    @get:Input
    abstract val baseLanguage: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val qualifier = Regex("""^values-([a-z]{2,3})(?:-r([A-Z]{2}))?$""")
        val locales = resourcesDir.get().asFile.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .map { dir ->
                if (dir.name == "values") baseLanguage.get()
                else qualifier.matchEntire(dir.name)
                    ?.groupValues?.drop(1)?.filter(String::isNotEmpty)?.joinToString("-")
                    ?: error("Unsupported locale directory '${dir.name}'. Teach GenerateLocalesConfig its BCP-47 form.")
            }
            .sorted()

        outputDir.get().asFile.resolve("xml/locales_config.xml").apply { parentFile.mkdirs() }
            .writeText(
                buildString {
                    appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                    appendLine("""<locale-config xmlns:android="http://schemas.android.com/apk/res/android">""")
                    locales.forEach { appendLine("""    <locale android:name="$it" />""") }
                    append("</locale-config>")
                }
            )
    }
}

// Android needs an explicit locale list for the per-app language picker. The shipped
// languages are the Lokalise-synced `values-*` directories, so the list is derived from
// them; a hand-written copy drifts on the next translation pull without any build error.
val generateLocalesConfig = tasks.register<GenerateLocalesConfig>("generateLocalesConfig") {
    resourcesDir.set(project(":composeApp").layout.projectDirectory.dir("src/commonMain/composeResources"))
    baseLanguage.set("en")
    outputDir.set(layout.buildDirectory.dir("generated/res/localesConfig"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(
            generateLocalesConfig,
            GenerateLocalesConfig::outputDir
        )
    }
}


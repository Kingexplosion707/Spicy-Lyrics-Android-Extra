// Spicy Lyrics Plus for Windows (and other desktops): the Android app's Compose UI and renderer on
// Compose Multiplatform. Files of :app that use no Android API compile here as they are; the ones
// that do have desktop versions in src/main/kotlin with the same names.
import java.util.TreeSet
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }

val appSrc = rootProject.file("app/src/main/java")
val sharedDir = layout.buildDirectory.dir("generated/shared/kotlin")

/**
 * Copies the listed :app files (shared-sources.txt, written by sources.py) into the build, with
 * the small desktop changes in patches.txt applied. A patch that no longer finds its text fails the
 * build, so a change in :app can't silently drop one.
 */
val syncShared by tasks.registering {
    val list = file("shared-sources.txt")
    val patchFile = file("patches.txt")
    inputs.file(list); inputs.file(patchFile); inputs.dir(appSrc)
    outputs.dir(sharedDir)
    doLast {
        val out = sharedDir.get().asFile
        out.deleteRecursively()
        val patches = mutableMapOf<String, MutableList<Pair<String, String>>>()
        var current: String? = null
        var old: String? = null
        patchFile.readLines().forEach { line ->
            when {
                line.startsWith("## ") -> current = line.removePrefix("## ").trim()
                line.startsWith("- ") -> old = line.removePrefix("- ")
                line.startsWith("+ ") || line == "+" -> {
                    patches.getOrPut(current!!) { mutableListOf() } += old!! to line.removePrefix("+").removePrefix(" ")
                    old = null
                }
            }
        }
        list.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { rel ->
            var text = File(appSrc, rel).readText()
            val key = rel.removePrefix("com/tx24/spicyplayer/")
            patches[key]?.forEach { (from, to) ->
                require(text.contains(from)) { "patches.txt: '$from' not found in $key" }
                text = text.replace(from, to.replace("\\n", "\n"))
            }
            File(out, rel).apply { parentFile.mkdirs(); writeText(text) }
        }
        writeResources(out, list.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") })
        // ivLyrics' plain-Java song model, which motion lyrics' page is fed through.
        val javaOut = sharedDir.get().asFile.parentFile.resolve("java")
        javaOut.deleteRecursively()
        listOf("LyricsLine", "LyricsResult", "MotionLyricsSong", "TrackSnapshot").forEach { name ->
            val rel = "kr/ivlis/ivlyricsandroid/$name.java"
            rootProject.file("ivlyrics/src/main/java/$rel").copyTo(File(javaOut, rel), overwrite = true)
        }
        val unknown = patches.keys - list.readLines().map { it.trim().removePrefix("com/tx24/spicyplayer/") }.toSet()
        require(unknown.isEmpty()) { "patches.txt names files that aren't shared: $unknown" }
    }
}

/**
 * The :app resources the shared code names: R (one id per string, drawable, font...), BuildConfig,
 * and the resource files themselves under android-res/ in the jar.
 */
fun writeResources(out: File, sharedFiles: List<String>) {
    val res = rootProject.file("app/src/main/res")
    val types = sortedMapOf<String, TreeSet<String>>()
    fun add(type: String, name: String) { types.getOrPut(type) { TreeSet() } += name }
    val strings = sortedMapOf<String, String>()
    val factory = DocumentBuilderFactory.newInstance()
    res.listFiles()!!.filter { it.name == "values" }.flatMap { it.listFiles()!!.toList() }.filter { it.extension == "xml" }.forEach { f ->
        val doc = factory.newDocumentBuilder().parse(f)
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i) as? org.w3c.dom.Element ?: continue
            val name = n.getAttribute("name").takeIf { it.isNotEmpty() } ?: continue
            when (n.tagName) {
                "string" -> { add("string", name); strings[name] = n.textContent.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").trim('"') }
                "bool", "color", "dimen", "integer", "style", "plurals", "array", "string-array" -> add(n.tagName.replace("string-array", "array").replace("plurals", "plurals"), name.replace('.', '_'))
            }
        }
    }
    res.listFiles()!!.filter { it.isDirectory && !it.name.startsWith("values") }.forEach { dir ->
        val type = dir.name.substringBefore('-')
        dir.listFiles()!!.forEach { add(type, it.nameWithoutExtension) }
    }
    // Ids the shared code names (views of Android-only layouts).
    val idRef = Regex("""R\.(id|layout|color|xml|raw|anim)\.(\w+)""")
    sharedFiles.forEach { rel -> idRef.findAll(File(appSrc, rel).readText()).forEach { add(it.groupValues[1], it.groupValues[2]) } }

    val index = StringBuilder()
    val r = StringBuilder("package com.tx24.spicyplayer\n\n/** Resource ids for the shared code (written by the desktop build). */\nobject R {\n")
    types.entries.forEachIndexed { t, (type, names) ->
        r.append("    object $type {\n")
        names.forEachIndexed { i, name ->
            val id = 0x7f000000 or ((t + 1) shl 16) or i
            r.append("        const val $name: Int = $id\n")
            index.append("$id\t$type\t$name\n")
        }
        r.append("    }\n")
    }
    r.append("}\n")
    File(out, "com/tx24/spicyplayer/R.kt").apply { parentFile.mkdirs(); writeText(r.toString()) }

    val resOut = layout.buildDirectory.dir("generated/shared/resources/android-res").get().asFile
    resOut.deleteRecursively(); resOut.mkdirs()
    File(resOut, "index.tsv").writeText(index.toString())
    File(resOut, "strings.tsv").writeText(strings.entries.joinToString("") { "${it.key}\t${it.value.replace("\t", " ").replace("\n", "\\n")}\n" })
    res.listFiles()!!.filter { it.isDirectory && !it.name.startsWith("values") }.forEach { dir ->
        dir.listFiles()!!.forEach { f -> f.copyTo(File(resOut, dir.name.substringBefore('-') + "/" + f.name), overwrite = true) }
    }

    val appBuild = rootProject.file("app/build.gradle.kts").readText()
    val versionName = Regex("versionName = \"([^\"]+)\"").find(appBuild)?.groupValues?.get(1) ?: "0"
    val versionCode = Regex("BASE_VERSION_CODE = (\\d+)").find(appBuild)?.groupValues?.get(1) ?: "1"
    val env = rootProject.file(".env").takeIf { it.exists() }?.readLines().orEmpty()
        .mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim().trim('"') } }.toMap()
    val clientKey = (System.getenv("SPICY_LYRICS_CLIENT_KEY") ?: env["SPICY_LYRICS_CLIENT_KEY"] ?: "").takeIf { it.startsWith("sl_pk_") } ?: ""
    File(out, "com/tx24/spicyplayer/BuildConfig.kt").writeText("""
        |package com.tx24.spicyplayer
        |
        |object BuildConfig {
        |    const val APPLICATION_ID: String = "com.behnoud.spicylyrics"
        |    const val BUILD_TYPE: String = "release"
        |    const val DEBUG: Boolean = false
        |    const val VERSION_NAME: String = "$versionName-desktop"
        |    const val VERSION_CODE: Int = $versionCode
        |    const val SPICY_LYRICS_CLIENT_KEY: String = "$clientKey"
        |    const val UMAMI_WEBSITE_ID: String = ""
        |    const val USAGE_STATS: Boolean = false
        |}
        |""".trimMargin())
}

sourceSets.main {
    kotlin.srcDir("src/main/kotlin")
    kotlin.srcDir("src/shim/kotlin")
    kotlin.srcDir(syncShared)
    java.srcDir(layout.buildDirectory.dir("generated/shared/java"))
    resources.srcDir(layout.buildDirectory.dir("generated/shared/resources"))
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.desktop.windows_x64)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation("dev.chrisbanes.haze:haze:1.7.3")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("net.sf.kxml:kxml2:2.3.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.json:json:20250517")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    // Video: Spotify Canvas MP4s and Apple Music's animated covers (HLS), decoded by FFmpeg.
    implementation("org.bytedeco:javacv:1.5.11")
    implementation("org.bytedeco:ffmpeg:7.1-1.5.11")
    for (platform in listOf("windows-x86_64", "linux-x86_64")) {
        implementation("org.bytedeco:ffmpeg:7.1-1.5.11:$platform")
        implementation("org.bytedeco:javacpp:1.5.11:$platform")
    }
}

compose.desktop {
    application {
        mainClass = "com.tx24.spicyplayer.desktop.MainKt"
        jvmArgs += listOf("-Xmx768m", "-Dsun.java2d.uiScale.enabled=true")
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Spicy Lyrics Plus"
            packageVersion = "1.0.0"
            vendor = "Spicy Lyrics Plus"
            modules("java.net.http", "java.sql", "jdk.unsupported", "java.naming")
            windows {
                menuGroup = "Spicy Lyrics Plus"
                perUserInstall = true
                shortcut = true
                dirChooser = true
                upgradeUuid = "8a2f3c5e-6b1d-4c7a-9e0f-2d4b6a8c1e37"
                iconFile.set(rootProject.file("desktop/compose/icon.ico"))
            }
        }
    }
}
tasks.named("processResources") { dependsOn(syncShared) }
tasks.named("compileJava") { dependsOn(syncShared) }

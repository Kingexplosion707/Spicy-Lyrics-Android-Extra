pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            mavenContent { includeGroupAndSubgroups("org.jetbrains") }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            mavenContent { includeGroupAndSubgroups("org.jetbrains") }
        }
    }
    // Music Assistant's own version catalog, for its modules.
    versionCatalogs {
        create("libs") { from(files("musicassistant/gradle/libs.versions.toml")) }
    }
}

rootProject.name = "Spicy Lyrics Mobile"
include(":app")
include(":core")
include(":ivlyrics")

// Music Assistant (github.com/music-assistant/mobile-app, through Kingexplosion707/mobile-app-balls):
// its shared app and libraries as they are, and its Android app as a library Spicy opens.
for (module in listOf("composeApp", "sendspin-kmp", "nav", "ui-utils", "shared-icons")) {
    include(":$module")
    project(":$module").projectDir = file("musicassistant/$module")
}
include(":musicassistant")
project(":musicassistant").projectDir = file("musicassistant/androidApp")

// The desktop app: the Android UI on Compose Multiplatform.
include(":desktopApp")
project(":desktopApp").projectDir = file("desktop/compose")

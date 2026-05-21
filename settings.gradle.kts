pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android (Track A1.3) ships only on JitPack — Maven
        // Central doesn't carry the artifact. Scoped to that one group
        // so the rest of the build still pulls from Google/Maven Central.
        maven {
            url = java.net.URI("https://jitpack.io")
            content {
                includeGroupByRegex("com\\.github\\.adaptech-cz.*")
            }
        }
    }
}

rootProject.name = "PrivateAICamera"
include(":app")

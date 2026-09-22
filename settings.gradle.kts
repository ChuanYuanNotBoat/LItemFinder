pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "LItemFinder"

include("core", "storage-sqlite", "neoforge-1.21.1")
include("navigation-core")

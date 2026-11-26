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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.TeamNewPipe")
                includeGroup("com.github.TeamNewPipe.NewPipeExtractor")
            }
        }
    }
}

rootProject.name = "Mupa"

include(":app")
include(":core:model")
include(":core:domain")
include(":core:designsystem")
include(":streaming:api")
include(":streaming:innertube")
include(":streaming:jiosaavn")
include(":streaming:aggregator")
include(":playback")
include(":engine:local")

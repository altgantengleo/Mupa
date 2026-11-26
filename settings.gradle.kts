pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Mirror resmi Google untuk Maven Central agar runner GitHub Actions tidak terkena HTTP 429
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Mirror resmi Google untuk Maven Central sebelum repo.maven.apache.org (anti-429 di CI)
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
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

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
        maven { url = uri("https://artifacts.applovin.com/android") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Podcast"
include(":app")
include(":samples")
include(":sharedLibrary")
include(":securedToken")
include(":coreApi")
include(":core")
include(":podcasts")
include(":sharedResources")
include(":featureOnboarding")
include(":hazeAndroid")
include(":sharedPlayer")
include(":sharedFirebase")
include(":ads")
include(":coreRadio")
include(":featureRadio")
//include(":app_traffic")
//include(":featureCheckPenalty")

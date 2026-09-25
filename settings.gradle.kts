pluginManagement {
    repositories {
        // dl.google.com is tampered on this network (fake 404s); these public
        // mirrors carry the same Google Maven artifacts and are reachable.
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://repo.huaweicloud.com/repository/maven/")
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
        // dl.google.com is tampered on this network (fake 404s); these public
        // mirrors carry the same Google Maven artifacts and are reachable.
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://repo.huaweicloud.com/repository/maven/")
        google()
        mavenCentral()
    }
}

rootProject.name = "Liquid Messages"
include(":app")

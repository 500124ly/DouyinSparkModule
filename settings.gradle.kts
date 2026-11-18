pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Xposed/LSPosed 编译期 API 仓库；若该仓库拉取失败，见 README「依赖排障」
        maven { url = uri("https://api.xposed.info/") }
    }
}

rootProject.name = "DouyinSparkModule"
include(":app")

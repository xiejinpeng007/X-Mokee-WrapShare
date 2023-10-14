pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven{
            isAllowInsecureProtocol = true
            url = uri("https://github.com/microsoft/project-rome/raw/mvn-repo/")
        }
    }
}
rootProject.name = "WarpShare"
include(":app")
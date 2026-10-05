pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "GlassSoundbar"
include(":app", ":liquidglass")
project(":liquidglass").projectDir = file("C:/IDE/Android/liquidglass_sketch/liquidglass")

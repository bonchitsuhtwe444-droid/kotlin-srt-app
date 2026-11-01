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
        maven("https://jitpack.io") // FFmpeg လို library များအတွက် ဤလိုင်းကို ထပ်ဖြည့်ထားသည်
    }
}
rootProject.name = "SrtApp"
include(":app")

plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.4"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":generation-engine"))
    implementation(project(":analysis-core"))
}

intellij {
    version.set("2024.1.4")
    type.set("IC")
    plugins.set(listOf("java"))
    updateSinceUntilBuild.set(false)
}

tasks {
    patchPluginXml {
        sinceBuild.set("241")
        // Wide upper bound for local ZIP installs; tighten before Marketplace publish.
        untilBuild.set("999.*")
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = freeCompilerArgs + "-Xjvm-default=all"
        }
    }
    buildSearchableOptions {
        enabled = false
    }
}

group = rootProject.group
version = rootProject.version

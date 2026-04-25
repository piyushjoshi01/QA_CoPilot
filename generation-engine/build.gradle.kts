plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.5"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
    implementation(project(":analysis-core"))

    // ── Feature 2: Document parsing (optional at runtime — loaded via reflection) ──────
    // PDF support: org.apache.pdfbox:pdfbox:3.0.3
    // DOCX support: org.apache.poi:poi-ooxml:5.3.0
    // These are declared compileOnly so they don't bulk up the shadow JAR by default.
    // Users who need PDF/DOCX support should add them to their own classpath, or
    // uncomment the implementation() lines below and rebuild: gradle :generation-engine:shadowJar
    compileOnly("org.apache.pdfbox:pdfbox:3.0.3")
    compileOnly("org.apache.poi:poi-ooxml:5.3.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.qacopilot.generation.cli.LocalPipelineCli"
    }
}

tasks.shadowJar {
    archiveClassifier.set("all")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "com.qacopilot.generation.cli.LocalPipelineCli"
    }
}

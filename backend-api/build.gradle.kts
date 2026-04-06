plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(project(":analysis-core"))
    implementation(project(":generation-engine"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

package com.qacopilot.generation.scaffold;

import com.qacopilot.generation.scenario.FeatureArtifact;
import com.qacopilot.generation.state.FrameworkMeta;
import com.qacopilot.generation.state.QacopilotPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes a standalone Gradle project (Cucumber + Rest Assured) at {@code frameworkRoot}. Java/Gradle
 * sources are only rewritten when missing or when {@link CodegenVersions#STEPS_VERSION} advances, so
 * developers can tweak dependencies unless {@code forceRegenerateAllFiles} is set.
 */
public final class TestFrameworkScaffolder {

    public void ensureLayoutAndFeatures(
            Path frameworkRoot,
            List<FeatureArtifact> features,
            String generatorId,
            boolean forceRegenerateAllFiles
    ) throws IOException {
        Files.createDirectories(QacopilotPaths.dotDir(frameworkRoot));
        FrameworkMeta.Data meta = FrameworkMeta.readOrDefault(QacopilotPaths.metaJson(frameworkRoot));
        boolean gradleMissing = !Files.isRegularFile(frameworkRoot.resolve("build.gradle.kts"));
        boolean needFullWrite =
                forceRegenerateAllFiles
                        || gradleMissing
                        || meta.stepsVersion() < CodegenVersions.STEPS_VERSION;

        if (needFullWrite) {
            writeProjectSkeleton(frameworkRoot);
            FrameworkMeta.write(
                    QacopilotPaths.metaJson(frameworkRoot),
                    new FrameworkMeta.Data(CodegenVersions.STEPS_VERSION, generatorId)
            );
        }

        Path testResources = frameworkRoot.resolve("src/test/resources");
        Files.createDirectories(testResources.resolve("features"));
        for (FeatureArtifact artifact : features) {
            Path target = artifact.resolveUnderTestResources(testResources);
            Files.createDirectories(target.getParent());
            Files.writeString(target, artifact.content());
        }

        removeLegacyMonolithicFeature(testResources);
        DomainStepDefinitionsWriter.writeAll(frameworkRoot, extractDomainKeys(features));

        ensureGradleWrapperPresent(frameworkRoot);
    }

    private static Set<String> extractDomainKeys(List<FeatureArtifact> features) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (FeatureArtifact a : features) {
            String rel = a.relativeToTestResources();
            if (rel.startsWith("features/")) {
                String[] parts = rel.split("/");
                if (parts.length >= 2 && !parts[1].isBlank()) {
                    keys.add(parts[1]);
                }
            }
        }
        return keys;
    }

    private static void removeLegacyMonolithicFeature(Path testResources) throws IOException {
        Files.deleteIfExists(testResources.resolve("features/qacopilot.generated.feature"));
    }

    /**
     * Classpath resources under {@code scaffold-wrapper/} (mirrors a Gradle project root). Emitted whenever
     * {@code gradlew} is missing so generated folders are runnable without a global {@code gradle} install.
     */
    private static final String[][] BUNDLED_GRADLE_WRAPPER = {
            {"scaffold-wrapper/gradlew", "gradlew"},
            {"scaffold-wrapper/gradlew.bat", "gradlew.bat"},
            {
                    "scaffold-wrapper/gradle/wrapper/gradle-wrapper.properties",
                    "gradle/wrapper/gradle-wrapper.properties"
            },
            {
                    // Named .pack so the shadow JAR does not unpack it as a nested dependency JAR.
                    "scaffold-wrapper/gradle/wrapper/gradle-wrapper-jar.pack",
                    "gradle/wrapper/gradle-wrapper.jar"
            },
    };

    private static void ensureGradleWrapperPresent(Path frameworkRoot) throws IOException {
        Path gradlew = frameworkRoot.resolve("gradlew");
        if (Files.isRegularFile(gradlew)) {
            return;
        }
        ClassLoader cl = TestFrameworkScaffolder.class.getClassLoader();
        for (String[] pair : BUNDLED_GRADLE_WRAPPER) {
            try (InputStream in = cl.getResourceAsStream(pair[0])) {
                if (in == null) {
                    throw new IOException("Missing bundled resource: " + pair[0]);
                }
                Path out = frameworkRoot.resolve(pair[1]);
                Files.createDirectories(out.getParent());
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        tryMakeGradleWrapperExecutable(gradlew);
    }

    private static void tryMakeGradleWrapperExecutable(Path gradlew) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(gradlew);
            perms = EnumSet.copyOf(perms);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(gradlew, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows or non-POSIX file systems
        }
    }

    /** @deprecated Use {@link #ensureLayoutAndFeatures} with {@link com.qacopilot.generation.scenario.FeatureSynthesis}. */
    @Deprecated
    public void scaffold(
            Path frameworkRoot,
            List<com.qacopilot.analysis.ir.EndpointContract> endpoints,
            com.qacopilot.generation.scenario.ScenarioGenerator generator
    ) throws Exception {
        var features = generator.generateFeatures(endpoints);
        ensureLayoutAndFeatures(frameworkRoot, features, generator.id(), false);
    }

    private static void writeProjectSkeleton(Path root) throws IOException {
        write(
                root.resolve(".gitignore"),
                """
                        .gradle/
                        build/
                        .idea/
                        *.iml
                        .DS_Store

                        """
        );
        write(
                root.resolve("settings.gradle.kts"),
                """
                        rootProject.name = "test_framework"
                        """
        );
        write(
                root.resolve("build.gradle.kts"),
                """
                        plugins {
                            java
                        }

                        java {
                            toolchain {
                                languageVersion.set(JavaLanguageVersion.of(17))
                            }
                        }

                        repositories {
                            mavenCentral()
                        }

                        dependencies {
                            testImplementation("io.cucumber:cucumber-java:7.18.1")
                            testImplementation("io.cucumber:cucumber-junit-platform-engine:7.18.1")
                            testImplementation("org.junit.platform:junit-platform-suite:1.10.2")
                            testImplementation("io.rest-assured:rest-assured:5.5.0")
                            testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
                        }

                        tasks.test {
                            useJUnitPlatform()
                            systemProperty("qacopilot.dryRun", System.getProperty("qacopilot.dryRun", "false"))
                            systemProperty("api.base.url", System.getProperty("api.base.url", "http://localhost:8080"))
                            val tagFilter = System.getProperty("cucumber.filter.tags", "").trim()
                            if (tagFilter.isNotEmpty()) {
                                systemProperty("cucumber.filter.tags", tagFilter)
                            }
                        }
                        """
        );
        copyScaffoldResource(root, "cucumber.properties", "src/test/resources/cucumber.properties");
        write(
                root.resolve("src/test/java/com/qacopilot/runner/CucumberTestRunner.java"),
                """
                        package com.qacopilot.runner;

                        import org.junit.platform.suite.api.ConfigurationParameter;
                        import org.junit.platform.suite.api.IncludeEngines;
                        import org.junit.platform.suite.api.SelectClasspathResource;
                        import org.junit.platform.suite.api.Suite;

                        @Suite
                        @IncludeEngines("cucumber")
                        @SelectClasspathResource("features")
                        @ConfigurationParameter(key = "cucumber.glue", value = "com.qacopilot.stepdefs")
                        @ConfigurationParameter(
                                key = "cucumber.plugin",
                                value = "pretty,com.qacopilot.support.QaCopilotHtmlReportPlugin:build/reports/qacopilot-api"
                        )
                        public class CucumberTestRunner {
                        }
                        """
        );
        copyScaffoldResource(root, "ApiTestContext.java", "src/test/java/com/qacopilot/support/ApiTestContext.java");
        copyScaffoldResource(root, "RestAssuredClient.java", "src/test/java/com/qacopilot/support/RestAssuredClient.java");
        copyScaffoldResource(root, "QaCopilotHtmlReport.java", "src/test/java/com/qacopilot/support/QaCopilotHtmlReport.java");
        copyScaffoldResource(root, "QaCopilotHtmlReportPlugin.java", "src/test/java/com/qacopilot/support/QaCopilotHtmlReportPlugin.java");
        write(
                root.resolve("src/test/java/com/qacopilot/support/GeneratedPayloadFactory.java"),
                """
                        package com.qacopilot.support;

                        public final class GeneratedPayloadFactory {
                            private GeneratedPayloadFactory() {
                            }

                            public static String payloadFor(String requestType) {
                                if (requestType == null || requestType.isBlank()) {
                                    return "{}";
                                }
                                return switch (requestType) {
                                    default -> "{}";
                                };
                            }
                        }
                        """
        );
        copyScaffoldResource(root, "CommonSteps.java", "src/test/java/com/qacopilot/stepdefs/common/CommonSteps.java");
    }

    private static void copyScaffoldResource(Path root, String resourceFileName, String destRelative) throws IOException {
        ClassLoader cl = TestFrameworkScaffolder.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream("scaffold-support/" + resourceFileName)) {
            if (in == null) {
                throw new IOException("Missing bundled resource: scaffold-support/" + resourceFileName);
            }
            Path out = root.resolve(destRelative);
            Files.createDirectories(out.getParent());
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

package com.qacopilot.generation.scaffold;

import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.generation.scenario.FeatureArtifact;
import com.qacopilot.generation.scenario.FeatureSynthesis;
import com.qacopilot.generation.scenario.TemplateEndpointBlockSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFrameworkScaffolderTest {

    @Test
    void writesStandaloneProject(@TempDir Path tmp) throws Exception {
        List<EndpointContract> endpoints = List.of(EndpointContract.synthetic("GET", "/health", "HealthController", "health"));
        List<FeatureArtifact> features = FeatureSynthesis.buildFresh(endpoints, new TemplateEndpointBlockSource());
        new TestFrameworkScaffolder().ensureLayoutAndFeatures(tmp, features, "template", false);

        assertTrue(Files.exists(tmp.resolve("build.gradle.kts")));
        assertTrue(Files.exists(tmp.resolve("gradlew")));
        assertTrue(Files.exists(tmp.resolve("gradle/wrapper/gradle-wrapper.jar")));
        assertTrue(Files.exists(tmp.resolve("src/test/java/com/qacopilot/runner/CucumberTestRunner.java")));
        String runner = Files.readString(tmp.resolve("src/test/java/com/qacopilot/runner/CucumberTestRunner.java"));
        assertTrue(runner.contains("cucumber.glue"), "runner must set glue so steps are discovered");
        assertTrue(runner.contains("com.qacopilot.stepdefs"));
        String cucumberProps = Files.readString(tmp.resolve("src/test/resources/cucumber.properties"));
        assertTrue(cucumberProps.contains("cucumber.glue=com.qacopilot.stepdefs"));
        String common = Files.readString(tmp.resolve("src/test/java/com/qacopilot/stepdefs/common/CommonSteps.java"));
        assertTrue(common.contains("the endpoint contract is logged for reporting"));
        assertTrue(common.contains("request body is:"));
        assertTrue(Files.exists(tmp.resolve("src/test/resources/features/health/health.feature")));
        String featureText = Files.readString(tmp.resolve("src/test/resources/features/health/health.feature"));
        assertTrue(featureText.contains("/health"));
        assertTrue(featureText.contains("@domain_health"));
        assertTrue(Files.exists(tmp.resolve("src/test/java/com/qacopilot/stepdefs/health/HealthStepDefinitions.java")));
        String gradle = Files.readString(tmp.resolve("build.gradle.kts"));
        assertTrue(gradle.contains("cucumber.filter.tags"), "build must forward tag filter to the test JVM");
    }
}

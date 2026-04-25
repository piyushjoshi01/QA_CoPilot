package com.qacopilot.generation.scaffold;

import com.qacopilot.generation.scenario.DomainClassifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Emits one thin step-definition class per domain under {@code com.qacopilot.stepdefs.<domain>}.
 * Shared REST steps live in {@link com.qacopilot.stepdefs.common.CommonSteps}.
 */
public final class DomainStepDefinitionsWriter {

    private DomainStepDefinitionsWriter() {
    }

    public static void writeAll(Path frameworkRoot, Collection<String> domainKeys) throws IOException {
        for (String domainKey : domainKeys) {
            writeOne(frameworkRoot, domainKey);
        }
    }

    private static void writeOne(Path frameworkRoot, String domainKey) throws IOException {
        String stem = DomainClassifier.domainClassStem(domainKey);
        String className = stem + "StepDefinitions";
        String pkg = "com.qacopilot.stepdefs." + domainKey;
        String pkgPath = pkg.replace('.', '/');
        Path file = frameworkRoot.resolve("src/test/java/" + pkgPath + "/" + className + ".java");
        Files.createDirectories(file.getParent());
        String content =
                """
                package %s;

                import io.cucumber.java.Before;

                /**
                 * Domain hooks for <code>%s</code>. Shared HTTP/Gherkin steps are in
                 * {@link com.qacopilot.stepdefs.common.CommonSteps} (single source of truth — avoids duplicate
                 * step regexes across domains).
                 */
                public final class %s {

                    @Before("@domain_%s")
                    public void markDomainContext() {
                        // Optional: attach domain to reporting, MDC, etc.
                    }
                }
                """
                        .formatted(pkg, domainKey, className, domainKey);
        Files.writeString(file, content);
    }
}

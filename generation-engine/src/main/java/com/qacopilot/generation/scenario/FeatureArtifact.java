package com.qacopilot.generation.scenario;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A feature file to place under the generated project's {@code src/test/resources} directory.
 *
 * @param relativeToTestResources Unix-style path such as {@code features/order/order.feature}
 */
public record FeatureArtifact(String relativeToTestResources, String content) {
    public FeatureArtifact {
        Objects.requireNonNull(relativeToTestResources, "relativeToTestResources");
        Objects.requireNonNull(content, "content");
    }

    public Path resolveUnderTestResources(Path testResourcesDir) {
        Path p = testResourcesDir;
        for (String segment : relativeToTestResources.split("/")) {
            if (!segment.isEmpty()) {
                p = p.resolve(segment);
            }
        }
        return p;
    }
}

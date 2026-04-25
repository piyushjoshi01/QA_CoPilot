package com.qacopilot.generation.state;

import com.qacopilot.analysis.extractor.SpringAnalysisOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectEndpointExtractorTest {

    @Test
    void incrementalParseUsesCacheOnSecondRun(@TempDir Path root, @TempDir Path out) throws Exception {
        Path pkg = root.resolve("src/main/java/demo");
        Files.createDirectories(pkg);
        Path javaFile = pkg.resolve("Api.java");
        Files.writeString(
                javaFile,
                """
                        package demo;
                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.RestController;
                        @RestController
                        class Api {
                            @GetMapping("/cached")
                            String x() { return "1"; }
                        }
                        """
        );
        Path cache = out.resolve(".qacopilot/parse-cache.json");
        Files.createDirectories(cache.getParent());

        SpringAnalysisOptions opts = SpringAnalysisOptions.forTests();
        ProjectEndpointExtractor.Result r1 =
                ProjectEndpointExtractor.extract(root, cache, List.of(), List.of(), opts, true, List.of(), false);
        assertEquals(1, r1.endpoints().size());

        ProjectEndpointExtractor.Result r2 =
                ProjectEndpointExtractor.extract(root, cache, List.of(), List.of(), opts, true, List.of(), false);
        assertEquals(1, r2.endpoints().size());
        assertEquals(r1.endpoints().get(0).getPath(), r2.endpoints().get(0).getPath());

        Files.writeString(
                javaFile,
                """
                        package demo;
                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.RestController;
                        @RestController
                        class Api {
                            @GetMapping("/changed")
                            String x() { return "1"; }
                        }
                        """
        );
        ProjectEndpointExtractor.Result r3 =
                ProjectEndpointExtractor.extract(root, cache, List.of(), List.of(), opts, true, List.of(), false);
        assertEquals("/changed", r3.endpoints().get(0).getPath());
    }
}

package com.qacopilot.generation.openai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DotEnvLoaderTest {

    @Test
    void readsKeyIgnoringCommentsAndExport(@TempDir Path tmp) throws Exception {
        Path env = tmp.resolve(".env");
        Files.writeString(
                env,
                """
                        # comment
                        export OPENAI_API_KEY=sk-test-123
                        OPENAI_MODEL=my-model
                        """
        );
        assertEquals("sk-test-123", DotEnvLoader.parseFile(env, "OPENAI_API_KEY").orElseThrow());
        assertEquals("my-model", DotEnvLoader.parseFile(env, "OPENAI_MODEL").orElseThrow());
    }

    @Test
    void walksUpToFindEnv(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve(".env"), "OPENAI_API_KEY=from-root\n");
        Path nested = Files.createDirectories(tmp.resolve("a/b/c"));
        assertEquals("from-root", DotEnvLoader.loadProperty("OPENAI_API_KEY", nested).orElseThrow());
    }
}

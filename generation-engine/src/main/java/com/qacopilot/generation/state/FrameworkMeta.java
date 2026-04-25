package com.qacopilot.generation.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tracks generated step-definition version so we can patch the test project when steps evolve. */
public final class FrameworkMeta {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(int stepsVersion, String lastGenerator) {
    }

    public static Data readOrDefault(Path metaFile) throws IOException {
        if (!Files.isRegularFile(metaFile)) {
            return new Data(0, "");
        }
        return MAPPER.readValue(Files.readString(metaFile), Data.class);
    }

    public static void write(Path metaFile, Data data) throws IOException {
        Files.createDirectories(metaFile.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(metaFile.toFile(), data);
    }
}

package com.qacopilot.generation.state;

import java.nio.file.Path;

public final class QacopilotPaths {

    private QacopilotPaths() {
    }

    public static Path dotDir(Path testFrameworkRoot) {
        return testFrameworkRoot.resolve(".qacopilot");
    }

    public static Path metaJson(Path testFrameworkRoot) {
        return dotDir(testFrameworkRoot).resolve("meta.json");
    }

    public static Path lastEndpointsJson(Path testFrameworkRoot) {
        return dotDir(testFrameworkRoot).resolve("last-endpoints.json");
    }

    public static Path apiContractJson(Path testFrameworkRoot) {
        return dotDir(testFrameworkRoot).resolve("api-contract.json");
    }

    public static Path parseCacheJson(Path testFrameworkRoot) {
        return dotDir(testFrameworkRoot).resolve("parse-cache.json");
    }
}

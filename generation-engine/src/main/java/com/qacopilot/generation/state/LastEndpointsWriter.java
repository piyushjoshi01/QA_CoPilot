package com.qacopilot.generation.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qacopilot.analysis.ir.EndpointContract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LastEndpointsWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LastEndpointsWriter() {
    }

    /**
     * Writes compact {@code last-endpoints.json} (schema v1) for quick diffs; full contract is in
     * {@link ApiContractWriter}.
     */
    public static void write(Path file, List<EndpointContract> endpoints) throws IOException {
        Files.createDirectories(file.getParent());
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", 1);
        ArrayNode arr = root.putArray("endpoints");
        for (EndpointContract e : endpoints) {
            ObjectNode n = arr.addObject();
            n.put("stableId", EndpointFingerprints.stableId(e));
            n.put("signature", EndpointFingerprints.signature(e));
            n.put("httpMethod", e.getHttpMethod());
            n.put("path", e.getPath());
            n.put("controller", e.getControllerClassName());
            n.put("handler", e.getHandlerMethodName());
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
    }
}

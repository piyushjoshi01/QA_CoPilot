package com.qacopilot.generation.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.qacopilot.analysis.ir.EndpointContract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Loads OpenAPI 3.x JSON/YAML (e.g. springdoc export) and merges operation metadata into
 * {@link EndpointContract}s by HTTP method + path template matching.
 */
public final class OpenApiSpecMerger {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private OpenApiSpecMerger() {
    }

    public static List<Path> discoverSpecs(Path projectRoot) throws IOException {
        List<Path> found = new ArrayList<>();
        String[] rels = {
                "src/main/resources/openapi.yaml",
                "src/main/resources/openapi.yml",
                "src/main/resources/openapi.json",
                "src/main/resources/static/openapi.yaml",
                "src/main/resources/static/openapi.yml",
                "src/main/resources/static/openapi.json",
                "build/openapi.json",
                "target/openapi.json"
        };
        for (String r : rels) {
            Path p = projectRoot.resolve(r);
            if (Files.isRegularFile(p)) {
                found.add(p.toAbsolutePath().normalize());
            }
        }
        return found;
    }

    public static String contentFingerprint(List<Path> specPaths) throws IOException {
        if (specPaths == null || specPaths.isEmpty()) {
            return "";
        }
        List<Path> sorted = new ArrayList<>(specPaths);
        sorted.sort(Comparator.comparing(Path::toString));
        StringBuilder sb = new StringBuilder();
        for (Path p : sorted) {
            sb.append(p).append('\u0001');
            if (Files.isRegularFile(p)) {
                sb.append(sha256Hex(Files.readString(p)));
            }
            sb.append('\u0002');
        }
        return sha256Hex(sb.toString());
    }

    public static List<EndpointContract> merge(List<EndpointContract> endpoints, List<Path> specPaths)
            throws IOException {
        if (specPaths == null || specPaths.isEmpty()) {
            return endpoints;
        }
        List<OpMatch> ops = loadOperations(specPaths);
        if (ops.isEmpty()) {
            return endpoints;
        }
        List<EndpointContract> out = new ArrayList<>();
        for (EndpointContract e : endpoints) {
            Optional<OpMatch> match = findBestMatch(e, ops);
            if (match.isEmpty()) {
                out.add(e);
                continue;
            }
            OpMatch op = match.get();
            LinkedHashSet<Integer> codes = new LinkedHashSet<>(e.getOpenApiResponseCodes());
            codes.addAll(op.responseCodes);
            List<Integer> mergedCodes = new ArrayList<>(codes);
            Collections.sort(mergedCodes);

            List<String> sources = new ArrayList<>(e.getOpenApiSpecSources());
            if (!sources.contains(op.sourceLabel)) {
                sources.add(op.sourceLabel);
            }

            String summary = e.getOpenApiSummary();
            if (summary == null && op.summary != null && !op.summary.isBlank()) {
                summary = op.summary;
            }

            String opId = op.operationId != null && !op.operationId.isBlank() ? op.operationId : e.getOpenApiSpecOperationId();
            String desc =
                    op.description != null && !op.description.isBlank()
                            ? op.description
                            : e.getOpenApiSpecDescription();

            out.add(
                    new EndpointContract(
                            e.getHttpMethod(),
                            e.getPath(),
                            e.getControllerClassName(),
                            e.getControllerFqcn(),
                            e.getHandlerMethodName(),
                            e.getBasePath(),
                            e.getMethodPath(),
                            e.getPathParams(),
                            e.getQueryParams(),
                            e.getRequestBody(),
                            e.getConsumes(),
                            e.getProduces(),
                            e.getAuth(),
                            e.getWarnings(),
                            summary,
                            e.getOpenApiTags(),
                            mergedCodes,
                            opId,
                            desc,
                            sources,
                            e.getSourceFile()
                    )
            );
        }
        return out;
    }

    private record OpMatch(
            String sourceLabel,
            String method,
            String pathTemplate,
            String operationId,
            String summary,
            String description,
            List<Integer> responseCodes
    ) {}

    private static List<OpMatch> loadOperations(List<Path> paths) throws IOException {
        List<OpMatch> all = new ArrayList<>();
        for (Path p : paths) {
            if (!Files.isRegularFile(p)) {
                continue;
            }
            JsonNode root = readSpec(p);
            if (root == null) {
                continue;
            }
            String label = p.getFileName().toString();
            JsonNode pathsNode = root.get("paths");
            if (pathsNode == null || !pathsNode.isObject()) {
                continue;
            }
            ObjectNode pathsObject = (ObjectNode) pathsNode;
            Iterator<Map.Entry<String, JsonNode>> pathIt = pathsObject.fields();
            while (pathIt.hasNext()) {
                Map.Entry<String, JsonNode> pathEntry = pathIt.next();
                String pathKey = pathEntry.getKey();
                JsonNode pathItem = pathEntry.getValue();
                if (!pathItem.isObject()) {
                    continue;
                }
                for (String verb : List.of("get", "post", "put", "patch", "delete", "head", "options")) {
                    JsonNode op = pathItem.get(verb);
                    if (op == null || !op.isObject()) {
                        continue;
                    }
                    String opId = text(op, "operationId");
                    String sum = text(op, "summary");
                    String desc = text(op, "description");
                    List<Integer> rc = responseCodes(op.get("responses"));
                    all.add(
                            new OpMatch(
                                    label,
                                    verb.toUpperCase(Locale.ROOT),
                                    pathKey,
                                    opId,
                                    sum,
                                    desc,
                                    rc
                            )
                    );
                }
            }
        }
        return all;
    }

    private static JsonNode readSpec(Path p) throws IOException {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".json")) {
            return JSON_MAPPER.readTree(p.toFile());
        }
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return YAML_MAPPER.readTree(p.toFile());
        }
        return null;
    }

    private static String text(JsonNode n, String field) {
        if (n == null) {
            return null;
        }
        JsonNode j = n.get(field);
        return j != null && j.isTextual() ? j.asText() : null;
    }

    private static List<Integer> responseCodes(JsonNode responses) {
        List<Integer> out = new ArrayList<>();
        if (responses == null || !responses.isObject()) {
            return out;
        }
        ObjectNode ro = (ObjectNode) responses;
        Iterator<Map.Entry<String, JsonNode>> it = ro.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> ent = it.next();
            String name = ent.getKey();
            if (name.matches("\\d{3}")) {
                try {
                    out.add(Integer.parseInt(name));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    private static Optional<OpMatch> findBestMatch(EndpointContract e, List<OpMatch> ops) {
        String method = e.getHttpMethod().toUpperCase(Locale.ROOT);
        String springPath = normalizePath(e.getPath());
        OpMatch exact = null;
        OpMatch template = null;
        for (OpMatch o : ops) {
            if (!method.equals(o.method)) {
                continue;
            }
            String oPath = normalizePath(o.pathTemplate);
            if (springPath.equals(oPath)) {
                exact = o;
                break;
            }
            if (pathsTemplateCompatible(springPath, oPath)) {
                template = o;
            }
        }
        return Optional.ofNullable(exact != null ? exact : template);
    }

    private static String normalizePath(String p) {
        if (p == null || p.isBlank()) {
            return "/";
        }
        String x = p.trim();
        if (!x.startsWith("/")) {
            x = "/" + x;
        }
        while (x.length() > 1 && x.endsWith("/")) {
            x = x.substring(0, x.length() - 1);
        }
        return x;
    }

    private static boolean pathsTemplateCompatible(String a, String b) {
        String[] as = normalizePath(a).split("/");
        String[] bs = normalizePath(b).split("/");
        if (as.length != bs.length) {
            return false;
        }
        for (int i = 0; i < as.length; i++) {
            if (as[i].equals(bs[i])) {
                continue;
            }
            if (isParamSegment(as[i]) || isParamSegment(bs[i])) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean isParamSegment(String seg) {
        return seg.length() > 2 && seg.startsWith("{") && seg.endsWith("}");
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

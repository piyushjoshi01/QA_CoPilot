package com.qacopilot.generation.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qacopilot.analysis.extractor.SecurityScanResult;
import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.analysis.ir.FieldContract;
import com.qacopilot.analysis.ir.ParamBinding;
import com.qacopilot.analysis.ir.RequestBodyContract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

/** Writes {@code .qacopilot/api-contract.json} for AI and tooling. */
public final class ApiContractWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ApiContractWriter() {
    }

    public static void write(Path file, String projectName, List<EndpointContract> endpoints) throws IOException {
        write(file, projectName, endpoints, SecurityScanResult.empty());
    }

    public static void write(
            Path file,
            String projectName,
            List<EndpointContract> endpoints,
            SecurityScanResult security
    ) throws IOException {
        Files.createDirectories(file.getParent());
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", 4);
        ObjectNode project = root.putObject("project");
        project.put("name", projectName == null || projectName.isBlank() ? "unknown" : projectName);

        if (security != null && !security.isEmpty()) {
            ObjectNode sec = root.putObject("securityFilterChain");
            ArrayNode pats = sec.putArray("patterns");
            security.getPatterns().forEach(pats::add);
            ArrayNode matchers = sec.putArray("requestMatcherLiterals");
            security.getRequestMatcherLiterals().forEach(matchers::add);
            ArrayNode src = sec.putArray("sourceFiles");
            security.getSourceFiles().forEach(src::add);
        }

        ArrayNode arr = root.putArray("endpoints");
        for (EndpointContract e : endpoints) {
            arr.add(endpointNode(e));
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
    }

    private static ObjectNode endpointNode(EndpointContract e) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("stableId", EndpointFingerprints.stableId(e));
        n.put("signature", EndpointFingerprints.signature(e));
        n.put("httpMethod", e.getHttpMethod());
        n.put("path", e.getPath());
        n.put("basePath", e.getBasePath().isEmpty() ? "" : "/" + e.getBasePath());
        n.put("methodPath", e.getMethodPath().isEmpty() ? "" : "/" + e.getMethodPath());
        n.put("controller", e.getControllerClassName());
        n.put("controllerFqcn", e.getControllerFqcn());
        n.put("handler", e.getHandlerMethodName());
        if (e.getSourceFile() != null) {
            n.put("sourceFile", e.getSourceFile());
        }

        ObjectNode auth = n.putObject("auth");
        auth.put("type", e.getAuth().getType());
        auth.put("required", e.getAuth().isRequired());

        ObjectNode request = n.putObject("request");
        request.set("pathParams", paramsArray(e.getPathParams()));
        request.set("queryParams", paramsArray(e.getQueryParams()));
        request.set("body", bodyNode(e.getRequestBody()));

        ArrayNode consumes = MAPPER.createArrayNode();
        e.getConsumes().forEach(consumes::add);
        request.set("consumes", consumes);
        ArrayNode produces = MAPPER.createArrayNode();
        e.getProduces().forEach(produces::add);
        n.set("produces", produces);

        boolean hasOpenApi =
                e.getOpenApiSummary() != null
                        || !e.getOpenApiTags().isEmpty()
                        || !e.getOpenApiResponseCodes().isEmpty()
                        || e.getOpenApiSpecOperationId() != null
                        || e.getOpenApiSpecDescription() != null
                        || !e.getOpenApiSpecSources().isEmpty();
        if (hasOpenApi) {
            ObjectNode oa = n.putObject("openApi");
            if (e.getOpenApiSummary() != null) {
                oa.put("summary", e.getOpenApiSummary());
            }
            ArrayNode t = oa.putArray("tags");
            e.getOpenApiTags().forEach(t::add);
            ArrayNode rc = oa.putArray("responseCodes");
            e.getOpenApiResponseCodes().stream().sorted().distinct().forEach(rc::add);
            if (e.getOpenApiSpecOperationId() != null) {
                oa.put("operationId", e.getOpenApiSpecOperationId());
            }
            if (e.getOpenApiSpecDescription() != null) {
                oa.put("descriptionFromSpec", e.getOpenApiSpecDescription());
            }
            ArrayNode specSrc = oa.putArray("mergedSpecSources");
            e.getOpenApiSpecSources().forEach(specSrc::add);
        }

        ObjectNode response = n.putObject("response");
        response.set("statusCodes", mergedStatusCodes(e));
        response.putArray("variants");

        ArrayNode warnings = MAPPER.createArrayNode();
        e.getWarnings().forEach(warnings::add);
        if (!e.getWarnings().isEmpty()) {
            n.set("warnings", warnings);
        }

        return n;
    }

    private static ArrayNode mergedStatusCodes(EndpointContract e) {
        LinkedHashSet<Integer> codes = new LinkedHashSet<>();
        for (int c : defaultStatusCodeList(e)) {
            codes.add(c);
        }
        e.getOpenApiResponseCodes().stream().sorted().forEach(codes::add);
        ArrayNode a = MAPPER.createArrayNode();
        codes.stream().sorted().forEach(a::add);
        return a;
    }

    private static List<Integer> defaultStatusCodeList(EndpointContract e) {
        String m = e.getHttpMethod();
        if ("POST".equals(m)) {
            return List.of(201, 400);
        }
        if ("DELETE".equals(m)) {
            return List.of(204, 404);
        }
        return List.of(200, 400);
    }

    private static ObjectNode bodyNode(RequestBodyContract b) {
        ObjectNode o = MAPPER.createObjectNode();
        o.put("present", b.isPresent());
        o.put("required", b.isRequired());
        if (b.getDtoJavaType() != null) {
            o.put("dtoJavaType", b.getDtoJavaType());
        }
        ArrayNode fields = o.putArray("fields");
        for (FieldContract f : b.getFields()) {
            ObjectNode fn = fields.addObject();
            fn.put("name", f.getName());
            fn.put("javaType", f.getJavaType());
            fn.put("required", f.isRequired());
            ArrayNode v = fn.putArray("validations");
            f.getValidations().forEach(v::add);
        }
        return o;
    }

    private static ArrayNode paramsArray(List<ParamBinding> params) {
        ArrayNode a = MAPPER.createArrayNode();
        for (ParamBinding p : params) {
            ObjectNode o = a.addObject();
            o.put("name", p.getName());
            o.put("javaType", p.getJavaType());
            o.put("required", p.isRequired());
            if (p.getDefaultValue() != null && !p.getDefaultValue().isEmpty()) {
                o.put("defaultValue", p.getDefaultValue());
            }
        }
        return a;
    }
}

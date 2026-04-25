package com.qacopilot.generation.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qacopilot.analysis.ir.AuthContract;
import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.analysis.ir.FieldContract;
import com.qacopilot.analysis.ir.ParamBinding;
import com.qacopilot.analysis.ir.RequestBodyContract;

import java.util.ArrayList;
import java.util.List;

/** JSON round-trip for {@link EndpointContract} (parse cache + API contract). */
public final class EndpointContractSerde {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EndpointContractSerde() {
    }

    public static ObjectNode toObjectNode(EndpointContract e) {
        ObjectNode n = MAPPER.createObjectNode();
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
        request.set("pathParams", paramsToJson(e.getPathParams()));
        request.set("queryParams", paramsToJson(e.getQueryParams()));
        request.set("body", bodyToJson(e.getRequestBody()));
        ArrayNode consumes = MAPPER.createArrayNode();
        e.getConsumes().forEach(consumes::add);
        request.set("consumes", consumes);
        ArrayNode produces = MAPPER.createArrayNode();
        e.getProduces().forEach(produces::add);
        n.set("produces", produces);
        ArrayNode warnings = MAPPER.createArrayNode();
        e.getWarnings().forEach(warnings::add);
        n.set("warnings", warnings);
        if (e.getOpenApiSummary() != null) {
            n.put("openApiSummary", e.getOpenApiSummary());
        }
        ArrayNode tags = MAPPER.createArrayNode();
        e.getOpenApiTags().forEach(tags::add);
        n.set("openApiTags", tags);
        ArrayNode codes = MAPPER.createArrayNode();
        e.getOpenApiResponseCodes().forEach(codes::add);
        n.set("openApiResponseCodes", codes);
        if (e.getOpenApiSpecOperationId() != null) {
            n.put("openApiSpecOperationId", e.getOpenApiSpecOperationId());
        }
        if (e.getOpenApiSpecDescription() != null) {
            n.put("openApiSpecDescription", e.getOpenApiSpecDescription());
        }
        ArrayNode specSrc = MAPPER.createArrayNode();
        e.getOpenApiSpecSources().forEach(specSrc::add);
        n.set("openApiSpecSources", specSrc);
        return n;
    }

    public static EndpointContract fromObjectNode(ObjectNode n) {
        String httpMethod = text(n, "httpMethod");
        String path = text(n, "path");
        String controller = text(n, "controller");
        String fqcn = textOr(n, "controllerFqcn", controller);
        String handler = text(n, "handler");
        String basePath = stripLeadingSlash(textOr(n, "basePath", ""));
        String methodPath = stripLeadingSlash(textOr(n, "methodPath", ""));
        String sourceFile = n.hasNonNull("sourceFile") ? n.get("sourceFile").asText() : null;

        ObjectNode authN = (ObjectNode) n.get("auth");
        AuthContract auth =
                authN == null
                        ? AuthContract.none()
                        : new AuthContract(authN.path("type").asText("none"), authN.path("required").asBoolean(false));

        ObjectNode req = (ObjectNode) n.get("request");
        List<ParamBinding> pathParams = req == null ? List.of() : paramsFromJson(req.get("pathParams"));
        List<ParamBinding> queryParams = req == null ? List.of() : paramsFromJson(req.get("queryParams"));
        RequestBodyContract body = req == null ? RequestBodyContract.absent() : bodyFromJson(req.get("body"));
        List<String> consumes = req == null ? List.of() : stringArray(req.get("consumes"));

        List<String> produces = stringArray(n.get("produces"));
        List<String> warnings = stringArray(n.get("warnings"));

        String openApiSummary = n.hasNonNull("openApiSummary") ? n.get("openApiSummary").asText() : null;
        List<String> openApiTags = stringArray(n.get("openApiTags"));
        List<Integer> openApiCodes = intArray(n.get("openApiResponseCodes"));
        String openApiSpecOpId = n.hasNonNull("openApiSpecOperationId") ? n.get("openApiSpecOperationId").asText() : null;
        String openApiSpecDesc = n.hasNonNull("openApiSpecDescription") ? n.get("openApiSpecDescription").asText() : null;
        List<String> openApiSpecSources = stringArray(n.get("openApiSpecSources"));

        return new EndpointContract(
                httpMethod,
                path,
                controller,
                fqcn,
                handler,
                basePath,
                methodPath,
                pathParams,
                queryParams,
                body,
                consumes,
                produces,
                auth,
                warnings,
                openApiSummary,
                openApiTags,
                openApiCodes,
                openApiSpecOpId,
                openApiSpecDesc,
                openApiSpecSources,
                sourceFile
        );
    }

    private static String text(ObjectNode n, String k) {
        return n.path(k).asText("");
    }

    private static String textOr(ObjectNode n, String k, String def) {
        JsonNode j = n.get(k);
        return j == null || j.isNull() ? def : j.asText(def);
    }

    private static String stripLeadingSlash(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.startsWith("/") ? s.substring(1) : s;
    }

    private static ArrayNode paramsToJson(List<ParamBinding> params) {
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

    private static List<ParamBinding> paramsFromJson(JsonNode node) {
        List<ParamBinding> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode x : node) {
            if (!x.isObject()) {
                continue;
            }
            ObjectNode o = (ObjectNode) x;
            String def = o.has("defaultValue") ? o.get("defaultValue").asText("") : "";
            out.add(
                    new ParamBinding(
                            o.path("name").asText(""),
                            o.path("javaType").asText(""),
                            o.path("required").asBoolean(true),
                            def
                    )
            );
        }
        return out;
    }

    private static ObjectNode bodyToJson(RequestBodyContract b) {
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

    private static RequestBodyContract bodyFromJson(JsonNode node) {
        if (node == null || !node.isObject()) {
            return RequestBodyContract.absent();
        }
        ObjectNode o = (ObjectNode) node;
        if (!o.path("present").asBoolean(false)) {
            return RequestBodyContract.absent();
        }
        String dto = o.has("dtoJavaType") ? o.get("dtoJavaType").asText() : "java.lang.Object";
        boolean required = o.path("required").asBoolean(true);
        List<FieldContract> fields = new ArrayList<>();
        JsonNode fa = o.get("fields");
        if (fa != null && fa.isArray()) {
            for (JsonNode x : fa) {
                if (!x.isObject()) {
                    continue;
                }
                ObjectNode fo = (ObjectNode) x;
                List<String> vals = stringArray(fo.get("validations"));
                fields.add(
                        new FieldContract(
                                fo.path("name").asText(""),
                                fo.path("javaType").asText(""),
                                fo.path("required").asBoolean(false),
                                vals
                        )
                );
            }
        }
        return RequestBodyContract.of(dto, required, fields);
    }

    private static List<String> stringArray(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode x : node) {
            out.add(x.asText());
        }
        return out;
    }

    private static List<Integer> intArray(JsonNode node) {
        List<Integer> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode x : node) {
            if (x.isInt()) {
                out.add(x.intValue());
            } else {
                try {
                    out.add(Integer.parseInt(x.asText()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }
}

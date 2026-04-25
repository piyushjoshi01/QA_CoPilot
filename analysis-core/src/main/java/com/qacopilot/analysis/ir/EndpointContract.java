package com.qacopilot.analysis.ir;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Structured API endpoint extracted from Spring MVC annotations. Serves as the single IR for
 * fingerprinting, JSON contract export, and scenario generation.
 */
public final class EndpointContract {
    private final String httpMethod;
    private final String path;
    private final String controllerClassName;
    private final String controllerFqcn;
    private final String handlerMethodName;
    private final String basePath;
    private final String methodPath;
    private final List<ParamBinding> pathParams;
    private final List<ParamBinding> queryParams;
    private final RequestBodyContract requestBody;
    private final List<String> consumes;
    private final List<String> produces;
    private final AuthContract auth;
    private final List<String> warnings;
    private final String openApiSummary;
    private final List<String> openApiTags;
    private final List<Integer> openApiResponseCodes;
    /** From merged OpenAPI documents (springdoc export, etc.). */
    private final String openApiSpecOperationId;
    private final String openApiSpecDescription;
    private final List<String> openApiSpecSources;
    /** Relative path under the scanned project, or null if unknown. */
    private final String sourceFile;

    public EndpointContract(
            String httpMethod,
            String path,
            String controllerClassName,
            String controllerFqcn,
            String handlerMethodName,
            String basePath,
            String methodPath,
            List<ParamBinding> pathParams,
            List<ParamBinding> queryParams,
            RequestBodyContract requestBody,
            List<String> consumes,
            List<String> produces,
            AuthContract auth,
            List<String> warnings,
            String openApiSummary,
            List<String> openApiTags,
            List<Integer> openApiResponseCodes,
            String openApiSpecOperationId,
            String openApiSpecDescription,
            List<String> openApiSpecSources,
            String sourceFile
    ) {
        this.httpMethod = Objects.requireNonNull(httpMethod, "httpMethod");
        this.path = Objects.requireNonNull(path, "path");
        this.controllerClassName = Objects.requireNonNull(controllerClassName, "controllerClassName");
        this.controllerFqcn = Objects.requireNonNull(controllerFqcn, "controllerFqcn");
        this.handlerMethodName = Objects.requireNonNull(handlerMethodName, "handlerMethodName");
        this.basePath = basePath == null ? "" : basePath;
        this.methodPath = methodPath == null ? "" : methodPath;
        this.pathParams = pathParams == null ? List.of() : List.copyOf(pathParams);
        this.queryParams = queryParams == null ? List.of() : List.copyOf(queryParams);
        this.requestBody = requestBody == null ? RequestBodyContract.absent() : requestBody;
        this.consumes = consumes == null ? List.of() : List.copyOf(consumes);
        this.produces = produces == null ? List.of() : List.copyOf(produces);
        this.auth = auth == null ? AuthContract.none() : auth;
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
        this.openApiSummary = openApiSummary;
        this.openApiTags = openApiTags == null ? List.of() : List.copyOf(openApiTags);
        this.openApiResponseCodes = openApiResponseCodes == null ? List.of() : List.copyOf(openApiResponseCodes);
        this.openApiSpecOperationId = openApiSpecOperationId;
        this.openApiSpecDescription = openApiSpecDescription;
        this.openApiSpecSources = openApiSpecSources == null ? List.of() : List.copyOf(openApiSpecSources);
        this.sourceFile = sourceFile;
    }

    /**
     * Minimal contract for tests and synthetic endpoints (no parameters, no body).
     */
    public static EndpointContract synthetic(String httpMethod, String path, String controllerClassName, String handlerMethodName) {
        String trimmed = path == null ? "/" : path.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return new EndpointContract(
                httpMethod,
                trimmed,
                controllerClassName,
                controllerClassName,
                handlerMethodName,
                "",
                trimSlashes(trimmed.replaceFirst("^/", "")),
                List.of(),
                List.of(),
                RequestBodyContract.absent(),
                List.of(),
                List.of(),
                AuthContract.none(),
                List.of(),
                null,
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                null
        );
    }

    private static String trimSlashes(String path) {
        String normalized = path == null ? "" : path.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getPath() {
        return path;
    }

    public String getControllerClassName() {
        return controllerClassName;
    }

    public String getControllerFqcn() {
        return controllerFqcn;
    }

    public String getHandlerMethodName() {
        return handlerMethodName;
    }

    public String getBasePath() {
        return basePath;
    }

    public String getMethodPath() {
        return methodPath;
    }

    public List<ParamBinding> getPathParams() {
        return Collections.unmodifiableList(pathParams);
    }

    public List<ParamBinding> getQueryParams() {
        return Collections.unmodifiableList(queryParams);
    }

    public RequestBodyContract getRequestBody() {
        return requestBody;
    }

    public List<String> getConsumes() {
        return Collections.unmodifiableList(consumes);
    }

    public List<String> getProduces() {
        return Collections.unmodifiableList(produces);
    }

    public AuthContract getAuth() {
        return auth;
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public String getOpenApiSummary() {
        return openApiSummary;
    }

    public List<String> getOpenApiTags() {
        return Collections.unmodifiableList(openApiTags);
    }

    public List<Integer> getOpenApiResponseCodes() {
        return Collections.unmodifiableList(openApiResponseCodes);
    }

    public String getOpenApiSpecOperationId() {
        return openApiSpecOperationId;
    }

    public String getOpenApiSpecDescription() {
        return openApiSpecDescription;
    }

    public List<String> getOpenApiSpecSources() {
        return Collections.unmodifiableList(openApiSpecSources);
    }

    public String getSourceFile() {
        return sourceFile;
    }
}

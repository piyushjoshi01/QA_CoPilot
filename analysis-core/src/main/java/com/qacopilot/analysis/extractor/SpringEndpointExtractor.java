package com.qacopilot.analysis.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.qacopilot.analysis.ir.AuthContract;
import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.analysis.ir.ParamBinding;
import com.qacopilot.analysis.ir.RequestBodyContract;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class SpringEndpointExtractor {

    public List<EndpointContract> extractFromProject(
            Path projectRoot,
            List<String> includePatterns,
            List<String> excludePatterns
    ) throws IOException {
        return extractFromProject(projectRoot, includePatterns, excludePatterns, SpringAnalysisOptions.defaults());
    }

    public List<EndpointContract> extractFromProject(
            Path projectRoot,
            List<String> includePatterns,
            List<String> excludePatterns,
            SpringAnalysisOptions options
    ) throws IOException {
        List<PathMatcher> includes = compileMatchers(includePatterns);
        List<PathMatcher> excludes = compileMatchers(excludePatterns);
        JavaParser parser = AnalysisParserFactory.javaParser(projectRoot, options);
        List<EndpointContract> endpoints = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(path) || !path.toString().endsWith(".java")) {
                    continue;
                }
                Path relative = projectRoot.relativize(path);
                if (!matches(relative, includes, excludes)) {
                    continue;
                }
                try {
                    endpoints.addAll(extractFromFile(path, projectRoot, parser, options));
                } catch (Exception ex) {
                    System.err.println("QA CoPilot: skip (parse error) " + path + ": " + ex.getMessage());
                }
            }
        }
        endpoints.sort(
                Comparator.comparing(EndpointContract::getPath)
                        .thenComparing(EndpointContract::getHttpMethod)
                        .thenComparing(EndpointContract::getHandlerMethodName)
        );
        return endpoints;
    }

    public List<EndpointContract> extractFromFile(Path javaFile) throws IOException {
        return extractFromFile(javaFile, null, testParser(), SpringAnalysisOptions.forTests());
    }

    public List<EndpointContract> extractFromFile(
            Path javaFile,
            Path projectRoot,
            JavaParser parser,
            SpringAnalysisOptions options
    ) throws IOException {
        String rel = projectRoot == null ? null : projectRoot.relativize(javaFile).toString().replace('\\', '/');
        var pr = parser.parse(javaFile);
        CompilationUnit cu = pr.getResult().orElseThrow(() -> new IOException("Parse failed: " + javaFile));
        return extractFromCompilationUnit(cu, rel, options);
    }

    public List<EndpointContract> extractFromSource(String javaSource) {
        return extractFromSource(javaSource, null);
    }

    public List<EndpointContract> extractFromSource(String javaSource, String sourceFileRelative) {
        try {
            var pr = testParser().parse(javaSource);
            CompilationUnit cu = pr.getResult().orElseThrow(() -> new IllegalStateException("parse failed"));
            return extractFromCompilationUnit(cu, sourceFileRelative, SpringAnalysisOptions.forTests());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public List<EndpointContract> extractFromCompilationUnit(
            CompilationUnit compilationUnit,
            String sourceFileRelative,
            SpringAnalysisOptions options
    ) {
        List<EndpointContract> endpoints = new ArrayList<>();

        String pkg = compilationUnit.getPackageDeclaration()
                .map(p -> p.getNameAsString())
                .orElse("");

        for (ClassOrInterfaceDeclaration declaration : compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
            if (!hasAnnotation(declaration.getAnnotations(), "RestController")) {
                continue;
            }

            String basePath = extractRequestMappingPath(declaration.getAnnotations()).orElse("");
            String fqcn = pkg.isEmpty() ? declaration.getNameAsString() : pkg + "." + declaration.getNameAsString();

            for (MethodDeclaration methodDeclaration : declaration.getMethods()) {
                endpoints.addAll(
                        toEndpoints(declaration, methodDeclaration, basePath, fqcn, sourceFileRelative, options)
                );
            }
        }

        return endpoints;
    }

    private static JavaParser testParser() {
        ParserConfiguration cfg = new ParserConfiguration();
        cfg.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        return new JavaParser(cfg);
    }

    private List<EndpointContract> toEndpoints(
            ClassOrInterfaceDeclaration declaration,
            MethodDeclaration methodDeclaration,
            String basePath,
            String controllerFqcn,
            String sourceFileRelative,
            SpringAnalysisOptions options
    ) {
        List<EndpointContract> out = new ArrayList<>();
        String simpleName = declaration.getNameAsString();
        String handler = methodDeclaration.getNameAsString();
        SpringdocHints springdoc = SpringdocAnnotationParser.parse(methodDeclaration, declaration);

        for (AnnotationExpr annotation : methodDeclaration.getAnnotations()) {
            Optional<MappingAttributes> attrs = parseMappingAttributes(annotation);
            if (attrs.isEmpty()) {
                continue;
            }
            MappingAttributes ma = attrs.get();
            List<ParamBinding> pathBindings = pathVariableBindings(methodDeclaration);
            List<ParamBinding> queryBindings = queryParamBindings(methodDeclaration);
            RequestBodyContract body = requestBodyBinding(methodDeclaration);
            body = DtoFieldEnricher.enrich(body, methodDeclaration, options);
            AuthContract auth = detectAuth(methodDeclaration, declaration);

            for (String verb : ma.httpMethods()) {
                String resolvedPath = normalizePath(basePath, ma.path());
                List<String> warnings = new ArrayList<>();
                warnPathTemplateMismatch(resolvedPath, pathBindings, warnings);

                out.add(
                        new EndpointContract(
                                verb,
                                resolvedPath,
                                simpleName,
                                controllerFqcn,
                                handler,
                                trimSlashes(basePath),
                                trimSlashes(ma.path()),
                                sortBindings(pathBindings),
                                sortBindings(queryBindings),
                                body,
                                ma.consumes(),
                                ma.produces(),
                                auth,
                                warnings,
                                springdoc.getSummary(),
                                springdoc.getTags(),
                                springdoc.getResponseCodes(),
                                null,
                                null,
                                List.of(),
                                sourceFileRelative
                        )
                );
            }
        }
        return out;
    }

    private static void warnPathTemplateMismatch(String path, List<ParamBinding> pathBindings, List<String> warnings) {
        Set<String> inPath = pathVariableNamesInTemplate(path);
        Set<String> annotated = new LinkedHashSet<>();
        for (ParamBinding b : pathBindings) {
            annotated.add(b.getName());
        }
        for (String name : inPath) {
            if (!annotated.contains(name)) {
                warnings.add("Path template contains {" + name + "} but no matching @PathVariable on method.");
            }
        }
        for (String name : annotated) {
            if (!inPath.contains(name)) {
                warnings.add("@PathVariable \"" + name + "\" does not appear as {" + name + "} in path template.");
            }
        }
    }

    private static Set<String> pathVariableNamesInTemplate(String path) {
        Set<String> names = new LinkedHashSet<>();
        int i = 0;
        while (i < path.length()) {
            int open = path.indexOf('{', i);
            if (open < 0) {
                break;
            }
            int close = path.indexOf('}', open + 1);
            if (close < 0) {
                break;
            }
            String raw = path.substring(open + 1, close).trim();
            int colon = raw.indexOf(':');
            String name = colon >= 0 ? raw.substring(0, colon).trim() : raw;
            if (!name.isEmpty()) {
                names.add(name);
            }
            i = close + 1;
        }
        return names;
    }

    private static List<ParamBinding> sortBindings(List<ParamBinding> bindings) {
        List<ParamBinding> copy = new ArrayList<>(bindings);
        copy.sort(Comparator.comparing(ParamBinding::getName));
        return copy;
    }

    private Optional<MappingAttributes> parseMappingAttributes(AnnotationExpr annotation) {
        String name = annotation.getNameAsString();
        return switch (name) {
            case "GetMapping" -> Optional.of(
                    new MappingAttributes(List.of("GET"), extractAnnotationPath(annotation).orElse(""), mediaList(annotation, "consumes"), mediaList(annotation, "produces"))
            );
            case "PostMapping" -> Optional.of(
                    new MappingAttributes(List.of("POST"), extractAnnotationPath(annotation).orElse(""), mediaList(annotation, "consumes"), mediaList(annotation, "produces"))
            );
            case "PutMapping" -> Optional.of(
                    new MappingAttributes(List.of("PUT"), extractAnnotationPath(annotation).orElse(""), mediaList(annotation, "consumes"), mediaList(annotation, "produces"))
            );
            case "DeleteMapping" -> Optional.of(
                    new MappingAttributes(List.of("DELETE"), extractAnnotationPath(annotation).orElse(""), mediaList(annotation, "consumes"), mediaList(annotation, "produces"))
            );
            case "PatchMapping" -> Optional.of(
                    new MappingAttributes(List.of("PATCH"), extractAnnotationPath(annotation).orElse(""), mediaList(annotation, "consumes"), mediaList(annotation, "produces"))
            );
            case "RequestMapping" -> Optional.of(parseRequestMapping(annotation));
            default -> Optional.empty();
        };
    }

    private MappingAttributes parseRequestMapping(AnnotationExpr annotation) {
        List<String> methods = List.of("GET");
        String path = "";
        List<String> consumes = List.of();
        List<String> produces = List.of();
        boolean explicitMethod = false;

        if (annotation.isSingleMemberAnnotationExpr()) {
            path = extractAnnotationPath(annotation).orElse("");
        } else if (annotation.isNormalAnnotationExpr()) {
            NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
            Optional<String> p = readPathFromPairs(normal.getPairs(), "path");
            path = p.orElseGet(() -> readPathFromPairs(normal.getPairs(), "value").orElse(""));
            Optional<Expression> methodExpr = readPairValue(normal.getPairs(), "method");
            if (methodExpr.isPresent()) {
                explicitMethod = true;
                methods = parseRequestMethodExpression(methodExpr.get());
            }
            consumes = mediaList(normal, "consumes");
            produces = mediaList(normal, "produces");
        }

        if (!explicitMethod) {
            methods = List.of("GET");
        }
        return new MappingAttributes(methods, path, consumes, produces);
    }

    private static List<String> parseRequestMethodExpression(Expression expression) {
        List<String> out = new ArrayList<>();
        if (expression.isNameExpr()) {
            out.add(mapRequestMethodName(expression.asNameExpr().getNameAsString()));
        } else if (expression.isFieldAccessExpr()) {
            out.add(mapRequestMethodName(expression.asFieldAccessExpr().getNameAsString()));
        } else if (expression.isArrayInitializerExpr()) {
            ArrayInitializerExpr arr = expression.asArrayInitializerExpr();
            for (Expression e : arr.getValues()) {
                if (e.isFieldAccessExpr()) {
                    out.add(mapRequestMethodName(e.asFieldAccessExpr().getNameAsString()));
                } else if (e.isNameExpr()) {
                    out.add(mapRequestMethodName(e.asNameExpr().getNameAsString()));
                }
            }
        }
        if (out.isEmpty()) {
            return List.of("GET");
        }
        return List.copyOf(out);
    }

    private static String mapRequestMethodName(String enumConstant) {
        return switch (enumConstant) {
            case "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE" -> enumConstant;
            default -> enumConstant.toUpperCase(Locale.ROOT);
        };
    }

    private static Optional<Expression> readPairValue(NodeList<MemberValuePair> pairs, String key) {
        for (MemberValuePair pair : pairs) {
            if (key.equals(pair.getNameAsString())) {
                return Optional.of(pair.getValue());
            }
        }
        return Optional.empty();
    }

    private List<String> mediaList(AnnotationExpr annotation, String key) {
        if (!annotation.isNormalAnnotationExpr()) {
            return List.of();
        }
        return mediaList(annotation.asNormalAnnotationExpr(), key);
    }

    private static List<String> mediaList(NormalAnnotationExpr normal, String key) {
        Optional<Expression> expr = readPairValue(normal.getPairs(), key);
        if (expr.isEmpty()) {
            return List.of();
        }
        Expression e = expr.get();
        List<String> out = new ArrayList<>();
        if (e.isStringLiteralExpr()) {
            out.add(e.asStringLiteralExpr().asString());
        } else if (e.isArrayInitializerExpr()) {
            for (Expression v : e.asArrayInitializerExpr().getValues()) {
                toStringValue(v).ifPresent(out::add);
            }
        }
        return List.copyOf(out);
    }

    private List<ParamBinding> pathVariableBindings(MethodDeclaration method) {
        List<ParamBinding> out = new ArrayList<>();
        for (Parameter p : method.getParameters()) {
            for (AnnotationExpr ann : p.getAnnotations()) {
                if ("PathVariable".equals(ann.getNameAsString())) {
                    String paramName = parameterName(ann, p);
                    boolean required = readBooleanMember(ann, "required", true);
                    out.add(new ParamBinding(paramName, p.getType().asString(), required, ""));
                }
            }
        }
        return out;
    }

    private List<ParamBinding> queryParamBindings(MethodDeclaration method) {
        List<ParamBinding> out = new ArrayList<>();
        for (Parameter p : method.getParameters()) {
            for (AnnotationExpr ann : p.getAnnotations()) {
                if ("RequestParam".equals(ann.getNameAsString())) {
                    String paramName = parameterName(ann, p);
                    boolean required = readBooleanMember(ann, "required", true);
                    String def = readStringMember(ann, "defaultValue").orElse("");
                    if ("\\".equals(def) || def.isEmpty()) {
                        def = "";
                    }
                    out.add(new ParamBinding(paramName, p.getType().asString(), required, def));
                }
            }
        }
        return out;
    }

    private RequestBodyContract requestBodyBinding(MethodDeclaration method) {
        for (Parameter p : method.getParameters()) {
            for (AnnotationExpr ann : p.getAnnotations()) {
                if ("RequestBody".equals(ann.getNameAsString())) {
                    boolean required = readBooleanMember(ann, "required", true);
                    return RequestBodyContract.of(p.getType().asString(), required, List.of());
                }
            }
        }
        return RequestBodyContract.absent();
    }

    private static AuthContract detectAuth(MethodDeclaration method, ClassOrInterfaceDeclaration controller) {
        for (AnnotationExpr ann : method.getAnnotations()) {
            Optional<AuthContract> a = authFromAnnotation(ann);
            if (a.isPresent()) {
                return a.get();
            }
        }
        for (AnnotationExpr ann : controller.getAnnotations()) {
            Optional<AuthContract> a = authFromAnnotation(ann);
            if (a.isPresent()) {
                return a.get();
            }
        }
        return AuthContract.none();
    }

    private static Optional<AuthContract> authFromAnnotation(AnnotationExpr ann) {
        String n = ann.getNameAsString();
        if ("PreAuthorize".equals(n) || "Secured".equals(n) || "RolesAllowed".equals(n)) {
            return Optional.of(new AuthContract("methodSecurity", true));
        }
        return Optional.empty();
    }

    private static String parameterName(AnnotationExpr ann, Parameter p) {
        Optional<String> v = readStringMember(ann, "value");
        if (v.isPresent() && !v.get().isEmpty()) {
            return v.get();
        }
        Optional<String> n = readStringMember(ann, "name");
        if (n.isPresent() && !n.get().isEmpty()) {
            return n.get();
        }
        return p.getNameAsString();
    }

    private static boolean readBooleanMember(AnnotationExpr ann, String key, boolean defaultValue) {
        if (!ann.isNormalAnnotationExpr()) {
            return defaultValue;
        }
        Optional<Expression> expr = readPairValue(ann.asNormalAnnotationExpr().getPairs(), key);
        if (expr.isEmpty()) {
            return defaultValue;
        }
        Expression e = expr.get();
        if (e.isBooleanLiteralExpr()) {
            return e.asBooleanLiteralExpr().getValue();
        }
        return defaultValue;
    }

    private static Optional<String> readStringMember(AnnotationExpr ann, String key) {
        if (!ann.isNormalAnnotationExpr()) {
            return Optional.empty();
        }
        Optional<Expression> expr = readPairValue(ann.asNormalAnnotationExpr().getPairs(), key);
        if (expr.isEmpty()) {
            return Optional.empty();
        }
        return toStringValue(expr.get());
    }

    private static Optional<String> extractRequestMappingPath(NodeList<AnnotationExpr> annotations) {
        for (AnnotationExpr annotation : annotations) {
            if ("RequestMapping".equals(annotation.getNameAsString())) {
                return extractAnnotationPath(annotation);
            }
        }
        return Optional.empty();
    }

    private static boolean hasAnnotation(NodeList<AnnotationExpr> annotations, String simpleName) {
        for (AnnotationExpr annotation : annotations) {
            if (simpleName.equals(annotation.getNameAsString())) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> extractAnnotationPath(AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            return toStringValue(annotation.asSingleMemberAnnotationExpr().getMemberValue());
        }

        if (annotation.isNormalAnnotationExpr()) {
            NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
            Optional<String> path = readPathFromPairs(normal.getPairs(), "path");
            if (path.isPresent()) {
                return path;
            }
            return readPathFromPairs(normal.getPairs(), "value");
        }

        return Optional.empty();
    }

    private static Optional<String> readPathFromPairs(NodeList<MemberValuePair> pairs, String key) {
        for (MemberValuePair pair : pairs) {
            if (key.equals(pair.getNameAsString())) {
                return toStringValue(pair.getValue());
            }
        }
        return Optional.empty();
    }

    private static Optional<String> toStringValue(Expression expression) {
        if (expression.isStringLiteralExpr()) {
            StringLiteralExpr literalExpr = expression.asStringLiteralExpr();
            return Optional.of(literalExpr.asString());
        }

        if (expression.isArrayInitializerExpr()) {
            ArrayInitializerExpr arrayExpr = expression.asArrayInitializerExpr();
            for (Expression value : arrayExpr.getValues()) {
                if (value.isStringLiteralExpr()) {
                    return Optional.of(value.asStringLiteralExpr().asString());
                }
            }
        }

        return Optional.empty();
    }

    private static String normalizePath(String basePath, String methodPath) {
        String base = trimSlashes(basePath);
        String method = trimSlashes(methodPath);

        if (base.isEmpty() && method.isEmpty()) {
            return "/";
        }
        if (base.isEmpty()) {
            return "/" + method;
        }
        if (method.isEmpty()) {
            return "/" + base;
        }
        return "/" + base + "/" + method;
    }

    private static String trimSlashes(String path) {
        String normalized = path == null ? "" : path.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static List<PathMatcher> compileMatchers(List<String> patterns) {
        List<PathMatcher> matchers = new ArrayList<>();
        for (String p : patterns) {
            if (p == null || p.isBlank()) {
                continue;
            }
            String syntax = p.startsWith("glob:") || p.startsWith("regex:") ? p : "glob:" + p;
            matchers.add(FileSystems.getDefault().getPathMatcher(syntax));
        }
        return matchers;
    }

    private static boolean matches(Path relative, List<PathMatcher> includes, List<PathMatcher> excludes) {
        if (!excludes.isEmpty()) {
            for (PathMatcher ex : excludes) {
                if (ex.matches(relative)) {
                    return false;
                }
            }
        }
        if (includes.isEmpty()) {
            return true;
        }
        for (PathMatcher in : includes) {
            if (in.matches(relative)) {
                return true;
            }
        }
        return false;
    }

    private record MappingAttributes(List<String> httpMethods, String path, List<String> consumes, List<String> produces) {}
}

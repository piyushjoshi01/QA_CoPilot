package com.qacopilot.analysis.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Scans Java sources for Spring Security {@code SecurityFilterChain} beans and collects
 * {@code HttpSecurity}-style call names (best-effort; lambdas are partially visible to the AST).
 */
public final class SecurityFilterChainScanner {

    private static final Set<String> INTERESTING_CALLS = Set.of(
            "oauth2ResourceServer",
            "oauth2Login",
            "httpBasic",
            "formLogin",
            "anonymous",
            "authorizeHttpRequests",
            "authorizeRequests",
            "csrf",
            "cors",
            "sessionManagement",
            "jwt",
            "logout",
            "exceptionHandling",
            "requestCache",
            "securityContext",
            "headers",
            "rememberMe"
    );

    private static final Set<String> MATCHER_METHODS = Set.of("requestMatchers", "antMatchers", "mvcMatchers");

    private SecurityFilterChainScanner() {
    }

    public static SecurityScanResult scan(
            Path projectRoot,
            List<String> includePatterns,
            List<String> excludePatterns
    ) throws IOException {
        List<PathMatcher> includes = compileMatchers(includePatterns);
        List<PathMatcher> excludes = compileMatchers(excludePatterns);
        ParserConfiguration cfg = new ParserConfiguration();
        cfg.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        JavaParser parser = new JavaParser(cfg);

        Set<String> patterns = new LinkedHashSet<>();
        Set<String> matcherLiterals = new LinkedHashSet<>();
        Set<String> files = new LinkedHashSet<>();

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
                    var pr = parser.parse(path);
                    if (pr.getResult().isEmpty()) {
                        continue;
                    }
                    CompilationUnit cu = pr.getResult().get();
                    for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
                        if (!methodMightBeSecurityFilterChain(m)) {
                            continue;
                        }
                        String rel = relative.toString().replace('\\', '/');
                        files.add(rel);
                        for (MethodCallExpr call : m.findAll(MethodCallExpr.class)) {
                            String name = call.getNameAsString();
                            if (INTERESTING_CALLS.contains(name)) {
                                patterns.add(name);
                            }
                            if (MATCHER_METHODS.contains(name)) {
                                for (Expression arg : call.getArguments()) {
                                    collectStringLiterals(arg, matcherLiterals);
                                }
                            }
                            String scope = call.getScope().map(Object::toString).orElse("").toLowerCase(Locale.ROOT);
                            if (scope.contains("jwt") && name.length() > 2) {
                                patterns.add("jwtDecoder");
                            }
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("QA CoPilot: security scan skip " + path + ": " + ex.getMessage());
                }
            }
        }
        return new SecurityScanResult(new ArrayList<>(patterns), new ArrayList<>(matcherLiterals), new ArrayList<>(files));
    }

    private static void collectStringLiterals(Expression e, Set<String> sink) {
        if (e instanceof StringLiteralExpr sl) {
            sink.add(sl.asString());
        } else if (e instanceof ArrayInitializerExpr arr) {
            for (Expression x : arr.getValues()) {
                collectStringLiterals(x, sink);
            }
        }
    }

    private static boolean methodMightBeSecurityFilterChain(MethodDeclaration m) {
        String ret = m.getType().asString();
        if (ret.contains("SecurityFilterChain")) {
            return true;
        }
        boolean hasHttpSecurity = m.getParameters().stream().anyMatch(p -> p.getType().asString().contains("HttpSecurity"));
        return hasHttpSecurity && (ret.contains("SecurityFilterChain") || m.getBody().isPresent());
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
}

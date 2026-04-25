package com.qacopilot.generation.state;

import com.github.javaparser.JavaParser;
import com.qacopilot.analysis.extractor.AnalysisParserFactory;
import com.qacopilot.analysis.extractor.SecurityFilterChainScanner;
import com.qacopilot.analysis.extractor.SecurityScanResult;
import com.qacopilot.analysis.extractor.SpringAnalysisOptions;
import com.qacopilot.analysis.extractor.SpringEndpointExtractor;
import com.qacopilot.analysis.ir.EndpointContract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class ProjectEndpointExtractor {

    private ProjectEndpointExtractor() {
    }

    public record Result(List<EndpointContract> endpoints, SecurityScanResult security) {}

    public static Result extract(
            Path springProject,
            Path parseCacheFile,
            List<String> includePatterns,
            List<String> excludePatterns,
            SpringAnalysisOptions analysisOptions,
            boolean incrementalParse,
            List<Path> openApiExplicitSpecs,
            boolean discoverOpenApiSpecs
    ) throws IOException {
        SecurityScanResult security = SecurityFilterChainScanner.scan(springProject, includePatterns, excludePatterns);
        List<Path> resolvedOpenApiSpecs = resolveOpenApiSpecs(springProject, openApiExplicitSpecs, discoverOpenApiSpecs);
        String openApiFp = OpenApiSpecMerger.contentFingerprint(resolvedOpenApiSpecs);
        String analysisFp = AnalysisFingerprint.of(analysisOptions, openApiFp);

        SpringEndpointExtractor extractor = new SpringEndpointExtractor();

        if (!incrementalParse || parseCacheFile == null) {
            List<EndpointContract> all =
                    extractor.extractFromProject(springProject, includePatterns, excludePatterns, analysisOptions);
            all = OpenApiSpecMerger.merge(all, resolvedOpenApiSpecs);
            return new Result(all, security);
        }

        List<PathMatcher> includes = compileMatchers(includePatterns);
        List<PathMatcher> excludes = compileMatchers(excludePatterns);
        ParseCacheStore cache = ParseCacheStore.loadOrEmpty(parseCacheFile, analysisFp);

        Map<String, JavaSourceFileIndex.Entry> sourceIndex =
                JavaSourceFileIndex.build(springProject, includePatterns, excludePatterns);

        Set<String> hashMismatch = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(springProject)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(path) || !path.toString().endsWith(".java")) {
                    continue;
                }
                Path relative = springProject.relativize(path);
                if (!matches(relative, includes, excludes)) {
                    continue;
                }
                String relKey = relative.toString().replace('\\', '/');
                String content = Files.readString(path);
                String hash = sha256Hex(content);
                ParseCacheStore.CachedFile hit = cache.get(relKey);
                if (hit == null || !hash.equals(hit.sha256())) {
                    hashMismatch.add(relKey);
                }
            }
        }

        Set<String> dirtyRelKeys = JavaSourceFileIndex.expandDirtyForParseCache(hashMismatch, sourceIndex);

        JavaParser parser = AnalysisParserFactory.javaParser(springProject, analysisOptions);
        List<EndpointContract> merged = new ArrayList<>();
        Set<String> seenRel = new LinkedHashSet<>();

        try (Stream<Path> walk = Files.walk(springProject)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(path) || !path.toString().endsWith(".java")) {
                    continue;
                }
                Path relative = springProject.relativize(path);
                if (!matches(relative, includes, excludes)) {
                    continue;
                }
                String relKey = relative.toString().replace('\\', '/');
                seenRel.add(relKey);
                String content = Files.readString(path);
                String hash = sha256Hex(content);
                ParseCacheStore.CachedFile hit = cache.get(relKey);
                if (!dirtyRelKeys.contains(relKey) && hit != null && hash.equals(hit.sha256())) {
                    merged.addAll(hit.endpoints());
                } else {
                    List<EndpointContract> fresh = extractor.extractFromFile(path, springProject, parser, analysisOptions);
                    merged.addAll(fresh);
                    cache.put(relKey, hash, fresh);
                }
            }
        }

        cache.retainOnly(seenRel);
        cache.save(parseCacheFile);

        merged.sort(
                Comparator.comparing(EndpointContract::getPath)
                        .thenComparing(EndpointContract::getHttpMethod)
                        .thenComparing(EndpointContract::getHandlerMethodName)
        );
        merged = OpenApiSpecMerger.merge(merged, resolvedOpenApiSpecs);
        return new Result(merged, security);
    }

    private static List<Path> resolveOpenApiSpecs(
            Path springProject,
            List<Path> explicit,
            boolean discover
    ) throws IOException {
        Set<Path> set = new LinkedHashSet<>();
        if (explicit != null) {
            for (Path p : explicit) {
                if (p != null && Files.isRegularFile(p)) {
                    set.add(p.toAbsolutePath().normalize());
                }
            }
        }
        if (discover) {
            for (Path p : OpenApiSpecMerger.discoverSpecs(springProject)) {
                set.add(p.toAbsolutePath().normalize());
            }
        }
        return new ArrayList<>(set);
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

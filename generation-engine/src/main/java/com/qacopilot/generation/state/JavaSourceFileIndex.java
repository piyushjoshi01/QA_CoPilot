package com.qacopilot.generation.state;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lightweight per-file index (package, top-level types, imports) to invalidate parse-cache entries when
 * types defined in another file change (same package or explicit imports).
 */
public final class JavaSourceFileIndex {

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern TOP_LEVEL_TYPE =
            Pattern.compile("(?:^|\\n)\\s*(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?(?:strictfp\\s+)?(?:class|interface|enum|record)\\s+(\\w+)");
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.*]+)\\s*;", Pattern.MULTILINE);

    public record Entry(
            String relativeKey,
            String contentSha256,
            String packageName,
            Set<String> definedFqcn,
            Set<String> importedFqcn
    ) {}

    private JavaSourceFileIndex() {
    }

    public static Map<String, Entry> build(Path projectRoot, List<String> includePatterns, List<String> excludePatterns)
            throws IOException {
        List<PathMatcher> includes = compileMatchers(includePatterns);
        List<PathMatcher> excludes = compileMatchers(excludePatterns);
        Map<String, Entry> map = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(path) || !path.toString().endsWith(".java")) {
                    continue;
                }
                Path relative = projectRoot.relativize(path);
                if (!matches(relative, includes, excludes)) {
                    continue;
                }
                String content = Files.readString(path);
                String relKey = relative.toString().replace('\\', '/');
                map.put(relKey, entryFor(relKey, content));
            }
        }
        return map;
    }

    static Entry entryFor(String relKey, String content) {
        String pkg = "";
        Matcher pm = PACKAGE.matcher(content);
        if (pm.find()) {
            pkg = pm.group(1);
        }
        Set<String> defined = new LinkedHashSet<>();
        Matcher tm = TOP_LEVEL_TYPE.matcher(content);
        while (tm.find()) {
            String simple = tm.group(1);
            if (!pkg.isEmpty()) {
                defined.add(pkg + "." + simple);
            } else {
                defined.add(simple);
            }
        }
        Set<String> imports = new LinkedHashSet<>();
        Matcher im = IMPORT.matcher(content);
        while (im.find()) {
            imports.add(im.group(1));
        }
        return new Entry(relKey, sha256Hex(content), pkg, defined, imports);
    }

    /**
     * Files that must be re-parsed: content changed, import a dirty type, share a package with a dirty type, or
     * use a wildcard import covering a dirty type.
     */
    public static Set<String> expandDirtyForParseCache(Set<String> hashMismatchRelKeys, Map<String, Entry> index) {
        Set<String> dirty = new LinkedHashSet<>(hashMismatchRelKeys);
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<String> dirtyTypes = new LinkedHashSet<>();
            Set<String> dirtyPackages = new LinkedHashSet<>();
            for (String k : dirty) {
                Entry e = index.get(k);
                if (e == null) {
                    continue;
                }
                dirtyTypes.addAll(e.definedFqcn());
                for (String t : e.definedFqcn()) {
                    int dot = t.lastIndexOf('.');
                    if (dot > 0) {
                        dirtyPackages.add(t.substring(0, dot));
                    }
                }
            }
            for (Map.Entry<String, Entry> ent : index.entrySet()) {
                String key = ent.getKey();
                if (dirty.contains(key)) {
                    continue;
                }
                Entry e = ent.getValue();
                if (!e.packageName().isEmpty() && dirtyPackages.contains(e.packageName())) {
                    dirty.add(key);
                    changed = true;
                    continue;
                }
                if (importsTouchDirtyTypes(e.importedFqcn(), dirtyTypes)) {
                    dirty.add(key);
                    changed = true;
                }
            }
        }
        return dirty;
    }

    private static boolean importsTouchDirtyTypes(Set<String> imports, Set<String> dirtyTypes) {
        for (String imp : imports) {
            if (imp.endsWith(".*")) {
                String prefix = imp.substring(0, imp.length() - 2);
                for (String t : dirtyTypes) {
                    if (t.equals(prefix) || t.startsWith(prefix + ".")) {
                        return true;
                    }
                }
            } else if (dirtyTypes.contains(imp)) {
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

package com.qacopilot.generation.scenario;

import com.qacopilot.analysis.ir.EndpointContract;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Derives a stable folder / package segment for organizing features and step definitions by domain
 * (typically controller or first API resource segment).
 */
public final class DomainClassifier {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9_]+");
    private static final String[] CONTROLLER_SUFFIXES =
            new String[] {"RestController", "Controller", "Resource", "Facade", "Api", "Service"};

    private DomainClassifier() {
    }

    /**
     * Short folder name: {@code user}, {@code order}, {@code user_management}. Safe for Java package
     * segments and paths (lowercase [a-z0-9_], max 64 chars).
     */
    public static String domainKey(EndpointContract e) {
        String fromController = stripControllerBaseName(e.getControllerClassName());
        if (fromController != null && !fromController.isBlank()) {
            return sanitizeFolder(camelToSnake(fromController));
        }
        return sanitizeFolder(firstResourceSegment(e.getPath()));
    }

    /** Human title for {@code Feature:} line, e.g. {@code user_management} → {@code User management}. */
    public static String domainTitle(String domainKey) {
        if (domainKey == null || domainKey.isBlank()) {
            return "API";
        }
        String[] parts = domainKey.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.length() > 0 ? sb.toString() : "API";
    }

    /** PascalCase class name stem, e.g. {@code user_management} → {@code UserManagement}. */
    public static String domainClassStem(String domainKey) {
        if (domainKey == null || domainKey.isBlank()) {
            return "Misc";
        }
        String[] parts = domainKey.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.length() > 0 ? sb.toString() : "Misc";
    }

    private static String stripControllerBaseName(String controllerClassName) {
        if (controllerClassName == null || controllerClassName.isBlank()) {
            return null;
        }
        String n = controllerClassName.strip();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String suf : CONTROLLER_SUFFIXES) {
                if (n.length() > suf.length() && n.endsWith(suf)) {
                    n = n.substring(0, n.length() - suf.length());
                    changed = true;
                    break;
                }
            }
        }
        return n.isBlank() ? null : n;
    }

    private static String camelToSnake(String s) {
        if (s == null || s.isEmpty()) {
            return "misc";
        }
        String spaced = s.replaceAll("([a-z])([A-Z])", "$1_$2");
        return spaced.toLowerCase(Locale.ROOT);
    }

    private static String firstResourceSegment(String path) {
        if (path == null || path.isBlank()) {
            return "misc";
        }
        String p = path.strip();
        if (p.startsWith("/")) {
            p = p.substring(1);
        }
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        for (String segment : p.split("/")) {
            if (segment.isEmpty() || segment.startsWith("{")) {
                continue;
            }
            String skip = segment.toLowerCase(Locale.ROOT);
            if ("api".equals(skip) || "v1".equals(skip) || "v2".equals(skip) || "v3".equals(skip)) {
                continue;
            }
            String cleaned = segment.replaceAll("\\{[^}]+}", "");
            if (cleaned.isEmpty()) {
                continue;
            }
            return cleaned.toLowerCase(Locale.ROOT);
        }
        return "misc";
    }

    private static String sanitizeFolder(String raw) {
        if (raw == null || raw.isBlank()) {
            return "misc";
        }
        String s = raw.toLowerCase(Locale.ROOT).strip();
        s = NON_ALNUM.matcher(s).replaceAll("_");
        s = s.replaceAll("_+", "_");
        s = s.replaceAll("^_+|_+$", "");
        if (s.isEmpty()) {
            return "misc";
        }
        if (s.length() > 64) {
            s = s.substring(0, 64).replaceAll("_+$", "");
        }
        if (Character.isDigit(s.charAt(0))) {
            s = "d" + s;
        }
        return s;
    }
}

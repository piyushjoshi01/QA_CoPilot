package com.qacopilot.generation.openai;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;

/**
 * Loads variables from a {@code .env} file when they are not already in the process environment.
 * Search order:
 * <ol>
 *   <li>{@code QA_COPILOT_DOTENV} — absolute path to an env file</li>
 *   <li>Walk upward from {@code user.dir}</li>
 *   <li>Walk upward from the directory containing the runnable JAR (so {@code java -jar ...} works
 *       even when the shell cwd is not the QA CoPilot repo)</li>
 * </ol>
 */
public final class DotEnvLoader {

    private DotEnvLoader() {
    }

    /** Resolves a property using {@link #loadProperty(String, Path)} plus JAR-based search. */
    public static Optional<String> loadProperty(String name) {
        String explicit = System.getenv("QA_COPILOT_DOTENV");
        if (explicit != null && !explicit.isBlank()) {
            Path p = Path.of(explicit.strip()).toAbsolutePath().normalize();
            if (Files.isRegularFile(p)) {
                try {
                    Optional<String> v = parseFile(p, name);
                    if (v.isPresent()) {
                        return v;
                    }
                } catch (IOException e) {
                    System.err.println("QA CoPilot: could not read " + p + ": " + e.getMessage());
                }
            } else {
                System.err.println("QA CoPilot: QA_COPILOT_DOTENV is not a file: " + p);
            }
        }

        Optional<String> fromUserDir = loadProperty(name, Path.of(System.getProperty("user.dir", ".")));
        if (fromUserDir.isPresent()) {
            return fromUserDir;
        }

        Path jarAnchor = codeSourceJarParentDirectory();
        if (jarAnchor != null) {
            return loadProperty(name, jarAnchor);
        }
        return Optional.empty();
    }

    /** Walks upward from {@code startDir} looking for {@code .env}. */
    public static Optional<String> loadProperty(String name, Path startDir) {
        Path dir = startDir.toAbsolutePath().normalize();
        for (int depth = 0; depth < 12 && dir != null; depth++) {
            Path envFile = dir.resolve(".env");
            if (Files.isRegularFile(envFile)) {
                try {
                    Optional<String> v = parseFile(envFile, name);
                    if (v.isPresent()) {
                        return v;
                    }
                } catch (IOException e) {
                    System.err.println("QA CoPilot: could not read " + envFile + ": " + e.getMessage());
                    return Optional.empty();
                }
            }
            dir = dir.getParent();
        }
        return Optional.empty();
    }

    private static Path codeSourceJarParentDirectory() {
        try {
            URL loc = OpenAiConfig.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc == null) {
                return null;
            }
            URI uri = loc.toURI();
            Path p = Paths.get(uri);
            if (Files.isRegularFile(p) && p.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return p.getParent();
            }
            if (Files.isDirectory(p)) {
                return p;
            }
        } catch (URISyntaxException | SecurityException ignored) {
            return null;
        }
        return null;
    }

    static Optional<String> parseFile(Path envFile, String wantedKey) throws IOException {
        String raw = Files.readString(envFile, StandardCharsets.UTF_8);
        if (raw.startsWith("\uFEFF")) {
            raw = raw.substring(1);
        }
        String keyNorm = wantedKey.strip().toUpperCase(Locale.ROOT);
        for (String line : raw.split("\\R")) {
            String ln = line.strip();
            if (ln.isEmpty() || ln.startsWith("#")) {
                continue;
            }
            if (ln.startsWith("export ")) {
                ln = ln.substring(7).strip();
            }
            int eq = ln.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = ln.substring(0, eq).strip();
            String v = ln.substring(eq + 1).strip();
            v = stripQuotes(v);
            if (k.toUpperCase(Locale.ROOT).equals(keyNorm)) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }

    private static String stripQuotes(String v) {
        if (v.length() >= 2) {
            if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                return v.substring(1, v.length() - 1);
            }
        }
        return v;
    }
}

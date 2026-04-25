package com.qacopilot.analysis.extractor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Heuristic signals from {@code SecurityFilterChain} / {@code HttpSecurity} configuration beans. */
public final class SecurityScanResult {
    private final List<String> patterns;
    private final List<String> requestMatcherLiterals;
    private final List<String> sourceFiles;

    public SecurityScanResult(List<String> patterns, List<String> requestMatcherLiterals, List<String> sourceFiles) {
        this.patterns = patterns == null ? List.of() : List.copyOf(patterns);
        this.requestMatcherLiterals = requestMatcherLiterals == null ? List.of() : List.copyOf(requestMatcherLiterals);
        this.sourceFiles = sourceFiles == null ? List.of() : List.copyOf(sourceFiles);
    }

    public static SecurityScanResult empty() {
        return new SecurityScanResult(List.of(), List.of(), List.of());
    }

    public List<String> getPatterns() {
        return Collections.unmodifiableList(patterns);
    }

    public List<String> getRequestMatcherLiterals() {
        return Collections.unmodifiableList(requestMatcherLiterals);
    }

    public List<String> getSourceFiles() {
        return Collections.unmodifiableList(sourceFiles);
    }

    public boolean isEmpty() {
        return patterns.isEmpty() && requestMatcherLiterals.isEmpty() && sourceFiles.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SecurityScanResult that = (SecurityScanResult) o;
        return patterns.equals(that.patterns)
                && requestMatcherLiterals.equals(that.requestMatcherLiterals)
                && sourceFiles.equals(that.sourceFiles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patterns, requestMatcherLiterals, sourceFiles);
    }
}

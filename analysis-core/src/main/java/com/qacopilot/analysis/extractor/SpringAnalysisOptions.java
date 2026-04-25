package com.qacopilot.analysis.extractor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Options for JavaParser + symbol solving when scanning a Spring project. */
public final class SpringAnalysisOptions {

    private final boolean symbolSolverEnabled;
    private final List<Path> additionalSourceRoots;
    private final List<Path> jarClasspathEntries;
    private final int maxDtoDepth;
    private final int maxDtoFieldsPerType;

    private SpringAnalysisOptions(Builder b) {
        this.symbolSolverEnabled = b.symbolSolverEnabled;
        this.additionalSourceRoots = List.copyOf(b.additionalSourceRoots);
        this.jarClasspathEntries = List.copyOf(b.jarClasspathEntries);
        this.maxDtoDepth = b.maxDtoDepth;
        this.maxDtoFieldsPerType = b.maxDtoFieldsPerType;
    }

    public static SpringAnalysisOptions defaults() {
        return new Builder().symbolSolverEnabled(true).build();
    }

    /** Single-file / unit tests: no type solver (avoids needing a project root on the solver). */
    public static SpringAnalysisOptions forTests() {
        return new Builder().symbolSolverEnabled(false).build();
    }

    public boolean isSymbolSolverEnabled() {
        return symbolSolverEnabled;
    }

    public List<Path> getAdditionalSourceRoots() {
        return Collections.unmodifiableList(additionalSourceRoots);
    }

    public List<Path> getJarClasspathEntries() {
        return Collections.unmodifiableList(jarClasspathEntries);
    }

    public int getMaxDtoDepth() {
        return maxDtoDepth;
    }

    public int getMaxDtoFieldsPerType() {
        return maxDtoFieldsPerType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean symbolSolverEnabled = true;
        private final List<Path> additionalSourceRoots = new ArrayList<>();
        private final List<Path> jarClasspathEntries = new ArrayList<>();
        private int maxDtoDepth = 4;
        private int maxDtoFieldsPerType = 64;

        public Builder symbolSolverEnabled(boolean v) {
            this.symbolSolverEnabled = v;
            return this;
        }

        public Builder addSourceRoot(Path path) {
            if (path != null) {
                additionalSourceRoots.add(path);
            }
            return this;
        }

        public Builder addJarClasspathEntry(Path path) {
            if (path != null) {
                jarClasspathEntries.add(path);
            }
            return this;
        }

        public Builder maxDtoDepth(int maxDtoDepth) {
            this.maxDtoDepth = Math.max(0, maxDtoDepth);
            return this;
        }

        public Builder maxDtoFieldsPerType(int maxDtoFieldsPerType) {
            this.maxDtoFieldsPerType = Math.max(1, maxDtoFieldsPerType);
            return this;
        }

        public SpringAnalysisOptions build() {
            return new SpringAnalysisOptions(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SpringAnalysisOptions that = (SpringAnalysisOptions) o;
        return symbolSolverEnabled == that.symbolSolverEnabled
                && maxDtoDepth == that.maxDtoDepth
                && maxDtoFieldsPerType == that.maxDtoFieldsPerType
                && additionalSourceRoots.equals(that.additionalSourceRoots)
                && jarClasspathEntries.equals(that.jarClasspathEntries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbolSolverEnabled, additionalSourceRoots, jarClasspathEntries, maxDtoDepth, maxDtoFieldsPerType);
    }
}

package com.qacopilot.analysis.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AnalysisParserFactory {

    private AnalysisParserFactory() {
    }

    public static ParserConfiguration parserConfiguration(Path projectRoot, SpringAnalysisOptions options) {
        ParserConfiguration cfg = new ParserConfiguration();
        cfg.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        if (!options.isSymbolSolverEnabled()) {
            return cfg;
        }
        CombinedTypeSolver solver = new CombinedTypeSolver();
        solver.add(new ReflectionTypeSolver());
        for (Path root : collectSourceRoots(projectRoot, options)) {
            if (root != null && Files.isDirectory(root)) {
                solver.add(new JavaParserTypeSolver(root));
            }
        }
        for (Path jar : options.getJarClasspathEntries()) {
            if (jar != null && Files.isRegularFile(jar) && jar.toString().endsWith(".jar")) {
                try {
                    solver.add(new JarTypeSolver(jar));
                } catch (IOException ex) {
                    System.err.println("QA CoPilot: skip JarTypeSolver " + jar + ": " + ex.getMessage());
                }
            }
        }
        cfg.setSymbolResolver(new JavaSymbolSolver(solver));
        return cfg;
    }

    public static JavaParser javaParser(Path projectRoot, SpringAnalysisOptions options) {
        return new JavaParser(parserConfiguration(projectRoot, options));
    }

    private static List<Path> collectSourceRoots(Path projectRoot, SpringAnalysisOptions options) {
        Set<Path> roots = new LinkedHashSet<>();
        if (projectRoot != null) {
            Path[] std = {
                    projectRoot.resolve("src/main/java"),
                    projectRoot.resolve("src/test/java")
            };
            for (Path p : std) {
                if (Files.isDirectory(p)) {
                    roots.add(p.toAbsolutePath().normalize());
                }
            }
        }
        for (Path extra : options.getAdditionalSourceRoots()) {
            if (extra != null && Files.isDirectory(extra)) {
                roots.add(extra.toAbsolutePath().normalize());
            }
        }
        return new ArrayList<>(roots);
    }
}

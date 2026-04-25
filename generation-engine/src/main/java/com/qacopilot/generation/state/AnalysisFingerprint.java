package com.qacopilot.generation.state;

import com.qacopilot.analysis.extractor.SpringAnalysisOptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class AnalysisFingerprint {

    private AnalysisFingerprint() {
    }

    /**
     * Fingerprint for parse-cache invalidation: analysis options plus merged OpenAPI spec file content hashes.
     */
    public static String of(SpringAnalysisOptions options, String openApiContentFingerprint) {
        StringBuilder sb = new StringBuilder();
        sb.append("sym=").append(options.isSymbolSolverEnabled());
        sb.append("|dtoDepth=").append(options.getMaxDtoDepth());
        sb.append("|dtoFields=").append(options.getMaxDtoFieldsPerType());
        for (Path p : options.getAdditionalSourceRoots()) {
            sb.append("|srcRoot=").append(p.toAbsolutePath().normalize());
        }
        for (Path p : options.getJarClasspathEntries()) {
            sb.append("|jar=").append(p.toAbsolutePath().normalize());
        }
        sb.append("|openapi=").append(openApiContentFingerprint == null ? "" : openApiContentFingerprint);
        return sha256Hex(sb.toString());
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

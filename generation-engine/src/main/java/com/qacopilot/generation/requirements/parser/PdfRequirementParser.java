package com.qacopilot.generation.requirements.parser;

import com.qacopilot.generation.requirements.RequirementDocument;
import com.qacopilot.generation.requirements.RequirementParseException;
import com.qacopilot.generation.requirements.RequirementParser;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser for PDF requirement documents.
 *
 * <p>Uses Apache PDFBox 3.x via reflection so that the dependency is optional at runtime.
 * If PDFBox is not on the classpath, a descriptive error is thrown instead of a
 * {@link NoClassDefFoundError}.</p>
 *
 * <p>To enable PDF support, add to {@code generation-engine/build.gradle.kts}:
 * <pre>
 *   implementation("org.apache.pdfbox:pdfbox:3.0.3")
 * </pre>
 * </p>
 */
public final class PdfRequirementParser implements RequirementParser {

    private static final String PDFBOX_CLASS = "org.apache.pdfbox.pdmodel.PDDocument";
    private static final int MAX_CHARS = 200_000;

    @Override
    public boolean supports(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    @Override
    public RequirementDocument parse(Path file) throws RequirementParseException {
        checkPdfBoxAvailable();
        try {
            String text = extractTextViaPdfBox(file);
            if (text.length() > MAX_CHARS) {
                text = text.substring(0, MAX_CHARS) + "\n\n[… PDF truncated at " + MAX_CHARS + " characters]";
            }
            List<String> sections = extractPdfSections(text);
            return new RequirementDocument(file, "application/pdf", text.strip(), sections);
        } catch (RequirementParseException e) {
            throw e;
        } catch (Exception e) {
            throw new RequirementParseException("Failed to parse PDF: " + file + " — " + e.getMessage(), e);
        }
    }

    // ── PDFBox via reflection ─────────────────────────────────────────────────

    private static String extractTextViaPdfBox(Path file) throws Exception {
        Class<?> loaderClass = Class.forName("org.apache.pdfbox.Loader");
        Class<?> pdDocClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
        Class<?> stripperClass = Class.forName("org.apache.pdfbox.text.PDFTextStripper");

        // PDDocument doc = Loader.loadPDF(file.toFile())
        Method loadPDF = loaderClass.getMethod("loadPDF", java.io.File.class);
        Object doc = loadPDF.invoke(null, file.toFile());

        try {
            // PDFTextStripper stripper = new PDFTextStripper()
            Object stripper = stripperClass.getDeclaredConstructor().newInstance();
            // String text = stripper.getText(doc)
            Method getText = stripperClass.getMethod("getText", pdDocClass);
            return (String) getText.invoke(stripper, doc);
        } finally {
            // doc.close()
            pdDocClass.getMethod("close").invoke(doc);
        }
    }

    private static void checkPdfBoxAvailable() throws RequirementParseException {
        try {
            Class.forName(PDFBOX_CLASS);
        } catch (ClassNotFoundException e) {
            throw new RequirementParseException(
                    "Apache PDFBox is not on the classpath. Add to build.gradle.kts:\n"
                            + "  implementation(\"org.apache.pdfbox:pdfbox:3.0.3\")\n"
                            + "then rebuild the shadow JAR: gradle :generation-engine:shadowJar");
        }
    }

    private static List<String> extractPdfSections(String text) {
        List<String> sections = new ArrayList<>();
        for (String line : text.lines().toList()) {
            String trimmed = line.strip();
            // Short, bold-ish lines that look like headings (heuristic)
            if (trimmed.length() >= 3 && trimmed.length() <= 100
                    && !trimmed.contains(".")
                    && (Character.isUpperCase(trimmed.charAt(0)) || trimmed.matches("\\d+\\..*"))) {
                sections.add(trimmed);
                if (sections.size() >= 50) break; // cap
            }
        }
        return sections;
    }
}

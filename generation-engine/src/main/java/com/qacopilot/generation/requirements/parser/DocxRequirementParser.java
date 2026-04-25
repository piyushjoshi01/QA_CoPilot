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
 * Parser for Microsoft Word ({@code .docx}) requirement documents.
 *
 * <p>Uses Apache POI 5.x via reflection so the dependency is optional at runtime.
 * If POI is not on the classpath a descriptive error is thrown.</p>
 *
 * <p>To enable DOCX support, add to {@code generation-engine/build.gradle.kts}:
 * <pre>
 *   implementation("org.apache.poi:poi-ooxml:5.3.0")
 * </pre>
 * </p>
 *
 * <p>Note: Only {@code .docx} (OOXML) is supported — the legacy {@code .doc} (OLE2) format
 * requires {@code poi} (not {@code poi-ooxml}) and is not handled here.</p>
 */
public final class DocxRequirementParser implements RequirementParser {

    private static final String POI_CLASS = "org.apache.poi.xwpf.usermodel.XWPFDocument";
    private static final int MAX_CHARS = 200_000;

    @Override
    public boolean supports(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".docx");
    }

    @Override
    public RequirementDocument parse(Path file) throws RequirementParseException {
        checkPoiAvailable();
        try {
            DocxContent extracted = extractDocxContent(file);
            String text = extracted.body();
            if (text.length() > MAX_CHARS) {
                text = text.substring(0, MAX_CHARS) + "\n\n[… DOCX truncated at " + MAX_CHARS + " characters]";
            }
            return new RequirementDocument(
                    file,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    text.strip(),
                    extracted.headings());
        } catch (RequirementParseException e) {
            throw e;
        } catch (Exception e) {
            throw new RequirementParseException("Failed to parse DOCX: " + file + " — " + e.getMessage(), e);
        }
    }

    // ── Apache POI via reflection ─────────────────────────────────────────────

    private record DocxContent(String body, List<String> headings) {
    }

    @SuppressWarnings("unchecked")
    private static DocxContent extractDocxContent(Path file) throws Exception {
        Class<?> docClass = Class.forName("org.apache.poi.xwpf.usermodel.XWPFDocument");
        Class<?> parClass = Class.forName("org.apache.poi.xwpf.usermodel.XWPFParagraph");

        // XWPFDocument doc = new XWPFDocument(FileInputStream)
        java.io.InputStream is = java.nio.file.Files.newInputStream(file);
        Object doc = docClass.getConstructor(java.io.InputStream.class).newInstance(is);

        try {
            Method getParagraphs = docClass.getMethod("getParagraphs");
            List<Object> paragraphs = (List<Object>) getParagraphs.invoke(doc);

            Method getText = parClass.getMethod("getText");
            Method getStyle = parClass.getMethod("getStyle");

            StringBuilder sb = new StringBuilder();
            List<String> headings = new ArrayList<>();

            for (Object para : paragraphs) {
                String text = (String) getText.invoke(para);
                if (text == null || text.isBlank()) continue;

                String style = "";
                try {
                    Object styleObj = getStyle.invoke(para);
                    if (styleObj != null) style = styleObj.toString().toLowerCase(Locale.ROOT);
                } catch (Exception ignore) {
                }

                sb.append(text).append('\n');
                if (style.contains("heading") || style.matches(".*h[1-6].*")) {
                    headings.add(text.strip());
                }
            }

            return new DocxContent(sb.toString(), headings);
        } finally {
            docClass.getMethod("close").invoke(doc);
            is.close();
        }
    }

    private static void checkPoiAvailable() throws RequirementParseException {
        try {
            Class.forName(POI_CLASS);
        } catch (ClassNotFoundException e) {
            throw new RequirementParseException(
                    "Apache POI is not on the classpath. Add to build.gradle.kts:\n"
                            + "  implementation(\"org.apache.poi:poi-ooxml:5.3.0\")\n"
                            + "then rebuild the shadow JAR: gradle :generation-engine:shadowJar");
        }
    }
}

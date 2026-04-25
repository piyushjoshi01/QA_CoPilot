package com.qacopilot.generation.requirements.parser;

import com.qacopilot.generation.requirements.RequirementDocument;
import com.qacopilot.generation.requirements.RequirementParseException;
import com.qacopilot.generation.requirements.RequirementParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser for plain text ({@code .txt}) and Markdown ({@code .md}) requirement documents.
 *
 * <p>Extracts headings (lines starting with {@code #} for Markdown, or {@code ALL CAPS} lines
 * for plain text) as the sections list, and returns the full file content as-is.</p>
 */
public final class TxtMarkdownRequirementParser implements RequirementParser {

    private static final int MAX_CHARS = 200_000; // ~150k tokens — truncate if larger

    @Override
    public boolean supports(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".markdown");
    }

    @Override
    public RequirementDocument parse(Path file) throws RequirementParseException {
        try {
            String content = Files.readString(file);
            if (content.length() > MAX_CHARS) {
                content = content.substring(0, MAX_CHARS) + "\n\n[… document truncated at " + MAX_CHARS + " characters]";
            }
            List<String> sections = extractSections(content);
            String format = file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt") ? "text/plain" : "text/markdown";
            return new RequirementDocument(file, format, content, sections);
        } catch (IOException e) {
            throw new RequirementParseException("Failed to read file: " + file + " — " + e.getMessage(), e);
        }
    }

    private static List<String> extractSections(String content) {
        List<String> sections = new ArrayList<>();
        for (String line : content.lines().toList()) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) continue;

            // Markdown headings: #, ##, ###
            if (trimmed.startsWith("#")) {
                sections.add(trimmed.replaceFirst("^#{1,6}\\s*", "").strip());
                continue;
            }
            // Plain text: ALL CAPS line that looks like a heading (3-80 chars)
            if (trimmed.length() >= 3 && trimmed.length() <= 80
                    && trimmed.equals(trimmed.toUpperCase(Locale.ROOT))
                    && trimmed.chars().anyMatch(Character::isLetter)) {
                sections.add(trimmed);
            }
        }
        return sections;
    }
}

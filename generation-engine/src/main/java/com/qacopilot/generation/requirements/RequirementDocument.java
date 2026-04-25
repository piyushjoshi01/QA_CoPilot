package com.qacopilot.generation.requirements;

import java.nio.file.Path;
import java.util.List;

/**
 * Raw, pre-extraction representation of an ingested requirement document.
 *
 * <p>Holds the extracted plain text and structural metadata from the original file.
 * This is the input to {@link RequirementExtractor} which turns it into
 * a list of {@link Requirement} objects.</p>
 */
public record RequirementDocument(
        /** Absolute path to the source file. */
        Path sourcePath,
        /** MIME-like type detected during ingestion. */
        String format,
        /** Full plain-text body extracted from the file. */
        String textContent,
        /** Top-level section / heading titles found in the document (for context). */
        List<String> sections) {

    public RequirementDocument {
        if (textContent == null) textContent = "";
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    /** Returns just the file name (not the full path). */
    public String fileName() {
        return sourcePath.getFileName().toString();
    }

    /** Returns {@code true} if the document has non-empty text content. */
    public boolean hasContent() {
        return !textContent.isBlank();
    }

    /** Truncated preview for logging/debugging. */
    public String preview(int chars) {
        return textContent.length() <= chars ? textContent : textContent.substring(0, chars) + "…";
    }
}

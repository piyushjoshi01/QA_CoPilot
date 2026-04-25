package com.qacopilot.generation.requirements;

import java.nio.file.Path;

/**
 * Strategy interface for extracting plain text from a requirement document.
 *
 * <p>Each implementation handles a specific file format (TXT, Markdown, PDF, DOCX).
 * The pipeline is: {@code file → RequirementParser → RequirementDocument → RequirementExtractor → Requirement[]}</p>
 */
public interface RequirementParser {

    /** Returns {@code true} if this parser can handle the given file path. */
    boolean supports(Path file);

    /**
     * Parse the file into a {@link RequirementDocument} containing raw text and sections.
     *
     * @param file Absolute path to the requirements document.
     * @return     Parsed document; never {@code null}.
     * @throws RequirementParseException if the file cannot be read or parsed.
     */
    RequirementDocument parse(Path file) throws RequirementParseException;
}

package com.qacopilot.generation.requirements;

import com.qacopilot.generation.requirements.parser.DocxRequirementParser;
import com.qacopilot.generation.requirements.parser.PdfRequirementParser;
import com.qacopilot.generation.requirements.parser.TxtMarkdownRequirementParser;

import java.nio.file.Path;
import java.util.List;

/**
 * Selects the correct {@link RequirementParser} for a given file path.
 */
public final class RequirementParserFactory {

    private static final List<RequirementParser> PARSERS = List.of(
            new TxtMarkdownRequirementParser(),
            new PdfRequirementParser(),
            new DocxRequirementParser());

    private RequirementParserFactory() {
    }

    /**
     * Find a parser that {@link RequirementParser#supports(Path) supports} the file.
     *
     * @throws RequirementParseException if no parser handles the file's extension.
     */
    public static RequirementParser forFile(Path file) throws RequirementParseException {
        for (RequirementParser p : PARSERS) {
            if (p.supports(file)) return p;
        }
        throw new RequirementParseException(
                "No parser available for '" + file.getFileName() + "'. "
                        + "Supported formats: .txt, .md, .pdf, .docx");
    }

    /**
     * Convenience: parse the file directly.
     */
    public static RequirementDocument parse(Path file) throws RequirementParseException {
        return forFile(file).parse(file);
    }
}

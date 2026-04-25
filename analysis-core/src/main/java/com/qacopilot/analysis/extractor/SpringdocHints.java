package com.qacopilot.analysis.extractor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Swagger / springdoc annotations on a controller handler (best-effort). */
public final class SpringdocHints {
    private final String summary;
    private final List<String> tags;
    private final List<Integer> responseCodes;

    public SpringdocHints(String summary, List<String> tags, List<Integer> responseCodes) {
        this.summary = summary;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.responseCodes = responseCodes == null ? List.of() : List.copyOf(responseCodes);
    }

    public static SpringdocHints empty() {
        return new SpringdocHints(null, List.of(), List.of());
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    public List<Integer> getResponseCodes() {
        return Collections.unmodifiableList(responseCodes);
    }

    public boolean isEmpty() {
        return summary == null && tags.isEmpty() && responseCodes.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SpringdocHints that = (SpringdocHints) o;
        return Objects.equals(summary, that.summary)
                && tags.equals(that.tags)
                && responseCodes.equals(that.responseCodes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(summary, tags, responseCodes);
    }
}

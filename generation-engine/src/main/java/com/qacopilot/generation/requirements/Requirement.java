package com.qacopilot.generation.requirements;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A single, structured business requirement extracted from a document.
 *
 * <p>JSON schema (stored in {@code .qacopilot/requirements/requirements.json}):
 * <pre>{@code
 * {
 *   "requirementId": "REQ-001",
 *   "title":         "User Registration",
 *   "description":   "The system shall allow new users to register...",
 *   "type":          "FUNCTIONAL",
 *   "priority":      "HIGH",
 *   "acceptanceCriteria": [
 *     "User receives a confirmation email",
 *     "Duplicate email returns 409"
 *   ],
 *   "edgeCases": [
 *     "Email with + and . variants",
 *     "Very long passwords"
 *   ],
 *   "constraints": [
 *     "Password must be at least 8 characters",
 *     "Username must be unique"
 *   ],
 *   "sourceFile":    "requirements.pdf",
 *   "sourceSection": "Section 3.1 — User Management"
 * }
 * }</pre>
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Requirement {

    public enum Type { FUNCTIONAL, NON_FUNCTIONAL, CONSTRAINT, BUSINESS_RULE }
    public enum Priority { CRITICAL, HIGH, MEDIUM, LOW }

    public String requirementId;
    public String title;
    public String description;
    public Type type = Type.FUNCTIONAL;
    public Priority priority = Priority.MEDIUM;
    public List<String> acceptanceCriteria = List.of();
    public List<String> edgeCases = List.of();
    public List<String> constraints = List.of();
    /** Relative path to the source document. */
    public String sourceFile;
    /** Section / heading where this requirement was found. */
    public String sourceSection;
    /** Which endpoint stableIds this requirement maps to (populated by RequirementCoverageMapper). */
    public List<String> coveredByEndpoints = List.of();

    public Requirement() {
    }

    public Requirement(String requirementId, String title, String description) {
        this.requirementId = requirementId;
        this.title = title;
        this.description = description;
    }

    @Override
    public String toString() {
        return "[" + requirementId + "] " + title;
    }
}

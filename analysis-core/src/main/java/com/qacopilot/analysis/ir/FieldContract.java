package com.qacopilot.analysis.ir;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** DTO field (populated when type is resolved in a later phase; often empty in v1 extraction). */
public final class FieldContract {
    private final String name;
    private final String javaType;
    private final boolean required;
    private final List<String> validations;

    public FieldContract(String name, String javaType, boolean required, List<String> validations) {
        this.name = Objects.requireNonNull(name, "name");
        this.javaType = Objects.requireNonNull(javaType, "javaType");
        this.required = required;
        this.validations = validations == null ? List.of() : List.copyOf(validations);
    }

    public String getName() {
        return name;
    }

    public String getJavaType() {
        return javaType;
    }

    public boolean isRequired() {
        return required;
    }

    public List<String> getValidations() {
        return Collections.unmodifiableList(validations);
    }
}

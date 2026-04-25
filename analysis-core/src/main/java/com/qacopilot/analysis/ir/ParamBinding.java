package com.qacopilot.analysis.ir;

import java.util.Objects;

/** A path variable, query parameter, or header binding on a handler method. */
public final class ParamBinding {
    private final String name;
    private final String javaType;
    private final boolean required;
    /** Raw default from source, or empty if none. */
    private final String defaultValue;

    public ParamBinding(String name, String javaType, boolean required, String defaultValue) {
        this.name = Objects.requireNonNull(name, "name");
        this.javaType = Objects.requireNonNull(javaType, "javaType");
        this.required = required;
        this.defaultValue = defaultValue == null ? "" : defaultValue;
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

    public String getDefaultValue() {
        return defaultValue;
    }
}

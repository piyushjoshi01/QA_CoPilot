package com.qacopilot.analysis.ir;

import java.util.Objects;

public final class AuthContract {
    private final String type;
    private final boolean required;

    public AuthContract(String type, boolean required) {
        this.type = Objects.requireNonNull(type, "type");
        this.required = required;
    }

    public static AuthContract none() {
        return new AuthContract("none", false);
    }

    public String getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }
}

package com.qacopilot.analysis.ir;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RequestBodyContract {
    private final boolean present;
    private final boolean required;
    private final String dtoJavaType;
    private final List<FieldContract> fields;

    private RequestBodyContract(boolean present, boolean required, String dtoJavaType, List<FieldContract> fields) {
        this.present = present;
        this.required = required;
        this.dtoJavaType = dtoJavaType;
        this.fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public static RequestBodyContract absent() {
        return new RequestBodyContract(false, false, null, List.of());
    }

    public static RequestBodyContract of(String dtoJavaType, boolean required, List<FieldContract> fields) {
        Objects.requireNonNull(dtoJavaType, "dtoJavaType");
        return new RequestBodyContract(true, required, dtoJavaType, fields == null ? List.of() : fields);
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isRequired() {
        return required;
    }

    public String getDtoJavaType() {
        return dtoJavaType;
    }

    public List<FieldContract> getFields() {
        return Collections.unmodifiableList(fields);
    }
}

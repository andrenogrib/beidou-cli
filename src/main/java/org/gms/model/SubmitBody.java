package org.gms.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmitBody(String requestId, Object data) {
    public SubmitBody(Object data) {
        this(null, data);
    }
}

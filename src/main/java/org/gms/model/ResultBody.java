package org.gms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResultBody(Integer code, String message, String responseId, Object data) {
    public boolean isSuccess() {
        return code != null && code == 200;
    }
}

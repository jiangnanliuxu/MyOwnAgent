package com.agentdesk.backend.common.api;

import com.agentdesk.backend.common.web.TraceId;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        Object details,
        @JsonProperty("request_id")
        String requestId
) {
    public static final String SUCCESS_CODE = "0";
    public static final String SUCCESS_MESSAGE = "ok";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data, null, TraceId.current());
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return failure(code, message, null);
    }

    public static <T> ApiResponse<T> failure(String code, String message, Object details) {
        return new ApiResponse<>(code, message, null, details, TraceId.current());
    }
}

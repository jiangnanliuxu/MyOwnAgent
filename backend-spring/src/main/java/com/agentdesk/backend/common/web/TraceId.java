package com.agentdesk.backend.common.web;

import org.slf4j.MDC;

public final class TraceId {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "traceId";

    private TraceId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}

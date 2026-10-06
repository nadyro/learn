package com.kestrel.commerce.shared.web;

import com.kestrel.commerce.shared.error.ErrorCode;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 "problem details" bodies, the single error format used by every endpoint of this service. */
public final class Problems {

    private static final URI BLANK_TYPE = URI.create("about:blank");

    private Problems() {}

    public static ProblemDetail of(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setType(URI.create(code.typeUri()));
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        return enrich(problem, null);
    }

    /**
     * Adds the fields every error response must carry: a {@code code}, a meaningful {@code type}, the request path and
     * the request ID clients should quote when contacting support.
     */
    public static ProblemDetail enrich(ProblemDetail problem, String requestPath) {
        ErrorCode fallback = ErrorCode.fromStatus(problem.getStatus());
        if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
            problem.setProperty("code", fallback.name());
        }
        if (problem.getType() == null || BLANK_TYPE.equals(problem.getType())) {
            problem.setType(URI.create(fallback.typeUri()));
        }
        if (problem.getInstance() == null && requestPath != null) {
            problem.setInstance(URI.create(requestPath));
        }
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }

    /** Flattens a problem into the JSON structure mandated by RFC 9457 (extra properties at the top level). */
    public static Map<String, Object> toMap(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.getType().toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        if (problem.getDetail() != null) {
            body.put("detail", problem.getDetail());
        }
        if (problem.getInstance() != null) {
            body.put("instance", problem.getInstance().toString());
        }
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        return body;
    }
}

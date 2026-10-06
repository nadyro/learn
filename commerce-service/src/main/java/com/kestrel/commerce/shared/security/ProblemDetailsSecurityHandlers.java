package com.kestrel.commerce.shared.security;

import com.kestrel.commerce.shared.error.ErrorCode;
import com.kestrel.commerce.shared.web.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes 401 and 403 responses produced by Spring Security use the same problem format as the rest of the API.
 *
 * <p>We delegate to the standard bearer-token handlers first so the {@code WWW-Authenticate} header required by RFC
 * 6750 is still set, then we write the JSON body.
 */
final class ProblemDetailsSecurityHandlers {

    private static final String UNAUTHENTICATED_DETAIL =
            "A valid bearer access token is required to access this resource.";
    private static final String ACCESS_DENIED_DETAIL = "You are not allowed to access this resource.";

    private ProblemDetailsSecurityHandlers() {}

    static AuthenticationEntryPoint authenticationEntryPoint(JsonMapper jsonMapper) {
        BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
        return (request, response, exception) -> {
            delegate.commence(request, response, exception);
            write(jsonMapper, request, response, ErrorCode.UNAUTHENTICATED, UNAUTHENTICATED_DETAIL);
        };
    }

    static AccessDeniedHandler accessDeniedHandler(JsonMapper jsonMapper) {
        BearerTokenAccessDeniedHandler delegate = new BearerTokenAccessDeniedHandler();
        return (request, response, exception) -> {
            delegate.handle(request, response, exception);
            write(jsonMapper, request, response, ErrorCode.ACCESS_DENIED, ACCESS_DENIED_DETAIL);
        };
    }

    private static void write(
            JsonMapper jsonMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode code,
            String detail)
            throws IOException {
        ProblemDetail problem = Problems.enrich(Problems.of(code, detail), request.getRequestURI());
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), Problems.toMap(problem));
    }
}

package com.kestrel.commerce;

import static com.kestrel.commerce.support.TestJwts.admin;
import static com.kestrel.commerce.support.TestJwts.newCustomer;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kestrel.commerce.support.IntegrationTest;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The authorization matrix of the API. Every new endpoint must be added here: it documents who can call what and
 * guarantees a refactoring of SecurityConfig cannot silently open an endpoint.
 */
class SecurityIT extends IntegrationTest {

    private static final String ANY_ID = "0192f0a0-0000-7000-8000-00000000ffff";

    enum Caller {
        ANONYMOUS,
        CUSTOMER,
        ADMIN;

        RequestPostProcessor authentication() {
            return switch (this) {
                case ANONYMOUS -> request -> request;
                case CUSTOMER -> newCustomer();
                case ADMIN -> admin();
            };
        }
    }

    static Stream<Arguments> forbiddenCalls() {
        return Stream.of(
                // Customer endpoints need an authenticated customer
                Arguments.of(Caller.ANONYMOUS, HttpMethod.GET, "/api/v1/customers/me", 401),
                Arguments.of(Caller.ANONYMOUS, HttpMethod.GET, "/api/v1/orders", 401),
                Arguments.of(Caller.ANONYMOUS, HttpMethod.POST, "/api/v1/orders", 401),
                Arguments.of(Caller.ANONYMOUS, HttpMethod.GET, "/api/v1/orders/" + ANY_ID, 401),
                // Back-office endpoints need the admin role
                Arguments.of(Caller.ANONYMOUS, HttpMethod.GET, "/api/v1/admin/products", 401),
                Arguments.of(Caller.CUSTOMER, HttpMethod.GET, "/api/v1/admin/products", 403),
                Arguments.of(Caller.CUSTOMER, HttpMethod.POST, "/api/v1/admin/products", 403),
                Arguments.of(Caller.CUSTOMER, HttpMethod.PUT, "/api/v1/admin/products/" + ANY_ID, 403),
                Arguments.of(Caller.CUSTOMER, HttpMethod.POST, "/api/v1/admin/products/" + ANY_ID + "/archive", 403),
                Arguments.of(Caller.CUSTOMER, HttpMethod.GET, "/api/v1/admin/inventory/" + ANY_ID, 403),
                Arguments.of(
                        Caller.CUSTOMER, HttpMethod.POST, "/api/v1/admin/inventory/" + ANY_ID + "/adjustments", 403),
                Arguments.of(Caller.CUSTOMER, HttpMethod.GET, "/api/v1/admin/orders", 403),
                Arguments.of(Caller.CUSTOMER, HttpMethod.POST, "/api/v1/admin/orders/" + ANY_ID + "/shipment", 403),
                Arguments.of(Caller.CUSTOMER, HttpMethod.POST, "/api/v1/admin/orders/" + ANY_ID + "/delivery", 403),
                Arguments.of(Caller.CUSTOMER, HttpMethod.POST, "/api/v1/admin/orders/" + ANY_ID + "/cancel", 403),
                // Catalog is read-only for the public
                Arguments.of(Caller.ANONYMOUS, HttpMethod.POST, "/api/v1/products", 401),
                Arguments.of(Caller.ANONYMOUS, HttpMethod.DELETE, "/api/v1/products/" + ANY_ID, 401),
                // Unknown routes are denied, not left open by accident
                Arguments.of(Caller.ANONYMOUS, HttpMethod.GET, "/internal/secret", 401),
                Arguments.of(Caller.ADMIN, HttpMethod.GET, "/internal/secret", 403));
    }

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @MethodSource("forbiddenCalls")
    void callers_without_the_required_role_are_rejected(Caller caller, HttpMethod method, String path, int expected)
            throws Exception {
        mvc.perform(request(method, path).with(caller.authentication()))
                .andExpect(status().is(expected))
                .andExpect(jsonPath("$.code").value(expected == 401 ? "UNAUTHENTICATED" : "ACCESS_DENIED"));
    }

    static Stream<Arguments> allowedCalls() {
        return Stream.of(
                Arguments.of(Caller.ANONYMOUS, HttpMethod.GET, "/api/v1/products"),
                Arguments.of(Caller.CUSTOMER, HttpMethod.GET, "/api/v1/customers/me"),
                Arguments.of(Caller.CUSTOMER, HttpMethod.GET, "/api/v1/orders"),
                Arguments.of(Caller.ADMIN, HttpMethod.GET, "/api/v1/admin/products"),
                Arguments.of(Caller.ADMIN, HttpMethod.GET, "/api/v1/admin/orders"),
                Arguments.of(Caller.ADMIN, HttpMethod.GET, "/api/v1/orders"));
    }

    @ParameterizedTest(name = "{0} {1} {2} -> 200")
    @MethodSource("allowedCalls")
    void callers_with_the_required_role_are_allowed(Caller caller, HttpMethod method, String path) throws Exception {
        mvc.perform(request(method, path).with(caller.authentication())).andExpect(status().isOk());
    }

    @Test
    void unauthenticated_responses_tell_the_client_to_use_a_bearer_token() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void a_request_id_sent_by_the_caller_is_propagated() throws Exception {
        String requestId = "support-ticket-" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(request(HttpMethod.GET, "/api/v1/products/" + ANY_ID).header("X-Request-Id", requestId))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }
}

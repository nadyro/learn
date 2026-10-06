package com.kestrel.commerce.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void reuses_a_valid_request_id_from_the_caller_and_exposes_it_to_the_logs() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInMdc = new AtomicReference<>();

        filter.doFilter(request, response, chainCapturing(seenInMdc));

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("abc-123");
        assertThat(seenInMdc).hasValue("abc-123");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).as("MDC is cleaned up").isNull();
    }

    @Test
    void replaces_a_request_id_that_could_be_used_for_log_injection() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "abc\nFAKE LOG LINE");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainCapturing(new AtomicReference<>()));

        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .doesNotContain("FAKE")
                .hasSize(36);
    }

    @Test
    void generates_a_request_id_when_none_is_provided() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, chainCapturing(new AtomicReference<>()));

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotBlank();
    }

    private static FilterChain chainCapturing(AtomicReference<String> requestId) {
        return (request, response) -> requestId.set(MDC.get(RequestIdFilter.MDC_KEY));
    }
}

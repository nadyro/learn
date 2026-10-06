package com.kestrel.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.kestrel.commerce.support.IntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Operational endpoints used by Kubernetes probes and Prometheus. */
class ActuatorIT extends IntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int serverPort;

    @LocalManagementPort
    int managementPort;

    @Test
    void liveness_and_readiness_probes_are_up() throws Exception {
        assertThat(getFromManagementPort("/actuator/health/liveness").body()).contains("\"status\":\"UP\"");
        assertThat(getFromManagementPort("/actuator/health/readiness").body()).contains("\"status\":\"UP\"");
    }

    @Test
    void prometheus_metrics_include_our_business_metrics() throws Exception {
        String metrics = getFromManagementPort("/actuator/prometheus").body();

        assertThat(metrics)
                .contains("commerce_outbox_events_pending")
                .contains("hikaricp_connections_active")
                .contains("application=\"commerce-service\"");
    }

    @Test
    void actuator_is_not_exposed_on_the_public_port() throws Exception {
        HttpResponse<String> response = get(serverPort, "/actuator/health");

        assertThat(response.statusCode()).isNotEqualTo(200);
    }

    private HttpResponse<String> getFromManagementPort(String path) throws Exception {
        HttpResponse<String> response = get(managementPort, path);
        assertThat(response.statusCode()).as("GET %s", path).isEqualTo(200);
        return response;
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

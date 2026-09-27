package com.acadl.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que o gateway sobe e encaminha cada contexto para o microsserviço certo
 * (resolvido pelo Eureka via lb://). O Eureka é desligado no teste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "management.tracing.enabled=false"
})
class GatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @LocalServerPort
    private int port;

    @Test
    void rotasApontamParaOsMicrosservicosCertos() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).extracting(Route::getId)
                .contains("finora-auth", "finora-transactions", "reports");
        assertThat(routes).filteredOn(r -> r.getId().startsWith("finora"))
                .allSatisfy(r -> assertThat(r.getUri().toString()).isEqualTo("lb://finora"));
        assertThat(routes).filteredOn(r -> r.getId().equals("reports"))
                .allSatisfy(r -> assertThat(r.getUri().toString()).isEqualTo("lb://reports-service"));
    }

    @Test
    void healthCheckDoGatewayResponde() {
        WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .get().uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}

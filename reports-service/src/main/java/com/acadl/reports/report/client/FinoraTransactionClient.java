package com.acadl.reports.report.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * Cliente declarativo (OpenFeign) do microsserviço finora.
 * O nome "finora" é resolvido pelo Eureka (server-service) + Spring Cloud LoadBalancer,
 * então não há host/porta fixos aqui.
 */
@FeignClient(name = "finora")
public interface FinoraTransactionClient {

    @GetMapping("/transactions")
    List<FinoraTransactionResponse> listTransactions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    );
}

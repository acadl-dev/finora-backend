package com.acadl.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Service Discovery (Eureka Server).
 * <p>
 * Todos os microsserviços (finora, reports-service, gateway-service) se registram
 * aqui e se encontram pelo nome lógico, sem depender de host/porta fixos.
 * Painel: http://localhost:8761
 */
@SpringBootApplication
@EnableEurekaServer
public class ServerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServerServiceApplication.class, args);
	}
}

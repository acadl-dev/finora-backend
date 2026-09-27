package com.acadl.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway: ponto único de entrada do front-end.
 * <p>
 * Encaminha cada requisição para o microsserviço responsável, descobrindo o
 * endereço dele no Eureka (server-service). As rotas estão em application.yaml.
 */
@SpringBootApplication
public class GatewayServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayServiceApplication.class, args);
	}
}

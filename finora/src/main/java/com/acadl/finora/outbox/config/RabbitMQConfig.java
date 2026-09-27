package com.acadl.finora.outbox.config;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Topologia do lado PRODUTOR.
 * <p>
 * O finora só conhece o exchange onde publica seus eventos. Ele não declara nem
 * conhece as filas dos consumidores: cada consumidor (ex.: reports-service) cria a
 * própria fila e faz o binding com as chaves que lhe interessam (publish/subscribe).
 */
@Configuration
@EnableScheduling
public class RabbitMQConfig {

    /** Exchange de tópicos com os eventos do contexto de Transações. */
    public static final String TRANSACTIONS_EXCHANGE = "finora.transactions";

    @Bean
    public TopicExchange transactionsExchange() {
        return ExchangeBuilder.topicExchange(TRANSACTIONS_EXCHANGE).durable(true).build();
    }
}

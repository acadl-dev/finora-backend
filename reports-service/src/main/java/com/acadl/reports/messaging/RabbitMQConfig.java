package com.acadl.reports.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia RabbitMQ do lado CONSUMIDOR (reports-service).
 *
 * <pre>
 *  [finora] --TransactionRegistered/Removed--> (finora.transactions, topic)
 *                                                  | binding "transaction.#"
 *                                                  v
 *                                    [reports.transaction-events] --falha--> (finora.dlx) --> [reports.transaction-events.dlq]
 *
 *  [POST /reports] --GenerateReportCommand--> (finora.reports.commands, direct)
 *                                                  | binding "report.generate"
 *                                                  v
 *                                    [reports.generate-report] (workers concorrentes) --falha--> (finora.dlx) --> [reports.generate-report.dlq]
 * </pre>
 *
 * O Spring Boot (RabbitAdmin) declara automaticamente todos os beans abaixo ao conectar.
 */
@Configuration
public class RabbitMQConfig {

    // Eventos publicados pelo finora (publish/subscribe)
    public static final String TRANSACTIONS_EXCHANGE = "finora.transactions";
    public static final String TRANSACTION_EVENTS_QUEUE = "reports.transaction-events";
    public static final String TRANSACTION_EVENTS_BINDING = "transaction.#";

    // Comandos internos do reports-service (point-to-point / work queue)
    public static final String REPORTS_COMMANDS_EXCHANGE = "finora.reports.commands";
    public static final String GENERATE_REPORT_QUEUE = "reports.generate-report";
    public static final String GENERATE_REPORT_ROUTING_KEY = "report.generate";

    // Dead Letter Channel
    public static final String DEAD_LETTER_EXCHANGE = "finora.dlx";
    public static final String TRANSACTION_EVENTS_DLQ = TRANSACTION_EVENTS_QUEUE + ".dlq";
    public static final String GENERATE_REPORT_DLQ = GENERATE_REPORT_QUEUE + ".dlq";

    // ---- Exchanges ----

    @Bean
    public TopicExchange transactionsExchange() {
        return ExchangeBuilder.topicExchange(TRANSACTIONS_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange reportsCommandsExchange() {
        return ExchangeBuilder.directExchange(REPORTS_COMMANDS_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    // ---- Filas (duráveis, com dead-letter configurado) ----

    @Bean
    public Queue transactionEventsQueue() {
        return QueueBuilder.durable(TRANSACTION_EVENTS_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(TRANSACTION_EVENTS_DLQ)
                .build();
    }

    @Bean
    public Queue transactionEventsDlq() {
        return QueueBuilder.durable(TRANSACTION_EVENTS_DLQ).build();
    }

    @Bean
    public Queue generateReportQueue() {
        return QueueBuilder.durable(GENERATE_REPORT_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(GENERATE_REPORT_DLQ)
                .build();
    }

    @Bean
    public Queue generateReportDlq() {
        return QueueBuilder.durable(GENERATE_REPORT_DLQ).build();
    }

    // ---- Bindings ----

    @Bean
    public Binding transactionEventsBinding() {
        return BindingBuilder.bind(transactionEventsQueue()).to(transactionsExchange()).with(TRANSACTION_EVENTS_BINDING);
    }

    @Bean
    public Binding generateReportBinding() {
        return BindingBuilder.bind(generateReportQueue()).to(reportsCommandsExchange()).with(GENERATE_REPORT_ROUTING_KEY);
    }

    @Bean
    public Binding transactionEventsDlqBinding() {
        return BindingBuilder.bind(transactionEventsDlq()).to(deadLetterExchange()).with(TRANSACTION_EVENTS_DLQ);
    }

    @Bean
    public Binding generateReportDlqBinding() {
        return BindingBuilder.bind(generateReportDlq()).to(deadLetterExchange()).with(GENERATE_REPORT_DLQ);
    }

    /**
     * Mensagens em JSON. O tipo Java de destino é inferido pelo parâmetro do
     * {@code @RabbitListener}, então o contrato entre serviços é só o JSON — o
     * reports-service não depende de nenhuma classe do finora.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}

package com.acadl.finora.outbox.service;

import com.acadl.finora.outbox.model.OutboxEvent;
import com.acadl.finora.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Message Relay do padrão Transactional Outbox.
 * <p>
 * A cada {@code finora.outbox.relay-interval-ms}, publica no RabbitMQ os eventos
 * pendentes, em ordem. Um evento só é marcado como publicado depois que o broker
 * confirma o recebimento (publisher confirm) e a mensagem foi roteada para ao
 * menos uma fila (mandatory + returns). Se o RabbitMQ estiver fora do ar, nada se
 * perde: os eventos continuam na tabela e são enviados quando ele voltar.
 * <p>
 * Garantia: entrega "pelo menos uma vez" — por isso os consumidores são idempotentes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxTracing outboxTracing;
    private final MeterRegistry meterRegistry;

    @Value("${finora.outbox.confirm-timeout-ms:5000}")
    private long confirmTimeoutMs;

    @Scheduled(fixedDelayString = "${finora.outbox.relay-interval-ms:1000}")
    public void relayPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc();

        for (OutboxEvent event : pending) {
            try {
                // publica "dentro" do trace da requisição que gerou o evento
                outboxTracing.runWithin(event.getTraceHeaders(), "outbox publish " + event.getEventType(),
                        () -> publish(event));
                event.markPublished(Instant.now());
                meterRegistry.counter("finora.outbox.published", "event", event.getEventType()).increment();
                outboxEventRepository.save(event);
                log.debug("Evento {} ({}) publicado em {}", event.getId(), event.getEventType(), event.getRoutingKey());
            } catch (Exception e) {
                event.registerFailure(e.getMessage());
                outboxEventRepository.save(event);
                meterRegistry.counter("finora.outbox.publish.failures", "event", event.getEventType()).increment();
                if (event.getAttempts() == 1 || event.getAttempts() % 30 == 0) {
                    log.warn("Falha ao publicar o evento {} (tentativa {}): {}. Nova tentativa em instantes.",
                            event.getId(), event.getAttempts(), e.getMessage());
                }
                // Para no primeiro erro para não publicar eventos fora de ordem.
                break;
            }
        }
    }

    private void publish(OutboxEvent event) throws Exception {
        Message message = MessageBuilder
                .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.getId().toString())
                .setType(event.getEventType())
                .setTimestamp(Date.from(event.getOccurredAt()))
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader("eventType", event.getEventType())
                .setHeader("aggregateType", event.getAggregateType())
                .setHeader("aggregateId", event.getAggregateId().toString())
                .build();

        CorrelationData correlation = new CorrelationData(event.getId().toString());
        rabbitTemplate.send(event.getExchange(), event.getRoutingKey(), message, correlation);

        CorrelationData.Confirm confirm = correlation.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
        if (!confirm.isAck()) {
            throw new AmqpException("RabbitMQ recusou a mensagem (nack): " + confirm.getReason());
        }
        if (correlation.getReturned() != null) {
            throw new AmqpException("Nenhuma fila recebeu a mensagem (unroutable): "
                    + correlation.getReturned().getReplyText());
        }
    }
}

package com.acadl.finora.outbox.service;

import com.acadl.finora.outbox.config.RabbitMQConfig;
import com.acadl.finora.outbox.model.OutboxEvent;
import com.acadl.finora.outbox.repository.OutboxEventRepository;
import com.acadl.finora.shared.domain.DomainEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Grava eventos de domínio na outbox.
 * <p>
 * {@code Propagation.MANDATORY}: só pode ser chamado DENTRO de uma transação já aberta
 * (a do caso de uso). Se alguém chamar fora de uma transação, falha na hora — é o
 * que garante a atomicidade "agregado + evento".
 */
@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(List<? extends DomainEvent> events) {
        for (DomainEvent event : events) {
            outboxEventRepository.save(OutboxEvent.pending(
                    event.eventId(),
                    event.aggregateType(),
                    event.aggregateId(),
                    event.eventType(),
                    exchangeFor(event),
                    event.routingKey(),
                    toJson(event),
                    event.occurredAt()
            ));
        }
    }

    private String exchangeFor(DomainEvent event) {
        return switch (event.aggregateType()) {
            case "Transaction" -> RabbitMQConfig.TRANSACTIONS_EXCHANGE;
            default -> throw new IllegalArgumentException("Sem exchange para o agregado " + event.aggregateType());
        };
    }

    private String toJson(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar o evento " + event.eventType(), e);
        }
    }
}

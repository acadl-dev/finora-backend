package com.acadl.finora.transaction.event;

import com.acadl.finora.shared.domain.DomainEvent;
import com.acadl.finora.transaction.model.Transaction;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento de domínio: uma receita ou despesa foi registrada.
 * <p>
 * Padrão "Event-Carried State Transfer": o evento leva todo o estado necessário
 * para os consumidores (ex.: reports-service) manterem sua própria cópia, sem
 * precisar chamar o finora de volta.
 */
public record TransactionRegistered(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID transactionId,
        UUID userId,
        String userEmail,
        String description,
        BigDecimal amount,
        String type,
        String category,
        LocalDate date
) implements DomainEvent {

    public static final String TYPE = "TransactionRegistered";
    public static final String ROUTING_KEY = "transaction.registered";
    public static final int VERSION = 1;

    public static TransactionRegistered of(Transaction transaction, String userEmail) {
        return new TransactionRegistered(
                UUID.randomUUID(),
                TYPE,
                VERSION,
                Instant.now(),
                transaction.getId(),
                transaction.getUser().getId(),
                userEmail,
                transaction.getDescription(),
                transaction.getAmount().value(),
                transaction.getType().name(),
                transaction.getCategory(),
                transaction.getDate()
        );
    }

    @Override
    @JsonIgnore
    public String aggregateType() {
        return "Transaction";
    }

    @Override
    @JsonIgnore
    public UUID aggregateId() {
        return transactionId;
    }

    @Override
    @JsonIgnore
    public String routingKey() {
        return ROUTING_KEY;
    }
}

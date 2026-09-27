package com.acadl.finora.transaction.event;

import com.acadl.finora.shared.domain.DomainEvent;
import com.acadl.finora.transaction.model.Transaction;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.UUID;

/** Evento de domínio: uma transação foi excluída pelo seu dono. */
public record TransactionRemoved(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID transactionId,
        UUID userId,
        String userEmail
) implements DomainEvent {

    public static final String TYPE = "TransactionRemoved";
    public static final String ROUTING_KEY = "transaction.removed";
    public static final int VERSION = 1;

    public static TransactionRemoved of(Transaction transaction, String userEmail) {
        return new TransactionRemoved(
                UUID.randomUUID(),
                TYPE,
                VERSION,
                Instant.now(),
                transaction.getId(),
                transaction.getUser().getId(),
                userEmail
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

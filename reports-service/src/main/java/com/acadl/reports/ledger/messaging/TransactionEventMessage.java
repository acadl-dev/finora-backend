package com.acadl.reports.ledger.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contrato (JSON) dos eventos publicados pelo finora no exchange finora.transactions.
 * Um único formato cobre os tipos de evento; o campo {@code eventType} diz qual é:
 * <ul>
 *   <li>TransactionRegistered: todos os campos preenchidos;</li>
 *   <li>TransactionRemoved: apenas ids e e-mail do dono.</li>
 * </ul>
 */
public record TransactionEventMessage(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        Instant occurredAt,
        UUID transactionId,
        UUID userId,
        String userEmail,
        String description,
        BigDecimal amount,
        String type,
        String category,
        LocalDate date
) {
    public static final String REGISTERED = "TransactionRegistered";
    public static final String REMOVED = "TransactionRemoved";
}

package com.acadl.reports.report.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Contrato publicado pelo finora em GET /transactions (linguagem do outro contexto). */
public record FinoraTransactionResponse(
        UUID id,
        String description,
        BigDecimal amount,
        BigDecimal signedAmount,
        String type,
        String category,
        LocalDate date,
        Instant createdAt
) {}

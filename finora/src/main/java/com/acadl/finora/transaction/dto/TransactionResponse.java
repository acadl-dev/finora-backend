package com.acadl.finora.transaction.dto;

import com.acadl.finora.transaction.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String description,
        BigDecimal amount,
        BigDecimal signedAmount,
        TransactionType type,
        String category,
        LocalDate date,
        Instant createdAt
) {}

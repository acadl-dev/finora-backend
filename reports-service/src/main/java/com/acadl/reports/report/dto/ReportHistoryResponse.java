package com.acadl.reports.report.dto;

import com.acadl.reports.report.model.ReportFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReportHistoryResponse(
        UUID id,
        String fileName,
        ReportFormat format,
        LocalDate periodStart,
        LocalDate periodEnd,
        String periodDescription,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal balance,
        int entryCount,
        Instant generatedAt
) {}

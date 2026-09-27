package com.acadl.reports.report.dto;

import com.acadl.reports.report.model.ReportFormat;
import com.acadl.reports.report.model.ReportStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        ReportStatus status,
        String fileName,
        ReportFormat format,
        LocalDate periodStart,
        LocalDate periodEnd,
        String periodDescription,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal balance,
        Integer entryCount,
        String failureReason,
        Instant requestedAt,
        Instant completedAt
) {}

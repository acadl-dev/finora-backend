package com.acadl.reports.report.service;

import com.acadl.reports.report.exception.ReportGenerationException;
import com.acadl.reports.report.model.*;
import com.acadl.reports.report.repository.FinancialReportRepository;
import com.acadl.reports.report.repository.ReportContentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Serviço de aplicação executado pelo worker (consumidor da fila de comandos).
 * <p>
 * Idempotente: se o comando chegar duplicado e o relatório já estiver READY ou
 * FAILED, nada acontece.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final FinancialReportRepository reportRepository;
    private final ReportContentRepository contentRepository;
    private final TransactionSource transactionSource;
    private final BalanceCalculator balanceCalculator;
    private final ReportExporter reportExporter;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void generate(UUID reportId) {
        FinancialReport report = reportRepository.findById(reportId).orElse(null);
        if (report == null) {
            log.warn("Comando para relatório inexistente {}; descartado", reportId);
            return;
        }
        if (!report.isPending()) {
            log.info("Relatório {} já está {}; comando duplicado ignorado", reportId, report.getStatus());
            return;
        }

        try {
            List<ReportEntry> entries = transactionSource.fetchEntries(report.getOwnerEmail());
            report.complete(entries, balanceCalculator);
            ReportFile file = reportExporter.export(report);
            contentRepository.save(ReportContent.of(report.getId(), file));
            log.info("Relatório {} gerado: {} lançamento(s)", reportId, report.getSummary().entryCount());
            meterRegistry.counter("finora.reports.completed", "status", "READY").increment();
        } catch (ReportGenerationException e) {
            // erro de negócio: tentar de novo não resolve -> falha definitiva
            log.warn("Relatório {} falhou: {}", reportId, e.getMessage());
            report.fail(e.getMessage());
            meterRegistry.counter("finora.reports.completed", "status", "FAILED").increment();
        }
    }

    @Transactional
    public void markFailed(UUID reportId, String reason) {
        reportRepository.findById(reportId)
                .filter(FinancialReport::isPending)
                .ifPresent(report -> report.fail(reason));
    }
}

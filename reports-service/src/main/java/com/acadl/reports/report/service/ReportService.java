package com.acadl.reports.report.service;

import com.acadl.reports.report.dto.ReportHistoryResponse;
import com.acadl.reports.report.mapper.FinancialReportMapper;
import com.acadl.reports.report.model.*;
import com.acadl.reports.report.repository.FinancialReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Serviço de aplicação: orquestra o caso de uso "extrair relatório".
 * 1) busca os lançamentos no finora (porta TransactionSource);
 * 2) o agregado FinancialReport aplica o período e usa o BalanceCalculator;
 * 3) o exportador gera o Excel;
 * 4) o histórico é salvo na base própria do serviço.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final TransactionSource transactionSource;
    private final BalanceCalculator balanceCalculator;
    private final ReportExporter reportExporter;
    private final FinancialReportRepository reportRepository;

    public ReportFile generateTransactionsReport(String ownerEmail, String authorizationHeader,
                                                 LocalDate start, LocalDate end) {
        ReportPeriod period = ReportPeriod.of(start, end);
        List<ReportEntry> entries = transactionSource.fetchEntries(authorizationHeader);

        FinancialReport report = FinancialReport.generate(
                ownerEmail, period, entries, balanceCalculator, reportExporter.format());

        ReportFile file = reportExporter.export(report);
        reportRepository.save(report);
        return file;
    }

    @Transactional(readOnly = true)
    public List<ReportHistoryResponse> listHistory(String ownerEmail) {
        return reportRepository.findTop20ByOwnerEmailOrderByGeneratedAtDesc(ownerEmail)
                .stream()
                .map(FinancialReportMapper::toDTO)
                .toList();
    }
}

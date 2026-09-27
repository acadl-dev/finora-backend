package com.acadl.reports.report.service;

import com.acadl.reports.report.dto.ReportResponse;
import com.acadl.reports.report.exception.ReportNotFoundException;
import com.acadl.reports.report.exception.ReportNotReadyException;
import com.acadl.reports.report.mapper.FinancialReportMapper;
import com.acadl.reports.report.model.*;
import com.acadl.reports.report.repository.FinancialReportRepository;
import com.acadl.reports.report.repository.ReportContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Serviço de aplicação do lado HTTP.
 * <p>
 * Padrão Request-Reply assíncrono: {@link #requestTransactionsReport} só registra o
 * pedido e responde na hora (202 Accepted). A geração acontece no worker; o cliente
 * consulta o status em GET /reports/{id} e baixa em GET /reports/{id}/file.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final FinancialReportRepository reportRepository;
    private final ReportContentRepository contentRepository;
    private final ReportExporter reportExporter;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReportResponse requestTransactionsReport(String ownerEmail, LocalDate start, LocalDate end) {
        FinancialReport report = FinancialReport.request(ownerEmail, ReportPeriod.of(start, end), reportExporter.format());
        FinancialReport saved = reportRepository.save(report);
        // publicado no RabbitMQ somente após o commit (ReportCommandPublisher)
        eventPublisher.publishEvent(new ReportRequested(saved.getId()));
        return FinancialReportMapper.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public ReportResponse find(UUID reportId, String ownerEmail) {
        return FinancialReportMapper.toDTO(loadOwned(reportId, ownerEmail));
    }

    @Transactional(readOnly = true)
    public ReportFile download(UUID reportId, String ownerEmail) {
        FinancialReport report = loadOwned(reportId, ownerEmail);
        if (!report.isReady()) {
            throw new ReportNotReadyException(report.getStatus().name());
        }
        ReportContent content = contentRepository.findById(reportId)
                .orElseThrow(ReportNotFoundException::new);
        return new ReportFile(report.getFileName(), content.getContentType(), content.getContent());
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> listHistory(String ownerEmail) {
        return reportRepository.findTop20ByOwnerEmailOrderByRequestedAtDesc(ownerEmail)
                .stream()
                .map(FinancialReportMapper::toDTO)
                .toList();
    }

    private FinancialReport loadOwned(UUID reportId, String ownerEmail) {
        return reportRepository.findById(reportId)
                .filter(report -> report.belongsTo(ownerEmail))
                .orElseThrow(ReportNotFoundException::new);
    }
}

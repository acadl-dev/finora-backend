package com.acadl.reports.report.service;

import com.acadl.reports.report.messaging.ReportCommandPublisher;
import com.acadl.reports.report.model.FinancialReport;
import com.acadl.reports.report.model.ReportStatus;
import com.acadl.reports.report.repository.FinancialReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Rede de segurança: se o comando de um relatório se perdeu (ex.: RabbitMQ fora do
 * ar no momento do pedido), ele é reenviado. Depois do tempo limite, o relatório é
 * marcado como FAILED. Duplicatas são inofensivas: o worker é idempotente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleReportDispatcher {

    private final FinancialReportRepository reportRepository;
    private final ReportCommandPublisher commandPublisher;
    private final ReportGenerationService generationService;

    @Value("${reports.dispatcher.stale-after-seconds:20}")
    private long staleAfterSeconds;

    @Value("${reports.dispatcher.timeout-minutes:10}")
    private long timeoutMinutes;

    @Scheduled(fixedDelayString = "${reports.dispatcher.interval-ms:30000}")
    public void redispatchStaleRequests() {
        Instant now = Instant.now();
        Instant staleBefore = now.minusSeconds(staleAfterSeconds);
        Instant timeoutBefore = now.minus(Duration.ofMinutes(timeoutMinutes));

        for (FinancialReport report : reportRepository
                .findTop50ByStatusAndRequestedAtBeforeOrderByRequestedAtAsc(ReportStatus.REQUESTED, staleBefore)) {
            if (report.getRequestedAt().isBefore(timeoutBefore)) {
                generationService.markFailed(report.getId(), "Tempo limite excedido para gerar o relatório.");
            } else {
                log.info("Reenviando comando do relatório {} (parado em REQUESTED)", report.getId());
                commandPublisher.dispatch(report.getId());
            }
        }
    }
}

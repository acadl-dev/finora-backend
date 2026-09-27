package com.acadl.reports.report.repository;

import com.acadl.reports.report.model.FinancialReport;
import com.acadl.reports.report.model.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Repositório dedicado do reports-service (base finora_reports). */
public interface FinancialReportRepository extends JpaRepository<FinancialReport, UUID> {

    List<FinancialReport> findTop20ByOwnerEmailOrderByRequestedAtDesc(String ownerEmail);

    /** Relatórios "parados" (comando perdido) para o dispatcher reenviar. */
    List<FinancialReport> findTop50ByStatusAndRequestedAtBeforeOrderByRequestedAtAsc(ReportStatus status, Instant before);
}

package com.acadl.reports.report.repository;

import com.acadl.reports.report.model.FinancialReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Repositório dedicado do reports-service (base finora_reports). */
public interface FinancialReportRepository extends JpaRepository<FinancialReport, UUID> {

    List<FinancialReport> findTop20ByOwnerEmailOrderByGeneratedAtDesc(String ownerEmail);
}

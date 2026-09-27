package com.acadl.reports.report.repository;

import com.acadl.reports.report.model.ReportContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportContentRepository extends JpaRepository<ReportContent, UUID> {
}

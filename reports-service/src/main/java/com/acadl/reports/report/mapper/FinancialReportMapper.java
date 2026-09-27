package com.acadl.reports.report.mapper;

import com.acadl.reports.report.dto.ReportResponse;
import com.acadl.reports.report.model.FinancialReport;
import com.acadl.reports.report.model.ReportSummary;

public class FinancialReportMapper {

    public static ReportResponse toDTO(FinancialReport report) {
        ReportSummary summary = report.getSummary();
        return new ReportResponse(
                report.getId(),
                report.getStatus(),
                report.getFileName(),
                report.getFormat(),
                report.getPeriod().start(),
                report.getPeriod().end(),
                report.getPeriod().describe(),
                summary == null ? null : summary.totalIncome(),
                summary == null ? null : summary.totalExpense(),
                summary == null ? null : summary.balance(),
                summary == null ? null : summary.entryCount(),
                report.getFailureReason(),
                report.getRequestedAt(),
                report.getCompletedAt()
        );
    }
}

package com.acadl.reports.report.mapper;

import com.acadl.reports.report.dto.ReportHistoryResponse;
import com.acadl.reports.report.model.FinancialReport;

public class FinancialReportMapper {

    public static ReportHistoryResponse toDTO(FinancialReport report) {
        return new ReportHistoryResponse(
                report.getId(),
                report.getFileName(),
                report.getFormat(),
                report.getPeriod().start(),
                report.getPeriod().end(),
                report.getPeriod().describe(),
                report.getSummary().totalIncome(),
                report.getSummary().totalExpense(),
                report.getSummary().balance(),
                report.getSummary().entryCount(),
                report.getGeneratedAt()
        );
    }
}

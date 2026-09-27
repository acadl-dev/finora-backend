package com.acadl.reports.report.model;

/** Porta de saída: transforma um relatório gerado em arquivo (ex.: Excel). */
public interface ReportExporter {

    ReportFormat format();

    ReportFile export(FinancialReport report);
}

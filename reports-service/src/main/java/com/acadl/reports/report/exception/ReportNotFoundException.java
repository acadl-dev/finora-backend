package com.acadl.reports.report.exception;

public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException() {
        super("Relatório não encontrado");
    }
}

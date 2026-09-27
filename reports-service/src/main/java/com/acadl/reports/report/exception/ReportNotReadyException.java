package com.acadl.reports.report.exception;

public class ReportNotReadyException extends RuntimeException {
    public ReportNotReadyException(String status) {
        super("O relatório ainda não está pronto (status: " + status + ")");
    }
}

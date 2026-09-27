package com.acadl.reports.report.model;

/** Value Object: o arquivo gerado, pronto para download. */
public record ReportFile(String fileName, String contentType, byte[] content) {
}

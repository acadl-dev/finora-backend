package com.acadl.reports.report.model;

import java.util.UUID;

/**
 * Evento de domínio interno: um relatório foi solicitado. Após o commit no banco,
 * ele é convertido no comando GenerateReportCommand e enviado ao RabbitMQ.
 */
public record ReportRequested(UUID reportId) {
}

package com.acadl.reports.report.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensagem de COMANDO (imperativo: "gere este relatório"), ponto a ponto.
 * Diferente de um evento, tem um único destinatário: o worker de relatórios.
 * Leva só o id — o estado está no banco (padrão "Claim Check").
 */
public record GenerateReportCommand(UUID commandId, UUID reportId, Instant issuedAt) {

    public static GenerateReportCommand of(UUID reportId) {
        return new GenerateReportCommand(UUID.randomUUID(), reportId, Instant.now());
    }
}

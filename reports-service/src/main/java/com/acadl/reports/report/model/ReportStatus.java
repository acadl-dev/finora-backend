package com.acadl.reports.report.model;

/** Ciclo de vida de um relatório gerado de forma assíncrona. */
public enum ReportStatus {
    /** Pedido aceito; comando enviado para a fila de geração. */
    REQUESTED,
    /** Arquivo gerado e disponível para download. */
    READY,
    /** Não foi possível gerar (erro de negócio ou tentativas esgotadas). */
    FAILED
}

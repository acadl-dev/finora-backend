package com.acadl.reports.report.model;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * Conteúdo binário (xlsx) de um relatório pronto, guardado na base própria do
 * reports-service para ser baixado depois (o worker gera; o usuário baixa quando quiser).
 * Fica numa tabela separada para o histórico não carregar os bytes.
 */
@Entity
@Table(name = "report_contents")
public class ReportContent {

    @Id
    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(nullable = false)
    private byte[] content;

    protected ReportContent() {
        // exigido pelo JPA
    }

    public static ReportContent of(UUID reportId, ReportFile file) {
        ReportContent content = new ReportContent();
        content.reportId = reportId;
        content.contentType = file.contentType();
        content.content = file.content();
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content;
    }
}

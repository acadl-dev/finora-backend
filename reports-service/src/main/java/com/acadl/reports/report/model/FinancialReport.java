package com.acadl.reports.report.model;

import com.acadl.reports.report.exception.ReportGenerationException;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root do contexto de Relatórios.
 * <p>
 * Na arquitetura orientada a eventos o relatório tem ciclo de vida:
 * <pre>
 *   request() ──► REQUESTED ──complete()──► READY
 *                     │
 *                     └──────fail()───────► FAILED
 * </pre>
 * O pedido é aceito na hora (HTTP 202) e a geração acontece depois, num worker que
 * consome a fila {@code reports.generate-report}.
 */
@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_owner", columnList = "owner_email, requested_at"),
        @Index(name = "idx_reports_status", columnList = "status, requested_at")
})
public class FinancialReport {

    private static final int MAX_REASON_LENGTH = 500;
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Controle de concorrência otimista: dois workers não concluem o mesmo relatório. */
    @Version
    private Long version;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Embedded
    private ReportPeriod period;

    @Embedded
    private ReportSummary summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "failure_reason", length = MAX_REASON_LENGTH)
    private String failureReason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Transient
    private List<ReportEntry> entries = List.of();

    protected FinancialReport() {
        // exigido pelo JPA
    }

    /** Fábrica: registra o pedido de um relatório (ainda sem conteúdo). */
    public static FinancialReport request(String ownerEmail, ReportPeriod period, ReportFormat format) {
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new ReportGenerationException("Relatório precisa pertencer a um usuário");
        }
        if (format == null) {
            throw new ReportGenerationException("Formato do relatório é obrigatório");
        }
        FinancialReport report = new FinancialReport();
        report.ownerEmail = ownerEmail;
        report.period = period == null ? ReportPeriod.allTime() : period;
        report.format = format;
        report.status = ReportStatus.REQUESTED;
        report.requestedAt = Instant.now();
        report.fileName = "relatorio-financeiro-" + FILE_TIMESTAMP.format(report.requestedAt) + "." + format.extension();
        return report;
    }

    /**
     * Conclui o relatório: só entram lançamentos do período, ordenados por data, e o
     * saldo é calculado pelo serviço de domínio {@link BalanceCalculator}.
     */
    public void complete(List<ReportEntry> allEntries, BalanceCalculator calculator) {
        if (status != ReportStatus.REQUESTED) {
            throw new ReportGenerationException("Relatório " + id + " não está aguardando geração (" + status + ")");
        }
        final ReportPeriod reportPeriod = getPeriod();
        this.entries = (allEntries == null ? List.<ReportEntry>of() : allEntries).stream()
                .filter(entry -> reportPeriod.contains(entry.date()))
                .sorted(Comparator.comparing(ReportEntry::date))
                .toList();
        this.summary = calculator.calculate(this.entries);
        this.status = ReportStatus.READY;
        this.completedAt = Instant.now();
    }

    /** Marca a falha (com o motivo). Um relatório já com falha não muda mais. */
    public void fail(String reason) {
        if (status == ReportStatus.FAILED) {
            return;
        }
        String text = reason == null || reason.isBlank() ? "Falha ao gerar o relatório" : reason;
        this.failureReason = text.length() > MAX_REASON_LENGTH ? text.substring(0, MAX_REASON_LENGTH) : text;
        this.status = ReportStatus.FAILED;
        this.completedAt = Instant.now();
    }

    public boolean isPending() {
        return status == ReportStatus.REQUESTED;
    }

    public boolean isReady() {
        return status == ReportStatus.READY;
    }

    public boolean belongsTo(String email) {
        return ownerEmail != null && ownerEmail.equalsIgnoreCase(email);
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    /** Quando início e fim são nulos o JPA devolve o embeddable nulo; tratamos como "todo o período". */
    public ReportPeriod getPeriod() {
        return period == null ? ReportPeriod.allTime() : period;
    }

    /** Nulo enquanto o relatório não foi concluído. */
    public ReportSummary getSummary() {
        return summary;
    }

    public ReportFormat getFormat() {
        return format;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<ReportEntry> getEntries() {
        return entries == null ? List.of() : entries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FinancialReport other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

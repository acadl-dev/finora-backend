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
 * Representa um relatório financeiro gerado para um usuário. O que é persistido
 * (na base própria do reports-service) é o histórico: dono, período, totais e saldo.
 * Os lançamentos usados na geração ficam disponíveis apenas em memória, para a
 * exportação do arquivo — não duplicamos as transações do finora.
 */
@Entity
@Table(name = "financial_reports")
public class FinancialReport {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Embedded
    private ReportPeriod period;

    @Embedded
    private ReportSummary summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportFormat format;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Transient
    private List<ReportEntry> entries = List.of();

    protected FinancialReport() {
        // exigido pelo JPA
    }

    /**
     * Fábrica: gera o relatório de um usuário para um período, aplicando as regras:
     * só entram lançamentos do período, ordenados por data, e o saldo é calculado
     * pelo serviço de domínio {@link BalanceCalculator}.
     */
    public static FinancialReport generate(String ownerEmail, ReportPeriod period, List<ReportEntry> allEntries,
                                           BalanceCalculator calculator, ReportFormat format) {
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new ReportGenerationException("Relatório precisa pertencer a um usuário");
        }
        if (period == null) period = ReportPeriod.allTime();
        if (format == null) throw new ReportGenerationException("Formato do relatório é obrigatório");

        final ReportPeriod reportPeriod = period;
        List<ReportEntry> entriesInPeriod = (allEntries == null ? List.<ReportEntry>of() : allEntries).stream()
                .filter(entry -> reportPeriod.contains(entry.date()))
                .sorted(Comparator.comparing(ReportEntry::date))
                .toList();

        FinancialReport report = new FinancialReport();
        report.ownerEmail = ownerEmail;
        report.period = reportPeriod;
        report.summary = calculator.calculate(entriesInPeriod);
        report.format = format;
        report.generatedAt = Instant.now();
        report.fileName = "relatorio-financeiro-" + FILE_TIMESTAMP.format(report.generatedAt) + "." + format.extension();
        report.entries = entriesInPeriod;
        return report;
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

    public ReportSummary getSummary() {
        return summary;
    }

    public ReportFormat getFormat() {
        return format;
    }

    public String getFileName() {
        return fileName;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
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

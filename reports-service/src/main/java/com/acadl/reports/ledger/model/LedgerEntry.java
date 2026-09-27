package com.acadl.reports.ledger.model;

import com.acadl.reports.report.model.EntryType;
import com.acadl.reports.report.model.ReportEntry;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Projeção (read model) de uma transação do finora dentro da base do reports-service.
 * <p>
 * É construída exclusivamente a partir dos eventos recebidos (CQRS): o
 * reports-service não escreve transações, só mantém a cópia de que precisa para
 * gerar relatórios sem depender do finora estar no ar.
 * <p>
 * Exclusões viram "lápides" ({@code removed = true}) em vez de apagar a linha: se
 * um TransactionRemoved chegar antes do TransactionRegistered (reentrega fora de
 * ordem), a lápide impede que a transação "ressuscite".
 */
@Entity
@Table(name = "ledger_entries", indexes = @Index(name = "idx_ledger_owner", columnList = "owner_email, removed"))
public class LedgerEntry {

    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    private String description;

    private String category;

    @Enumerated(EnumType.STRING)
    private EntryType type;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "entry_date")
    private LocalDate date;

    @Column(nullable = false)
    private boolean removed;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    protected LedgerEntry() {
        // exigido pelo JPA
    }

    public static LedgerEntry registered(UUID transactionId, UUID ownerId, String ownerEmail, String description,
                                         String category, EntryType type, BigDecimal amount, LocalDate date,
                                         Instant occurredAt) {
        LedgerEntry entry = new LedgerEntry();
        entry.transactionId = transactionId;
        entry.ownerId = ownerId;
        entry.ownerEmail = ownerEmail;
        entry.description = description;
        entry.category = category;
        entry.type = type;
        entry.amount = amount;
        entry.date = date;
        entry.removed = false;
        entry.lastEventAt = occurredAt;
        return entry;
    }

    /** Lápide para uma exclusão que chegou antes do registro. */
    public static LedgerEntry tombstone(UUID transactionId, UUID ownerId, String ownerEmail, Instant occurredAt) {
        LedgerEntry entry = new LedgerEntry();
        entry.transactionId = transactionId;
        entry.ownerId = ownerId;
        entry.ownerEmail = ownerEmail;
        entry.removed = true;
        entry.lastEventAt = occurredAt;
        return entry;
    }

    public void markRemoved(Instant occurredAt) {
        this.removed = true;
        this.lastEventAt = occurredAt;
    }

    public boolean isRemoved() {
        return removed;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    /** Tradução para o Value Object do domínio de relatórios. */
    public ReportEntry toReportEntry() {
        return new ReportEntry(date, description, category, type, amount);
    }
}

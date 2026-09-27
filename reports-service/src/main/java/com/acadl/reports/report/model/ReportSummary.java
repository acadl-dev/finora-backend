package com.acadl.reports.report.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object: consolidação financeira do relatório.
 * O saldo é sempre derivado: saldo = total de receitas − total de despesas.
 */
@Embeddable
public class ReportSummary {

    @Column(name = "total_income", precision = 15, scale = 2)
    private BigDecimal totalIncome;

    @Column(name = "total_expense", precision = 15, scale = 2)
    private BigDecimal totalExpense;

    @Column(name = "balance", precision = 15, scale = 2)
    private BigDecimal balance;

    // colunas anuláveis: enquanto o relatório está REQUESTED ainda não há resumo
    @Column(name = "entry_count")
    private Integer entryCount;

    protected ReportSummary() {
        // exigido pelo JPA
    }

    private ReportSummary(BigDecimal totalIncome, BigDecimal totalExpense, int entryCount) {
        this.totalIncome = totalIncome.setScale(2, RoundingMode.HALF_EVEN);
        this.totalExpense = totalExpense.setScale(2, RoundingMode.HALF_EVEN);
        this.balance = this.totalIncome.subtract(this.totalExpense);
        this.entryCount = entryCount;
    }

    public static ReportSummary of(BigDecimal totalIncome, BigDecimal totalExpense, int entryCount) {
        Objects.requireNonNull(totalIncome, "totalIncome");
        Objects.requireNonNull(totalExpense, "totalExpense");
        return new ReportSummary(totalIncome, totalExpense, entryCount);
    }

    public BigDecimal totalIncome() {
        return totalIncome;
    }

    public BigDecimal totalExpense() {
        return totalExpense;
    }

    public BigDecimal balance() {
        return balance;
    }

    public int entryCount() {
        return entryCount == null ? 0 : entryCount;
    }

    public boolean isPositive() {
        return balance.signum() >= 0;
    }
}

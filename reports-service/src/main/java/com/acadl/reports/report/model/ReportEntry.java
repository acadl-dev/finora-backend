package com.acadl.reports.report.model;

import com.acadl.reports.report.exception.ReportGenerationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Value Object: um lançamento (receita ou despesa) como o contexto de Relatórios o enxerga.
 * <p>
 * É a tradução (Anti-Corruption Layer) da transação que vem do finora; o domínio de
 * relatórios não depende do modelo do outro microsserviço.
 */
public record ReportEntry(
        LocalDate date,
        String description,
        String category,
        EntryType type,
        BigDecimal amount
) {
    public ReportEntry {
        if (date == null) throw new ReportGenerationException("Lançamento sem data");
        if (type == null) throw new ReportGenerationException("Lançamento sem tipo");
        if (amount == null || amount.signum() < 0) {
            throw new ReportGenerationException("Valor do lançamento deve ser positivo");
        }
        amount = amount.setScale(2, RoundingMode.HALF_EVEN);
        description = description == null ? "" : description;
    }

    public boolean isIncome() {
        return type == EntryType.INCOME;
    }

    public boolean isExpense() {
        return type == EntryType.EXPENSE;
    }

    /** Positivo para receitas, negativo para despesas. */
    public BigDecimal signedAmount() {
        return type.applySignTo(amount);
    }
}

package com.acadl.reports.report.model;

import java.math.BigDecimal;

/** Natureza de um lançamento no relatório: receitas somam ao saldo, despesas subtraem. */
public enum EntryType {
    INCOME("Receita") {
        @Override
        public BigDecimal applySignTo(BigDecimal amount) {
            return amount;
        }
    },
    EXPENSE("Despesa") {
        @Override
        public BigDecimal applySignTo(BigDecimal amount) {
            return amount.negate();
        }
    };

    private final String label;

    EntryType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public abstract BigDecimal applySignTo(BigDecimal amount);
}

package com.acadl.finora.transaction.model;

import java.math.BigDecimal;

/**
 * Natureza da transação. Cada tipo sabe qual é o efeito que causa no saldo:
 * receitas somam, despesas subtraem.
 */
public enum TransactionType {
    INCOME {
        @Override
        public BigDecimal applySignTo(BigDecimal amount) {
            return amount;
        }
    },
    EXPENSE {
        @Override
        public BigDecimal applySignTo(BigDecimal amount) {
            return amount.negate();
        }
    };

    public abstract BigDecimal applySignTo(BigDecimal amount);
}

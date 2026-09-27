package com.acadl.finora.transaction.model;

import com.acadl.finora.transaction.exception.InvalidTransactionException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object que representa um valor monetário (em BRL) de uma transação.
 * <p>
 * É imutável, comparado por valor (não por identidade) e garante suas próprias
 * invariantes: nunca é nulo, é sempre positivo e sempre tem duas casas decimais.
 * O "sinal" (entrada ou saída de dinheiro) não pertence ao valor, e sim ao
 * {@link TransactionType}.
 */
@Embeddable
public class Money {

    private static final int SCALE = 2;

    @Column(name = "amount", nullable = false, precision = 15, scale = SCALE)
    private BigDecimal value;

    protected Money() {
        // exigido pelo JPA
    }

    private Money(BigDecimal value) {
        this.value = value;
    }

    public static Money of(BigDecimal value) {
        if (value == null) {
            throw new InvalidTransactionException("Valor é obrigatório");
        }
        BigDecimal normalized = value.setScale(SCALE, RoundingMode.HALF_EVEN);
        if (normalized.signum() <= 0) {
            throw new InvalidTransactionException("Valor deve ser maior que zero");
        }
        return new Money(normalized);
    }

    public BigDecimal value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "R$ " + value.toPlainString();
    }
}

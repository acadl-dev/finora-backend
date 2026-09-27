package com.acadl.finora.transaction.model;

import com.acadl.finora.transaction.exception.InvalidTransactionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Value Object Money: invariantes e igualdade por valor. */
class MoneyTest {

    @Test
    void normalizaParaDuasCasasDecimais() {
        assertThat(Money.of(new BigDecimal("10.555")).value()).isEqualByComparingTo("10.56");
        assertThat(Money.of(new BigDecimal("7")).value().scale()).isEqualTo(2);
    }

    @Test
    void rejeitaValorNuloZeroOuNegativo() {
        assertThatThrownBy(() -> Money.of(null)).isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> Money.of(BigDecimal.ZERO)).isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> Money.of(new BigDecimal("-1"))).isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> Money.of(new BigDecimal("0.001"))).isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void doisValoresIguaisSaoOMesmoMoney() {
        Money a = Money.of(new BigDecimal("10"));
        Money b = Money.of(new BigDecimal("10.00"));
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}

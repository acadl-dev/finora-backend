package com.acadl.finora.transaction.model;

import com.acadl.finora.auth.model.User;
import com.acadl.finora.shared.domain.DomainEvent;
import com.acadl.finora.transaction.event.TransactionRegistered;
import com.acadl.finora.transaction.event.TransactionRemoved;
import com.acadl.finora.transaction.exception.InvalidTransactionException;
import com.acadl.finora.transaction.exception.TransactionNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras do agregado Transaction e os eventos de domínio que ele gera (sem Spring, sem banco). */
class TransactionTest {

    private static final LocalDate DATE = LocalDate.parse("2026-09-10");
    private static final String EMAIL = "ana@finora.com";

    private static User owner() {
        User user = new User("Ana");
        user.setId(UUID.randomUUID());
        return user;
    }

    @Test
    void registrarDespesaGeraEventoTransactionRegistered() {
        User owner = owner();
        Transaction transaction = Transaction.register(owner, EMAIL, "  Mercado  ",
                Money.of(new BigDecimal("120.5")), TransactionType.EXPENSE, "  ", DATE);

        assertThat(transaction.getId()).isNotNull();          // id gerado pelo domínio
        assertThat(transaction.isNew()).isTrue();
        assertThat(transaction.getDescription()).isEqualTo("Mercado");
        assertThat(transaction.getCategory()).isNull();       // categoria em branco vira nula
        assertThat(transaction.signedAmount()).isEqualByComparingTo("-120.50");

        List<DomainEvent> events = transaction.pullDomainEvents();
        assertThat(events).hasSize(1);
        TransactionRegistered event = (TransactionRegistered) events.get(0);
        assertThat(event.transactionId()).isEqualTo(transaction.getId());
        assertThat(event.userId()).isEqualTo(owner.getId());
        assertThat(event.userEmail()).isEqualTo(EMAIL);
        assertThat(event.amount()).isEqualByComparingTo("120.50");
        assertThat(event.type()).isEqualTo("EXPENSE");
        assertThat(event.routingKey()).isEqualTo("transaction.registered");
        assertThat(event.eventId()).isNotNull();

        assertThat(transaction.pullDomainEvents()).as("eventos são entregues uma única vez").isEmpty();
    }

    @Test
    void receitaTemValorComSinalPositivo() {
        Transaction income = Transaction.registerIncome(owner(), EMAIL, "Salário",
                Money.of(new BigDecimal("5000")), "Salário", DATE);
        assertThat(income.isIncome()).isTrue();
        assertThat(income.signedAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void descricaoEDataSaoObrigatorias() {
        assertThatThrownBy(() -> Transaction.register(owner(), EMAIL, "   ",
                Money.of(BigDecimal.TEN), TransactionType.INCOME, null, DATE))
                .isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> Transaction.register(owner(), EMAIL, "Aluguel",
                Money.of(BigDecimal.TEN), TransactionType.EXPENSE, null, null))
                .isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> Transaction.register(null, EMAIL, "Aluguel",
                Money.of(BigDecimal.TEN), TransactionType.EXPENSE, null, DATE))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void somenteODonoPodeExcluir() {
        User owner = owner();
        Transaction transaction = Transaction.register(owner, EMAIL, "Internet",
                Money.of(new BigDecimal("99.90")), TransactionType.EXPENSE, "Moradia", DATE);
        transaction.pullDomainEvents();

        assertThatThrownBy(() -> transaction.remove(UUID.randomUUID(), "intruso@finora.com"))
                .isInstanceOf(TransactionNotFoundException.class);
        assertThat(transaction.pullDomainEvents()).isEmpty();

        transaction.remove(owner.getId(), EMAIL);
        List<DomainEvent> events = transaction.pullDomainEvents();
        assertThat(events).singleElement().isInstanceOf(TransactionRemoved.class);
        assertThat(((TransactionRemoved) events.get(0)).routingKey()).isEqualTo("transaction.removed");
    }

    @Test
    void tipoDaTransacaoAplicaOSinal() {
        assertThat(TransactionType.INCOME.applySignTo(new BigDecimal("10"))).isEqualByComparingTo("10");
        assertThat(TransactionType.EXPENSE.applySignTo(new BigDecimal("10"))).isEqualByComparingTo("-10");
    }
}

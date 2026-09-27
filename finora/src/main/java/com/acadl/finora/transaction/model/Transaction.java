package com.acadl.finora.transaction.model;

import com.acadl.finora.auth.model.User;
import com.acadl.finora.transaction.exception.InvalidTransactionException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root do contexto de Transações.
 * <p>
 * Não expõe setters: a única forma de criar uma transação válida é pela fábrica
 * {@link #register}, que garante as regras de negócio (invariantes). Assim, nenhuma
 * transação inconsistente chega ao banco de dados.
 */
@Getter
@Entity
@Table(name = "transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido pelo JPA
public class Transaction {

    private static final int DESCRIPTION_MAX_LENGTH = 255;
    private static final int CATEGORY_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String description;

    @Embedded
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column
    private String category;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private Transaction(User user, String description, Money amount,
                        TransactionType type, String category, LocalDate date) {
        this.user = user;
        this.description = description;
        this.amount = amount;
        this.type = type;
        this.category = category;
        this.date = date;
        this.createdAt = Instant.now();
    }

    /**
     * Fábrica: registra uma nova receita ou despesa para o usuário informado.
     */
    public static Transaction register(User owner, String description, Money amount,
                                       TransactionType type, String category, LocalDate date) {
        if (owner == null) {
            throw new InvalidTransactionException("Transação precisa pertencer a um usuário");
        }
        if (amount == null) {
            throw new InvalidTransactionException("Valor é obrigatório");
        }
        if (type == null) {
            throw new InvalidTransactionException("Tipo é obrigatório");
        }
        if (date == null) {
            throw new InvalidTransactionException("Data é obrigatória");
        }
        return new Transaction(
                owner,
                normalizeDescription(description),
                amount,
                type,
                normalizeCategory(category),
                date
        );
    }

    public static Transaction registerIncome(User owner, String description, Money amount,
                                             String category, LocalDate date) {
        return register(owner, description, amount, TransactionType.INCOME, category, date);
    }

    public static Transaction registerExpense(User owner, String description, Money amount,
                                              String category, LocalDate date) {
        return register(owner, description, amount, TransactionType.EXPENSE, category, date);
    }

    // ---- Comportamentos de domínio ----

    public boolean isIncome() {
        return type == TransactionType.INCOME;
    }

    public boolean isExpense() {
        return type == TransactionType.EXPENSE;
    }

    /** Valor com sinal: positivo para receitas, negativo para despesas. */
    public BigDecimal signedAmount() {
        return type.applySignTo(amount.value());
    }

    public boolean belongsTo(UUID userId) {
        return user != null && Objects.equals(user.getId(), userId);
    }

    // ---- Regras de normalização ----

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new InvalidTransactionException("Descrição é obrigatória");
        }
        String trimmed = description.trim();
        if (trimmed.length() > DESCRIPTION_MAX_LENGTH) {
            throw new InvalidTransactionException(
                    "Descrição deve ter no máximo " + DESCRIPTION_MAX_LENGTH + " caracteres");
        }
        return trimmed;
    }

    private static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String trimmed = category.trim();
        if (trimmed.length() > CATEGORY_MAX_LENGTH) {
            throw new InvalidTransactionException(
                    "Categoria deve ter no máximo " + CATEGORY_MAX_LENGTH + " caracteres");
        }
        return trimmed;
    }

    // Entidades são comparadas pela identidade (id), não pelos atributos.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Transaction{id=" + id + ", type=" + type + ", amount=" + amount + ", date=" + date + "}";
    }
}

package com.acadl.finora.transaction.model;

import com.acadl.finora.auth.model.User;
import com.acadl.finora.shared.domain.DomainEvent;
import com.acadl.finora.transaction.event.TransactionRegistered;
import com.acadl.finora.transaction.event.TransactionRemoved;
import com.acadl.finora.transaction.exception.InvalidTransactionException;
import com.acadl.finora.transaction.exception.TransactionNotFoundException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root do contexto de Transações.
 * <p>
 * Não expõe setters: a única forma de criar uma transação válida é pela fábrica
 * {@link #register}, que garante as regras de negócio (invariantes). Assim, nenhuma
 * transação inconsistente chega ao banco de dados.
 * <p>
 * Arquitetura orientada a eventos: cada mudança relevante registra um evento de
 * domínio ({@link TransactionRegistered}, {@link TransactionRemoved}). O serviço de
 * aplicação recolhe esses eventos ({@link #pullDomainEvents()}) e os grava na
 * outbox, na mesma transação do banco.
 * <p>
 * O id é gerado pelo próprio domínio (UUID) no momento da criação, para que o evento
 * já nasça com o id da transação. {@link Persistable} informa ao Spring Data que a
 * entidade é nova (INSERT direto, sem SELECT antes).
 */
@Getter
@Entity
@Table(name = "transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido pelo JPA
public class Transaction implements Persistable<UUID> {

    private static final int DESCRIPTION_MAX_LENGTH = 255;
    private static final int CATEGORY_MAX_LENGTH = 100;

    @Id
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

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean persisted = false;

    @Transient
    @Getter(AccessLevel.NONE)
    private List<DomainEvent> domainEvents = new ArrayList<>();

    private Transaction(User user, String description, Money amount,
                        TransactionType type, String category, LocalDate date) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.description = description;
        this.amount = amount;
        this.type = type;
        this.category = category;
        this.date = date;
        this.createdAt = Instant.now();
    }

    /**
     * Fábrica: registra uma nova receita ou despesa para o usuário informado e
     * registra o evento de domínio {@link TransactionRegistered}.
     */
    public static Transaction register(User owner, String ownerEmail, String description, Money amount,
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
        Transaction transaction = new Transaction(
                owner,
                normalizeDescription(description),
                amount,
                type,
                normalizeCategory(category),
                date
        );
        transaction.domainEvents.add(TransactionRegistered.of(transaction, ownerEmail));
        return transaction;
    }

    public static Transaction registerIncome(User owner, String ownerEmail, String description, Money amount,
                                             String category, LocalDate date) {
        return register(owner, ownerEmail, description, amount, TransactionType.INCOME, category, date);
    }

    public static Transaction registerExpense(User owner, String ownerEmail, String description, Money amount,
                                              String category, LocalDate date) {
        return register(owner, ownerEmail, description, amount, TransactionType.EXPENSE, category, date);
    }

    /**
     * Regra de negócio: só o dono pode excluir a transação. Registra o evento
     * {@link TransactionRemoved}; a exclusão física fica a cargo do repositório.
     */
    public void remove(UUID requesterId, String requesterEmail) {
        if (!belongsTo(requesterId)) {
            throw new TransactionNotFoundException(id);
        }
        domainEvents.add(TransactionRemoved.of(this, requesterEmail));
    }

    /** Entrega (e limpa) os eventos de domínio registrados desde a última chamada. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    // ---- Persistable: id gerado pelo domínio ----

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.persisted = true;
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

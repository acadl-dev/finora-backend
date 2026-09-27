package com.acadl.finora.outbox.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox: evento de domínio aguardando publicação no RabbitMQ.
 * <p>
 * É gravado na mesma transação do banco que alterou o agregado. Depois, o
 * {@code OutboxRelay} publica os pendentes e marca {@code publishedAt}. A tabela
 * também serve de trilha de auditoria de tudo o que o finora anunciou.
 */
@Getter
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_pending", columnList = "published_at, occurred_at"),
        @Index(name = "idx_outbox_aggregate", columnList = "aggregate_id, event_type")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent implements Persistable<UUID> {

    private static final int MAX_ERROR_LENGTH = 1000;

    /** Mesmo valor do eventId: vira o messageId da mensagem (deduplicação no consumidor). */
    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String exchange;

    @Column(name = "routing_key", nullable = false)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean persisted = false;

    public static OutboxEvent pending(UUID eventId, String aggregateType, UUID aggregateId, String eventType,
                                      String exchange, String routingKey, String payload, Instant occurredAt) {
        OutboxEvent event = new OutboxEvent();
        event.id = eventId;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.exchange = exchange;
        event.routingKey = routingKey;
        event.payload = payload;
        event.occurredAt = occurredAt;
        event.attempts = 0;
        return event;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public void markPublished(Instant when) {
        this.attempts++;
        this.publishedAt = when;
        this.lastError = null;
    }

    public void registerFailure(String error) {
        this.attempts++;
        this.lastError = error == null ? "erro desconhecido"
                : error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.persisted = true;
    }
}

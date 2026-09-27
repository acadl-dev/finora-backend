package com.acadl.reports.ledger.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotent Consumer: registro dos eventos já aplicados. Como o RabbitMQ entrega
 * "pelo menos uma vez", um mesmo evento pode chegar duas vezes; se o eventId já
 * está aqui, ele é ignorado.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // exigido pelo JPA
    }

    public ProcessedEvent(UUID eventId, String eventType, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }
}

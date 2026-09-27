package com.acadl.finora.shared.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de domínio: um fato de negócio que já aconteceu (verbo no passado).
 * <p>
 * Os dados do evento (componentes do record) viram o corpo JSON da mensagem.
 * Os métodos marcados com {@link JsonIgnore} são metadados de roteamento.
 */
public interface DomainEvent {

    UUID eventId();

    String eventType();

    int eventVersion();

    Instant occurredAt();

    @JsonIgnore
    String aggregateType();

    @JsonIgnore
    UUID aggregateId();

    /** Chave de roteamento no exchange de tópicos (ex.: transaction.registered). */
    @JsonIgnore
    String routingKey();
}

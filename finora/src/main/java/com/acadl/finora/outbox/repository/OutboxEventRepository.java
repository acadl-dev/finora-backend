package com.acadl.finora.outbox.repository;

import com.acadl.finora.outbox.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Pendentes em ordem de ocorrência (preserva a ordem dos eventos). */
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByOccurredAtAsc();

    /** Métrica finora_outbox_pending: eventos ainda não publicados no RabbitMQ. */
    long countByPublishedAtIsNull();
}

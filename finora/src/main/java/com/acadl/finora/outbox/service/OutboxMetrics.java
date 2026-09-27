package com.acadl.finora.outbox.service;

import com.acadl.finora.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Métrica de negócio/operação exposta ao Prometheus:
 * finora_outbox_pending = eventos gravados que ainda não chegaram ao RabbitMQ.
 * Em operação normal fica em 0; se subir, o broker está fora do ar ou lento
 * (é o primeiro sinal no painel do Grafana).
 */
@Component
public class OutboxMetrics {

    public OutboxMetrics(MeterRegistry registry, OutboxEventRepository repository) {
        Gauge.builder("finora.outbox.pending", repository, OutboxEventRepository::countByPublishedAtIsNull)
                .description("Eventos na outbox aguardando publicação no RabbitMQ")
                .register(registry);
    }
}

package com.acadl.finora.outbox.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Liga o trace da requisição HTTP à publicação assíncrona feita pelo OutboxRelay.
 * <p>
 * Sem isso, a publicação (que roda numa thread agendada) começaria um trace novo e o
 * Grafana Tempo mostraria duas transações desconexas. Aqui guardamos o "traceparent"
 * W3C junto com o evento na outbox e, na hora de publicar, abrimos um span filho dele.
 * O RabbitTemplate (observation-enabled) injeta o contexto nos headers da mensagem e o
 * reports-service continua o MESMO trace ao consumi-la.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxTracing {

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectProvider<Tracer> tracerProvider;
    private final ObjectProvider<Propagator> propagatorProvider;
    private final ObjectMapper objectMapper;

    @FunctionalInterface
    public interface TracedAction {
        void run() throws Exception;
    }

    /** Serializa o contexto de rastreamento atual (ou null se não houver trace ativo). */
    public String captureCurrentContext() {
        Tracer tracer = tracerProvider.getIfAvailable();
        Propagator propagator = propagatorProvider.getIfAvailable();
        if (tracer == null || propagator == null) {
            return null;
        }
        TraceContext context = tracer.currentTraceContext().context();
        if (context == null) {
            return null;
        }
        Map<String, String> headers = new HashMap<>();
        propagator.inject(context, headers, (carrier, key, value) -> carrier.put(key, value));
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (Exception e) {
            log.debug("Não foi possível serializar o contexto de trace", e);
            return null;
        }
    }

    /** Executa a ação dentro de um span filho do contexto guardado na outbox. */
    public void runWithin(String traceHeaders, String spanName, TracedAction action) throws Exception {
        Tracer tracer = tracerProvider.getIfAvailable();
        Propagator propagator = propagatorProvider.getIfAvailable();
        if (tracer == null || propagator == null) {
            action.run();
            return;
        }

        Map<String, String> headers = parse(traceHeaders);
        Span.Builder builder = headers.isEmpty()
                ? tracer.spanBuilder()
                : propagator.extract(headers, (carrier, key) -> carrier.get(key));
        Span span = builder.name(spanName).kind(Span.Kind.PRODUCER).start();

        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            action.run();
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private Map<String, String> parse(String traceHeaders) {
        if (traceHeaders == null || traceHeaders.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(traceHeaders, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }
}

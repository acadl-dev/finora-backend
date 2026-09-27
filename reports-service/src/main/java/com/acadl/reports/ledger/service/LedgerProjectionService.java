package com.acadl.reports.ledger.service;

import com.acadl.reports.ledger.mapper.LedgerEntryMapper;
import com.acadl.reports.ledger.messaging.TransactionEventMessage;
import com.acadl.reports.ledger.model.LedgerEntry;
import com.acadl.reports.ledger.model.ProcessedEvent;
import com.acadl.reports.ledger.repository.LedgerEntryRepository;
import com.acadl.reports.ledger.repository.ProcessedEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Aplica os eventos de transação na projeção local (ledger_entries).
 * <p>
 * Tudo em uma transação do banco: atualizar a projeção + registrar o evento como
 * processado. Se algo falhar, nada é gravado e a mensagem é reentregue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerProjectionService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void apply(TransactionEventMessage event) {
        if (event.eventId() == null || event.eventType() == null) {
            throw new IllegalArgumentException("Evento sem eventId/eventType");
        }
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Evento {} já processado; ignorado (idempotência)", event.eventId());
            meterRegistry.counter("finora.ledger.events.duplicated").increment();
            return;
        }

        switch (event.eventType()) {
            case TransactionEventMessage.REGISTERED -> onRegistered(event);
            case TransactionEventMessage.REMOVED -> onRemoved(event);
            default -> log.warn("Tipo de evento desconhecido ignorado: {}", event.eventType());
        }

        processedEventRepository.save(new ProcessedEvent(event.eventId(), event.eventType(), Instant.now()));
        meterRegistry.counter("finora.ledger.events.applied", "type", event.eventType()).increment();
    }

    private void onRegistered(TransactionEventMessage event) {
        LedgerEntry existing = ledgerEntryRepository.findById(event.transactionId()).orElse(null);
        if (existing == null) {
            ledgerEntryRepository.save(LedgerEntryMapper.fromRegistered(event));
        } else if (existing.isRemoved()) {
            log.info("Transação {} já excluída; registro tardio ignorado", event.transactionId());
        }
        // existente e ativo: registro repetido (ex.: backfill) -> nada a fazer
    }

    private void onRemoved(TransactionEventMessage event) {
        Instant when = event.occurredAt() == null ? Instant.now() : event.occurredAt();
        ledgerEntryRepository.findById(event.transactionId()).ifPresentOrElse(
                entry -> entry.markRemoved(when),
                () -> ledgerEntryRepository.save(
                        LedgerEntry.tombstone(event.transactionId(), event.userId(), event.userEmail(), when))
        );
    }
}

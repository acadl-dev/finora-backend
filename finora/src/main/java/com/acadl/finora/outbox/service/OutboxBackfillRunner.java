package com.acadl.finora.outbox.service;

import com.acadl.finora.auth.model.Credential;
import com.acadl.finora.shared.domain.DomainEvent;
import com.acadl.finora.transaction.event.TransactionRegistered;
import com.acadl.finora.transaction.model.Transaction;
import com.acadl.finora.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Migração para a arquitetura de eventos: ao subir, gera {@link TransactionRegistered}
 * para transações que existiam ANTES da outbox (nunca anunciadas). Assim o
 * reports-service monta sua projeção completa sem precisar chamar o finora.
 * É idempotente: uma transação que já tem o evento na outbox é ignorada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxBackfillRunner implements ApplicationRunner {

    private final TransactionRepository transactionRepository;
    private final OutboxEventRecorder outboxEventRecorder;

    @Value("${finora.outbox.backfill-on-startup:true}")
    private boolean enabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        List<DomainEvent> events = new ArrayList<>();
        for (Transaction transaction : transactionRepository.findAllWithoutRegisteredEvent()) {
            Credential credential = transaction.getUser().getCredential();
            if (credential == null) {
                log.warn("Transação {} sem credencial associada ao usuário; ignorada no backfill", transaction.getId());
                continue;
            }
            events.add(TransactionRegistered.of(transaction, credential.getEmail()));
        }

        if (!events.isEmpty()) {
            outboxEventRecorder.record(events);
            log.info("Backfill da outbox: {} transação(ões) antiga(s) serão publicadas como TransactionRegistered",
                    events.size());
        }
    }
}

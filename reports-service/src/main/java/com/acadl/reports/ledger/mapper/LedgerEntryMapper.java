package com.acadl.reports.ledger.mapper;

import com.acadl.reports.ledger.messaging.TransactionEventMessage;
import com.acadl.reports.ledger.model.LedgerEntry;
import com.acadl.reports.report.model.EntryType;

/**
 * Anti-Corruption Layer: traduz o evento publicado pelo finora (linguagem do
 * contexto de Transações) para a projeção do contexto de Relatórios.
 */
public class LedgerEntryMapper {

    public static LedgerEntry fromRegistered(TransactionEventMessage event) {
        if (event.transactionId() == null || event.userEmail() == null || event.amount() == null
                || event.date() == null) {
            throw new IllegalArgumentException("Evento TransactionRegistered incompleto: " + event.eventId());
        }
        return LedgerEntry.registered(
                event.transactionId(),
                event.userId(),
                event.userEmail(),
                event.description(),
                event.category(),
                toEntryType(event.type()),
                event.amount().abs(),
                event.date(),
                event.occurredAt()
        );
    }

    private static EntryType toEntryType(String type) {
        if ("INCOME".equalsIgnoreCase(type)) return EntryType.INCOME;
        if ("EXPENSE".equalsIgnoreCase(type)) return EntryType.EXPENSE;
        throw new IllegalArgumentException("Tipo de transação desconhecido: " + type);
    }
}

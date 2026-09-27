package com.acadl.reports.ledger.mapper;

import com.acadl.reports.ledger.messaging.TransactionEventMessage;
import com.acadl.reports.ledger.model.LedgerEntry;
import com.acadl.reports.report.model.EntryType;
import com.acadl.reports.report.model.ReportEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Anti-Corruption Layer: evento do finora -> projeção do reports-service. */
class LedgerEntryMapperTest {

    private static TransactionEventMessage registered(String type, String amount) {
        return new TransactionEventMessage(UUID.randomUUID(), TransactionEventMessage.REGISTERED, 1, Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(), "user@finora.com", "Mercado", new BigDecimal(amount),
                type, "Alimentação", LocalDate.parse("2026-09-10"));
    }

    @Test
    void traduzEventoRegistradoParaLancamentoDoRelatorio() {
        LedgerEntry entry = LedgerEntryMapper.fromRegistered(registered("EXPENSE", "120.5"));
        ReportEntry reportEntry = entry.toReportEntry();

        assertFalse(entry.isRemoved());
        assertEquals(EntryType.EXPENSE, reportEntry.type());
        assertEquals(new BigDecimal("-120.50"), reportEntry.signedAmount());
        assertEquals("user@finora.com", entry.getOwnerEmail());
    }

    @Test
    void lapideMarcaExclusaoQueChegouAntesDoRegistro() {
        LedgerEntry tombstone = LedgerEntry.tombstone(UUID.randomUUID(), UUID.randomUUID(), "user@finora.com", Instant.now());
        assertTrue(tombstone.isRemoved());
    }

    @Test
    void tipoDesconhecidoEhRejeitado() {
        assertThrows(IllegalArgumentException.class, () -> LedgerEntryMapper.fromRegistered(registered("TRANSFER", "10")));
    }
}

package com.acadl.reports.ledger.service;

import com.acadl.reports.ledger.model.LedgerEntry;
import com.acadl.reports.ledger.repository.LedgerEntryRepository;
import com.acadl.reports.report.model.ReportEntry;
import com.acadl.reports.report.model.TransactionSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptador da porta {@link TransactionSource} baseado na projeção local.
 * <p>
 * Antes (arquitetura acoplada) o adaptador era um cliente OpenFeign que chamava o
 * finora a cada relatório. Agora lê a base do próprio reports-service, alimentada
 * pelos eventos. O domínio de relatórios não mudou: só o adaptador foi trocado.
 */
@Component
@RequiredArgsConstructor
public class LedgerTransactionSource implements TransactionSource {

    private final LedgerEntryRepository ledgerEntryRepository;

    @Override
    public List<ReportEntry> fetchEntries(String ownerEmail) {
        return ledgerEntryRepository.findAllByOwnerEmailAndRemovedFalse(ownerEmail)
                .stream()
                .map(LedgerEntry::toReportEntry)
                .toList();
    }
}

package com.acadl.reports.report.model;

import java.util.List;

/**
 * Porta (interface de domínio) para obter os lançamentos de um usuário.
 * <p>
 * Implementação atual: {@code LedgerTransactionSource}, que lê a projeção local
 * alimentada pelos eventos do finora. O domínio não sabe de onde os dados vêm.
 */
public interface TransactionSource {

    List<ReportEntry> fetchEntries(String ownerEmail);
}

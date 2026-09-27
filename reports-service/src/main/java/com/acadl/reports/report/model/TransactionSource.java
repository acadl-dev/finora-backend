package com.acadl.reports.report.model;

import java.util.List;

/**
 * Porta (interface de domínio) para obter os lançamentos do usuário.
 * A implementação concreta (adaptador) busca no microsserviço finora via OpenFeign,
 * mas o domínio não sabe disso.
 */
public interface TransactionSource {

    List<ReportEntry> fetchEntries(String authorizationHeader);
}

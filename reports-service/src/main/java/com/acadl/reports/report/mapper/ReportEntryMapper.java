package com.acadl.reports.report.mapper;

import com.acadl.reports.report.client.FinoraTransactionResponse;
import com.acadl.reports.report.exception.ReportGenerationException;
import com.acadl.reports.report.model.EntryType;
import com.acadl.reports.report.model.ReportEntry;

/** Tradução do contrato do finora para o Value Object do contexto de Relatórios. */
public class ReportEntryMapper {

    public static ReportEntry toEntry(FinoraTransactionResponse transaction) {
        return new ReportEntry(
                transaction.date(),
                transaction.description(),
                transaction.category(),
                toEntryType(transaction.type()),
                transaction.amount() == null ? null : transaction.amount().abs()
        );
    }

    private static EntryType toEntryType(String type) {
        if ("INCOME".equalsIgnoreCase(type)) return EntryType.INCOME;
        if ("EXPENSE".equalsIgnoreCase(type)) return EntryType.EXPENSE;
        throw new ReportGenerationException("Tipo de transação desconhecido: " + type);
    }
}

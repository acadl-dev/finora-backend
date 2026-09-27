package com.acadl.reports.report.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Domain Service: calcula a consolidação (receitas, despesas e saldo) de um
 * conjunto de lançamentos.
 * <p>
 * É um serviço de domínio porque a regra não pertence naturalmente a um único
 * lançamento nem ao relatório: ela opera sobre uma coleção de Value Objects.
 * Não depende de framework, banco ou HTTP — é regra de negócio pura.
 */
public class BalanceCalculator {

    public ReportSummary calculate(List<ReportEntry> entries) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;

        for (ReportEntry entry : entries) {
            if (entry.isIncome()) {
                income = income.add(entry.amount());
            } else {
                expense = expense.add(entry.amount());
            }
        }

        return ReportSummary.of(income, expense, entries.size());
    }
}

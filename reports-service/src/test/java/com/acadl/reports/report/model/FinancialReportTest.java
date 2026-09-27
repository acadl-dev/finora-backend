package com.acadl.reports.report.model;

import com.acadl.reports.report.exception.InvalidReportPeriodException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Testes de domínio puros: sem Spring, sem banco, sem rede. */
class FinancialReportTest {

    private final BalanceCalculator calculator = new BalanceCalculator();

    private static ReportEntry income(String date, String value) {
        return new ReportEntry(LocalDate.parse(date), "Receita", "Salário", EntryType.INCOME, new BigDecimal(value));
    }

    private static ReportEntry expense(String date, String value) {
        return new ReportEntry(LocalDate.parse(date), "Despesa", "Mercado", EntryType.EXPENSE, new BigDecimal(value));
    }

    @Test
    void saldoEhReceitasMenosDespesas() {
        ReportSummary summary = calculator.calculate(List.of(
                income("2026-09-01", "5000.00"),
                expense("2026-09-05", "1200.50"),
                expense("2026-09-10", "299.50")
        ));

        assertEquals(new BigDecimal("5000.00"), summary.totalIncome());
        assertEquals(new BigDecimal("1500.00"), summary.totalExpense());
        assertEquals(new BigDecimal("3500.00"), summary.balance());
        assertEquals(3, summary.entryCount());
        assertTrue(summary.isPositive());
    }

    @Test
    void saldoPodeSerNegativo() {
        ReportSummary summary = calculator.calculate(List.of(
                income("2026-09-01", "100"),
                expense("2026-09-02", "250")
        ));
        assertEquals(new BigDecimal("-150.00"), summary.balance());
        assertFalse(summary.isPositive());
    }

    @Test
    void relatorioConsideraApenasLancamentosDoPeriodoEOrdenaPorData() {
        List<ReportEntry> entries = List.of(
                expense("2026-09-20", "50"),
                income("2026-08-31", "999"),   // fora do período
                income("2026-09-01", "1000"),
                expense("2026-10-01", "999")   // fora do período
        );

        FinancialReport report = FinancialReport.generate(
                "user@finora.com",
                ReportPeriod.of(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")),
                entries, calculator, ReportFormat.XLSX);

        assertEquals(2, report.getEntries().size());
        assertEquals(LocalDate.parse("2026-09-01"), report.getEntries().get(0).date());
        assertEquals(new BigDecimal("950.00"), report.getSummary().balance());
        assertTrue(report.getFileName().endsWith(".xlsx"));
    }

    @Test
    void periodoInvalidoEhRejeitado() {
        assertThrows(InvalidReportPeriodException.class,
                () -> ReportPeriod.of(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-09-01")));
    }

    @Test
    void periodoSemLimitesIncluiTudo() {
        ReportPeriod all = ReportPeriod.allTime();
        assertTrue(all.isAllTime());
        assertTrue(all.contains(LocalDate.parse("1990-01-01")));
        assertEquals("Todo o período", all.describe());
    }

    @Test
    void despesaTemValorComSinalNegativo() {
        assertEquals(new BigDecimal("-10.00"), expense("2026-09-01", "10").signedAmount());
        assertEquals(new BigDecimal("10.00"), income("2026-09-01", "10").signedAmount());
    }
}

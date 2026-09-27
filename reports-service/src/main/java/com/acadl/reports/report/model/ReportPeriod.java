package com.acadl.reports.report.model;

import com.acadl.reports.report.exception.InvalidReportPeriodException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Value Object: intervalo de datas coberto pelo relatório.
 * Início e fim são opcionais (sem limites = todo o período).
 */
@Embeddable
public class ReportPeriod {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Column(name = "period_start")
    private LocalDate start;

    @Column(name = "period_end")
    private LocalDate end;

    protected ReportPeriod() {
        // exigido pelo JPA
    }

    private ReportPeriod(LocalDate start, LocalDate end) {
        this.start = start;
        this.end = end;
    }

    public static ReportPeriod of(LocalDate start, LocalDate end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new InvalidReportPeriodException("A data inicial deve ser anterior ou igual à data final");
        }
        return new ReportPeriod(start, end);
    }

    public static ReportPeriod allTime() {
        return new ReportPeriod(null, null);
    }

    public boolean contains(LocalDate date) {
        if (date == null) return false;
        boolean afterStart = start == null || !date.isBefore(start);
        boolean beforeEnd = end == null || !date.isAfter(end);
        return afterStart && beforeEnd;
    }

    public boolean isAllTime() {
        return start == null && end == null;
    }

    public String describe() {
        if (isAllTime()) return "Todo o período";
        if (start == null) return "Até " + end.format(BR);
        if (end == null) return "A partir de " + start.format(BR);
        return start.format(BR) + " a " + end.format(BR);
    }

    public LocalDate start() {
        return start;
    }

    public LocalDate end() {
        return end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReportPeriod other)) return false;
        return Objects.equals(start, other.start) && Objects.equals(end, other.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return describe();
    }
}

package com.acadl.reports.report.dto;

import java.time.LocalDate;

/** Pedido de relatório. Datas opcionais (yyyy-MM-dd); sem datas = todo o período. */
public record CreateReportRequest(LocalDate start, LocalDate end) {
}

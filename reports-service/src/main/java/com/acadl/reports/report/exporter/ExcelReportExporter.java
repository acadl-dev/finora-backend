package com.acadl.reports.report.exporter;

import com.acadl.reports.report.exception.ReportGenerationException;
import com.acadl.reports.report.model.FinancialReport;
import com.acadl.reports.report.model.ReportEntry;
import com.acadl.reports.report.model.ReportExporter;
import com.acadl.reports.report.model.ReportFile;
import com.acadl.reports.report.model.ReportFormat;
import com.acadl.reports.report.model.ReportSummary;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * Adaptador da porta {@link ReportExporter} que gera uma planilha .xlsx com:
 * - aba "Resumo": período, total de receitas, total de despesas e saldo (receitas − despesas);
 * - aba "Lançamentos": cada receita/despesa do período e uma linha final de saldo (fórmula).
 */
@Component
public class ExcelReportExporter implements ReportExporter {

    private static final String CURRENCY_FORMAT = "\"R$\" #,##0.00;[Red]-\"R$\" #,##0.00";
    private static final DateTimeFormatter GENERATED_AT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    @Override
    public ReportFormat format() {
        return ReportFormat.XLSX;
    }

    @Override
    public ReportFile export(FinancialReport report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);

            writeSummarySheet(workbook, styles, report);
            writeEntriesSheet(workbook, styles, report.getEntries());

            workbook.write(out);
            return new ReportFile(report.getFileName(), format().contentType(), out.toByteArray());
        } catch (IOException e) {
            throw new ReportGenerationException("Falha ao gerar o arquivo Excel", e);
        }
    }

    private void writeSummarySheet(Workbook workbook, Styles styles, FinancialReport report) {
        Sheet sheet = workbook.createSheet("Resumo");
        sheet.setColumnWidth(0, 34 * 256);
        sheet.setColumnWidth(1, 30 * 256);

        Cell title = sheet.createRow(0).createCell(0);
        title.setCellValue("Finora — Relatório de receitas e despesas");
        title.setCellStyle(styles.title);

        textRow(sheet, 2, "Usuário", report.getOwnerEmail(), styles);
        textRow(sheet, 3, "Período", report.getPeriod().describe(), styles);
        textRow(sheet, 4, "Gerado em", GENERATED_AT.format(report.getCompletedAt() != null ? report.getCompletedAt() : report.getRequestedAt()), styles);

        ReportSummary summary = report.getSummary();
        moneyRow(sheet, 6, "Total de receitas", summary.totalIncome(), styles.label, styles.currency);
        moneyRow(sheet, 7, "Total de despesas", summary.totalExpense(), styles.label, styles.currency);
        moneyRow(sheet, 8, "Saldo (receitas − despesas)", summary.balance(), styles.totalLabel, styles.totalCurrency);

        Row count = sheet.createRow(10);
        Cell countLabel = count.createCell(0);
        countLabel.setCellValue("Quantidade de lançamentos");
        countLabel.setCellStyle(styles.label);
        count.createCell(1).setCellValue(summary.entryCount());
    }

    private void writeEntriesSheet(Workbook workbook, Styles styles, List<ReportEntry> entries) {
        Sheet sheet = workbook.createSheet("Lançamentos");
        int[] widths = {14, 40, 22, 12, 18};
        String[] headers = {"Data", "Descrição", "Categoria", "Tipo", "Valor"};

        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }
        sheet.createFreezePane(0, 1);

        int rowIndex = 1;
        for (ReportEntry entry : entries) {
            Row row = sheet.createRow(rowIndex++);

            Cell date = row.createCell(0);
            date.setCellValue(Date.from(entry.date().atStartOfDay(ZoneId.systemDefault()).toInstant()));
            date.setCellStyle(styles.date);

            row.createCell(1).setCellValue(entry.description());
            row.createCell(2).setCellValue(entry.category() == null ? "" : entry.category());
            row.createCell(3).setCellValue(entry.type().label());

            Cell amount = row.createCell(4);
            amount.setCellValue(entry.signedAmount().doubleValue());
            amount.setCellStyle(styles.currency);
        }

        // Linha de saldo: fórmula viva (soma dos valores com sinal)
        Row totalRow = sheet.createRow(rowIndex + 1);
        Cell totalLabel = totalRow.createCell(3);
        totalLabel.setCellValue("Saldo");
        totalLabel.setCellStyle(styles.totalLabel);

        Cell total = totalRow.createCell(4);
        if (entries.isEmpty()) {
            total.setCellValue(0);
        } else {
            total.setCellFormula("SUM(E2:E" + rowIndex + ")");
        }
        total.setCellStyle(styles.totalCurrency);
    }

    private void textRow(Sheet sheet, int rowIndex, String label, String value, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.label);
        row.createCell(1).setCellValue(value);
    }

    private void moneyRow(Sheet sheet, int rowIndex, String label, BigDecimal value,
                          CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowIndex);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);

        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value.doubleValue());
        valueCell.setCellStyle(valueStyle);
    }

    /** Estilos reutilizados na planilha. */
    private static final class Styles {
        final CellStyle title;
        final CellStyle header;
        final CellStyle label;
        final CellStyle totalLabel;
        final CellStyle currency;
        final CellStyle totalCurrency;
        final CellStyle date;

        Styles(Workbook workbook) {
            DataFormat dataFormat = workbook.createDataFormat();

            Font bold = workbook.createFont();
            bold.setBold(true);

            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            title = workbook.createCellStyle();
            title.setFont(titleFont);

            header = workbook.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            label = workbook.createCellStyle();
            label.setFont(bold);

            totalLabel = workbook.createCellStyle();
            totalLabel.setFont(bold);
            totalLabel.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            totalLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            currency = workbook.createCellStyle();
            currency.setDataFormat(dataFormat.getFormat(CURRENCY_FORMAT));

            totalCurrency = workbook.createCellStyle();
            totalCurrency.setDataFormat(dataFormat.getFormat(CURRENCY_FORMAT));
            totalCurrency.setFont(bold);
            totalCurrency.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            totalCurrency.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            date = workbook.createCellStyle();
            date.setDataFormat(dataFormat.getFormat("dd/mm/yyyy"));
        }
    }
}

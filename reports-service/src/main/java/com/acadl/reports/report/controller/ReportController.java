package com.acadl.reports.report.controller;

import com.acadl.reports.report.dto.ReportHistoryResponse;
import com.acadl.reports.report.model.ReportFile;
import com.acadl.reports.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * Extrai o relatório de receitas e despesas em Excel.
     * Parâmetros opcionais: start e end (yyyy-MM-dd).
     */
    @GetMapping("/transactions/excel")
    public ResponseEntity<byte[]> exportTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @AuthenticationPrincipal String ownerEmail
    ) {
        ReportFile file = reportService.generateTransactionsReport(ownerEmail, authorization, start, end);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .contentLength(file.content().length)
                .body(file.content());
    }

    /** Histórico dos últimos relatórios gerados pelo usuário. */
    @GetMapping("/history")
    public ResponseEntity<List<ReportHistoryResponse>> history(@AuthenticationPrincipal String ownerEmail) {
        return ResponseEntity.ok(reportService.listHistory(ownerEmail));
    }
}

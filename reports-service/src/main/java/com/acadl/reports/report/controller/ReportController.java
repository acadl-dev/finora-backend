package com.acadl.reports.report.controller;

import com.acadl.reports.report.dto.CreateReportRequest;
import com.acadl.reports.report.dto.ReportResponse;
import com.acadl.reports.report.model.ReportFile;
import com.acadl.reports.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * Solicita um relatório de receitas e despesas em Excel.
     * Responde 202 Accepted na hora; a geração é assíncrona (fila RabbitMQ).
     */
    @PostMapping
    public ResponseEntity<ReportResponse> request(
            @RequestBody(required = false) CreateReportRequest body,
            @AuthenticationPrincipal String ownerEmail
    ) {
        CreateReportRequest request = body == null ? new CreateReportRequest(null, null) : body;
        ReportResponse created = reportService.requestTransactionsReport(ownerEmail, request.start(), request.end());
        return ResponseEntity.accepted()
                .location(URI.create("/reports/" + created.id()))
                .body(created);
    }

    /** Histórico dos últimos relatórios do usuário (com status). */
    @GetMapping("/history")
    public ResponseEntity<List<ReportResponse>> history(@AuthenticationPrincipal String ownerEmail) {
        return ResponseEntity.ok(reportService.listHistory(ownerEmail));
    }

    /** Status de um relatório (REQUESTED, READY ou FAILED). */
    @GetMapping("/{id}")
    public ResponseEntity<ReportResponse> status(@PathVariable UUID id, @AuthenticationPrincipal String ownerEmail) {
        return ResponseEntity.ok(reportService.find(id, ownerEmail));
    }

    /** Download do arquivo quando o relatório está READY (409 se ainda não está). */
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @AuthenticationPrincipal String ownerEmail) {
        ReportFile file = reportService.download(id, ownerEmail);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .contentLength(file.content().length)
                .body(file.content());
    }
}

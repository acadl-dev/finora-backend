package com.acadl.reports.report.controller;

import com.acadl.reports.report.exception.InvalidReportPeriodException;
import com.acadl.reports.report.exception.ReportGenerationException;
import com.acadl.reports.report.exception.TransactionSourceUnauthorizedException;
import com.acadl.reports.report.exception.TransactionSourceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ReportExceptionHandler {

    @ExceptionHandler(InvalidReportPeriodException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPeriod(InvalidReportPeriodException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleBadParam() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Datas devem estar no formato yyyy-MM-dd"));
    }

    @ExceptionHandler(TransactionSourceUnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized(TransactionSourceUnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(TransactionSourceUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleUnavailable(TransactionSourceUnavailableException ex) {
        log.error("Falha ao consultar o finora", ex.getCause());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ReportGenerationException.class)
    public ResponseEntity<Map<String, String>> handleGeneration(ReportGenerationException ex) {
        log.error("Erro ao gerar relatório", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Não foi possível gerar o relatório"));
    }
}

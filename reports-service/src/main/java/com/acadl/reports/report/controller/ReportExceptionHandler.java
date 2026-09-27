package com.acadl.reports.report.controller;

import com.acadl.reports.report.exception.InvalidReportPeriodException;
import com.acadl.reports.report.exception.ReportGenerationException;
import com.acadl.reports.report.exception.ReportNotFoundException;
import com.acadl.reports.report.exception.ReportNotReadyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> handleBadInput() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Dados inválidos. Datas devem estar no formato yyyy-MM-dd"));
    }

    @ExceptionHandler(ReportNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ReportNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ReportNotReadyException.class)
    public ResponseEntity<Map<String, String>> handleNotReady(ReportNotReadyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ReportGenerationException.class)
    public ResponseEntity<Map<String, String>> handleGeneration(ReportGenerationException ex) {
        log.error("Erro ao gerar relatório", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Não foi possível gerar o relatório"));
    }
}

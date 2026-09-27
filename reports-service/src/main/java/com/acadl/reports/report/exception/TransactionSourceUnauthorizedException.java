package com.acadl.reports.report.exception;

/** O finora recusou o token do usuário (expirado ou inválido). */
public class TransactionSourceUnauthorizedException extends RuntimeException {
    public TransactionSourceUnauthorizedException() {
        super("Sessão expirada. Faça login novamente.");
    }
}

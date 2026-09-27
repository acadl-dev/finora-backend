package com.acadl.reports.report.exception;

/** O microsserviço de transações (finora) não respondeu. */
public class TransactionSourceUnavailableException extends RuntimeException {
    public TransactionSourceUnavailableException(Throwable cause) {
        super("Serviço de transações indisponível no momento. Tente novamente.", cause);
    }
}

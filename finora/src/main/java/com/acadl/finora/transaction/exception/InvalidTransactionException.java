package com.acadl.finora.transaction.exception;

/**
 * Exceção de domínio lançada quando uma regra de negócio da transação é violada.
 */
public class InvalidTransactionException extends RuntimeException {
    public InvalidTransactionException(String message) {
        super(message);
    }
}

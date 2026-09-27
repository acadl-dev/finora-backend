package com.acadl.finora.transaction.exception;

import java.util.UUID;

/** A transação não existe ou não pertence ao usuário (não revelamos qual dos dois). */
public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(UUID transactionId) {
        super("Transação não encontrada: " + transactionId);
    }
}

package com.acadl.finora.transaction.mapper;

import com.acadl.finora.auth.model.User;
import com.acadl.finora.transaction.dto.CreateTransactionRequest;
import com.acadl.finora.transaction.dto.TransactionResponse;
import com.acadl.finora.transaction.model.Money;
import com.acadl.finora.transaction.model.Transaction;

public class TransactionMapper {

    public static Transaction toEntity(CreateTransactionRequest dto, User owner) {
        return Transaction.register(
                owner,
                dto.description(),
                Money.of(dto.amount()),
                dto.type(),
                dto.category(),
                dto.date()
        );
    }

    public static TransactionResponse toDTO(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getDescription(),
                transaction.getAmount().value(),
                transaction.signedAmount(),
                transaction.getType(),
                transaction.getCategory(),
                transaction.getDate(),
                transaction.getCreatedAt()
        );
    }
}

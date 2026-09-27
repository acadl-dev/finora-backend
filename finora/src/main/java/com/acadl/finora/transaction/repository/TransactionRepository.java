package com.acadl.finora.transaction.repository;

import com.acadl.finora.transaction.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findAllByUser_IdOrderByDateDescCreatedAtDesc(UUID userId);
}

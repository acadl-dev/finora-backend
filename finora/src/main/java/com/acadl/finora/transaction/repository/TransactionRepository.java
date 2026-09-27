package com.acadl.finora.transaction.repository;

import com.acadl.finora.transaction.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findAllByUser_IdOrderByDateDescCreatedAtDesc(UUID userId);

    /** Transações criadas antes da arquitetura de eventos (sem TransactionRegistered na outbox). */
    @Query("""
            select t from Transaction t
            where not exists (
                select o.id from OutboxEvent o
                where o.aggregateId = t.id and o.eventType = 'TransactionRegistered'
            )
            """)
    List<Transaction> findAllWithoutRegisteredEvent();
}

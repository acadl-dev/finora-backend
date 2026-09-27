package com.acadl.reports.ledger.repository;

import com.acadl.reports.ledger.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findAllByOwnerEmailAndRemovedFalse(String ownerEmail);
}

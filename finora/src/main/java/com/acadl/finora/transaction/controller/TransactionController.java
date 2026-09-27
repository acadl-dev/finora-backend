package com.acadl.finora.transaction.controller;

import com.acadl.finora.auth.model.Credential;
import com.acadl.finora.transaction.dto.CreateTransactionRequest;
import com.acadl.finora.transaction.dto.TransactionResponse;
import com.acadl.finora.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @RequestBody @Valid CreateTransactionRequest request,
            @AuthenticationPrincipal Credential credential
    ) {
        TransactionResponse created = transactionService.register(request, credential.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> list(
            @AuthenticationPrincipal Credential credential
    ) {
        return ResponseEntity.ok(transactionService.listByOwner(credential.getUser()));
    }
}

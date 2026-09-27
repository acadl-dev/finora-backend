package com.acadl.finora.transaction.service;

import com.acadl.finora.auth.model.User;
import com.acadl.finora.transaction.dto.CreateTransactionRequest;
import com.acadl.finora.transaction.dto.TransactionResponse;
import com.acadl.finora.transaction.mapper.TransactionMapper;
import com.acadl.finora.transaction.model.Transaction;
import com.acadl.finora.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Serviço de aplicação: orquestra o caso de uso (converter a entrada, delegar as
 * regras ao domínio e persistir). As regras de negócio ficam na entidade
 * {@link Transaction}, não aqui.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionResponse register(CreateTransactionRequest request, User owner) {
        Transaction transaction = TransactionMapper.toEntity(request, owner);
        Transaction saved = transactionRepository.save(transaction);
        return TransactionMapper.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listByOwner(User owner) {
        return transactionRepository.findAllByUser_IdOrderByDateDescCreatedAtDesc(owner.getId())
                .stream()
                .map(TransactionMapper::toDTO)
                .toList();
    }
}

package com.acadl.finora.transaction.service;

import com.acadl.finora.auth.model.User;
import com.acadl.finora.outbox.service.OutboxEventRecorder;
import com.acadl.finora.transaction.dto.CreateTransactionRequest;
import com.acadl.finora.transaction.dto.TransactionResponse;
import com.acadl.finora.transaction.exception.TransactionNotFoundException;
import com.acadl.finora.transaction.mapper.TransactionMapper;
import com.acadl.finora.transaction.model.Transaction;
import com.acadl.finora.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Serviço de aplicação: orquestra o caso de uso (converter a entrada, delegar as
 * regras ao domínio e persistir). As regras de negócio ficam na entidade
 * {@link Transaction}, não aqui.
 * <p>
 * Transactional Outbox: a transação e os eventos de domínio que ela gerou são
 * gravados na MESMA transação do banco. Ou os dois são salvos, ou nenhum — não
 * existe "salvou a transação mas perdeu o evento". Quem publica no RabbitMQ é o
 * OutboxRelay, de forma assíncrona.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final OutboxEventRecorder outboxEventRecorder;

    @Transactional
    public TransactionResponse register(CreateTransactionRequest request, User owner, String ownerEmail) {
        Transaction transaction = TransactionMapper.toEntity(request, owner, ownerEmail);
        Transaction saved = transactionRepository.save(transaction);
        outboxEventRecorder.record(transaction.pullDomainEvents());
        return TransactionMapper.toDTO(saved);
    }

    @Transactional
    public void remove(UUID transactionId, User owner, String ownerEmail) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        transaction.remove(owner.getId(), ownerEmail); // regra: só o dono exclui
        transactionRepository.delete(transaction);
        outboxEventRecorder.record(transaction.pullDomainEvents());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listByOwner(User owner) {
        return transactionRepository.findAllByUser_IdOrderByDateDescCreatedAtDesc(owner.getId())
                .stream()
                .map(TransactionMapper::toDTO)
                .toList();
    }
}

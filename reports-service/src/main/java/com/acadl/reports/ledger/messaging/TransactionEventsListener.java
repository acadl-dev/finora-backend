package com.acadl.reports.ledger.messaging;

import com.acadl.reports.ledger.service.LedgerProjectionService;
import com.acadl.reports.messaging.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Assinante (subscriber) dos eventos de transação do finora.
 * <p>
 * {@code concurrency = "1"}: um consumidor só nesta fila preserva a ordem dos
 * eventos de um mesmo usuário (registrou → excluiu). Em caso de erro, o Spring
 * tenta de novo com backoff (3x) e, se continuar falhando, a mensagem vai para a
 * DLQ {@code reports.transaction-events.dlq} para análise — sem travar a fila.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventsListener {

    private final LedgerProjectionService projectionService;

    @RabbitListener(queues = RabbitMQConfig.TRANSACTION_EVENTS_QUEUE, concurrency = "1")
    public void onTransactionEvent(TransactionEventMessage event) {
        log.debug("Evento recebido: {} {}", event.eventType(), event.eventId());
        projectionService.apply(event);
    }
}

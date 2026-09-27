package com.acadl.reports.report.messaging;

import com.acadl.reports.messaging.RabbitMQConfig;
import com.acadl.reports.report.model.ReportRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Envia o comando de geração para a fila SÓ DEPOIS do commit do pedido no banco
 * ({@code AFTER_COMMIT}). Assim o worker nunca recebe o id de um relatório que
 * ainda não existe (ou que sofreu rollback).
 * <p>
 * Se o RabbitMQ estiver indisponível neste instante, o pedido fica REQUESTED e o
 * {@code StaleReportDispatcher} reenvia o comando depois.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportCommandPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportRequested(ReportRequested event) {
        dispatch(event.reportId());
    }

    public void dispatch(UUID reportId) {
        GenerateReportCommand command = GenerateReportCommand.of(reportId);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.REPORTS_COMMANDS_EXCHANGE,
                    RabbitMQConfig.GENERATE_REPORT_ROUTING_KEY,
                    command,
                    message -> {
                        message.getMessageProperties().setMessageId(command.commandId().toString());
                        message.getMessageProperties().setType("GenerateReportCommand");
                        return message;
                    });
            log.debug("Comando de geração enviado para o relatório {}", reportId);
        } catch (AmqpException e) {
            log.warn("RabbitMQ indisponível ao enviar o relatório {}: {}. Será reenviado.", reportId, e.getMessage());
        }
    }
}

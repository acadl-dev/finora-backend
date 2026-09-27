package com.acadl.reports.report.messaging;

import com.acadl.reports.messaging.RabbitMQConfig;
import com.acadl.reports.report.service.ReportGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Worker de relatórios (Competing Consumers).
 * <p>
 * {@code concurrency = "2-4"}: de 2 a 4 consumidores nesta instância dividem a fila;
 * com mais instâncias do reports-service, mais workers — escala horizontal sem
 * mudar código. Falhas inesperadas: 3 tentativas com backoff e depois DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateReportCommandListener {

    private final ReportGenerationService generationService;

    @RabbitListener(queues = RabbitMQConfig.GENERATE_REPORT_QUEUE, concurrency = "2-4")
    public void onGenerateReport(GenerateReportCommand command) {
        generationService.generate(command.reportId());
    }

    /**
     * Dead Letter Channel: um comando que falhou em todas as tentativas chega aqui.
     * Registramos a falha no relatório para o usuário não ficar esperando para sempre.
     */
    @RabbitListener(queues = RabbitMQConfig.GENERATE_REPORT_DLQ)
    public void onDeadLetter(GenerateReportCommand command) {
        log.error("Comando {} do relatório {} foi para a DLQ", command.commandId(), command.reportId());
        generationService.markFailed(command.reportId(),
                "Não foi possível gerar o relatório após várias tentativas. Tente novamente.");
    }
}

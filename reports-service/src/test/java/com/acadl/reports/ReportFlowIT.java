package com.acadl.reports;

import com.acadl.reports.ledger.model.LedgerEntry;
import com.acadl.reports.ledger.repository.LedgerEntryRepository;
import com.acadl.reports.ledger.repository.ProcessedEventRepository;
import com.acadl.reports.messaging.RabbitMQConfig;
import com.acadl.reports.support.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de integração do reports-service com PostgreSQL e RabbitMQ REAIS (Testcontainers).
 * Os eventos são publicados no exchange finora.transactions exatamente no formato que o
 * finora usa, cobrindo: projeção, idempotência, lápides, DLQ e o relatório assíncrono.
 */
@SpringBootTest
@AutoConfigureMockMvc
// métricas ligadas: o teste de idempotência lê o contador finora.ledger.events.duplicated
@AutoConfigureObservability(metrics = true, tracing = false)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReportFlowIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AmqpAdmin amqpAdmin;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    void eventoRegistradoAlimentaAProjecao() throws Exception {
        String email = uniqueEmail();
        UUID transactionId = UUID.randomUUID();

        publish("transaction.registered", registered(UUID.randomUUID(), transactionId, email, "EXPENSE", "350.00"));

        LedgerEntry entry = await().atMost(Duration.ofSeconds(10))
                .until(() -> ledgerEntryRepository.findById(transactionId).orElse(null), e -> e != null);
        assertThat(entry.isRemoved()).isFalse();
        assertThat(entry.toReportEntry().signedAmount()).isEqualByComparingTo("-350.00");
    }

    @Test
    void eventoDuplicadoEhIgnorado() throws Exception {
        String email = uniqueEmail();
        UUID eventId = UUID.randomUUID();
        Map<String, Object> event = registered(eventId, UUID.randomUUID(), email, "INCOME", "100.00");
        double duplicatesBefore = duplicatedCount();

        publish("transaction.registered", event);
        publish("transaction.registered", event);   // reentrega da MESMA mensagem

        await().atMost(Duration.ofSeconds(10)).until(() -> duplicatedCount() > duplicatesBefore);
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
        assertThat(ledgerEntryRepository.findAllByOwnerEmailAndRemovedFalse(email)).hasSize(1);
    }

    @Test
    void exclusaoViraLapide() throws Exception {
        String email = uniqueEmail();
        UUID transactionId = UUID.randomUUID();
        publish("transaction.registered", registered(UUID.randomUUID(), transactionId, email, "EXPENSE", "50.00"));
        await().atMost(Duration.ofSeconds(10)).until(() -> ledgerEntryRepository.existsById(transactionId));

        publish("transaction.removed", removed(UUID.randomUUID(), transactionId, email));

        await().atMost(Duration.ofSeconds(10)).until(() ->
                ledgerEntryRepository.findById(transactionId).map(LedgerEntry::isRemoved).orElse(false));
        assertThat(ledgerEntryRepository.findAllByOwnerEmailAndRemovedFalse(email)).isEmpty();
    }

    @Test
    void exclusaoQueChegaAntesDoRegistroNaoDeixaATransacaoRessuscitar() throws Exception {
        String email = uniqueEmail();
        UUID transactionId = UUID.randomUUID();
        UUID registeredEventId = UUID.randomUUID();

        publish("transaction.removed", removed(UUID.randomUUID(), transactionId, email));
        publish("transaction.registered", registered(registeredEventId, transactionId, email, "EXPENSE", "70.00"));

        await().atMost(Duration.ofSeconds(10)).until(() -> processedEventRepository.existsById(registeredEventId));
        assertThat(ledgerEntryRepository.findById(transactionId)).get()
                .extracting(LedgerEntry::isRemoved).isEqualTo(true);
    }

    @Test
    void mensagemInvalidaVaiParaADeadLetterQueue() throws Exception {
        int before = messageCount(RabbitMQConfig.TRANSACTION_EVENTS_DLQ);
        Map<String, Object> incomplete = new LinkedHashMap<>();
        incomplete.put("eventId", UUID.randomUUID().toString());
        incomplete.put("eventType", "TransactionRegistered");
        incomplete.put("transactionId", UUID.randomUUID().toString());

        publish("transaction.registered", incomplete);

        await().atMost(Duration.ofSeconds(20))
                .until(() -> messageCount(RabbitMQConfig.TRANSACTION_EVENTS_DLQ) > before);
    }

    @Test
    void relatorioAssincronoFicaProntoComSaldoCorreto() throws Exception {
        String email = uniqueEmail();
        String token = token(email);
        UUID income = UUID.randomUUID();
        UUID expense = UUID.randomUUID();
        publish("transaction.registered", registered(UUID.randomUUID(), income, email, "INCOME", "1000.00"));
        publish("transaction.registered", registered(UUID.randomUUID(), expense, email, "EXPENSE", "400.00"));
        await().atMost(Duration.ofSeconds(10)).until(() ->
                ledgerEntryRepository.existsById(income) && ledgerEntryRepository.existsById(expense));

        // 1) pedido aceito na hora (202) com status REQUESTED
        String created = mockMvc.perform(post("/reports").header(AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String reportId = objectMapper.readTree(created).get("id").asText();

        // 2) o worker (fila reports.generate-report) conclui em segundo plano
        await().atMost(Duration.ofSeconds(20)).until(() -> "READY".equals(fetchReport(reportId, token).path("status").asText()));
        JsonNode ready = fetchReport(reportId, token);
        assertThat(ready.get("balance").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(ready.get("entryCount").asInt()).isEqualTo(2);

        // 3) o arquivo baixado é um Excel válido com o saldo no resumo
        byte[] file = mockMvc.perform(get("/reports/" + reportId + "/file").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet summary = workbook.getSheet("Resumo");
            assertThat(summary.getRow(8).getCell(1).getNumericCellValue()).isEqualTo(600.0);
            assertThat(workbook.getSheet("Lançamentos").getPhysicalNumberOfRows()).isGreaterThanOrEqualTo(3);
        }

        // 4) aparece no histórico do usuário
        mockMvc.perform(get("/reports/history").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(reportId));
    }

    @Test
    void relatorioDeOutroUsuarioNaoEhVisivel() throws Exception {
        String created = mockMvc.perform(post("/reports").header(AUTHORIZATION, "Bearer " + token(uniqueEmail()))
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String reportId = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(get("/reports/" + reportId).header(AUTHORIZATION, "Bearer " + token(uniqueEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    void periodoInvalidoRetorna400() throws Exception {
        mockMvc.perform(post("/reports").header(AUTHORIZATION, "Bearer " + token(uniqueEmail()))
                        .contentType(APPLICATION_JSON)
                        .content("{\"start\":\"2026-10-01\",\"end\":\"2026-09-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requisicaoSemTokenRetorna401() throws Exception {
        mockMvc.perform(get("/reports/history")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthCheckEstaDisponivel() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ---------------------------------------------------------------- helpers

    private static String uniqueEmail() {
        return "it-" + UUID.randomUUID() + "@finora.com";
    }

    private String token(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)))
                .compact();
    }

    private JsonNode fetchReport(String reportId, String token) throws Exception {
        String body = mockMvc.perform(get("/reports/" + reportId).header(AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private void publish(String routingKey, Map<String, Object> event) throws Exception {
        rabbitTemplate.send(RabbitMQConfig.TRANSACTIONS_EXCHANGE, routingKey,
                MessageBuilder.withBody(objectMapper.writeValueAsBytes(event))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .build());
    }

    private static Map<String, Object> registered(UUID eventId, UUID transactionId, String email,
                                                  String type, String amount) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId.toString());
        event.put("eventType", "TransactionRegistered");
        event.put("eventVersion", 1);
        event.put("occurredAt", Instant.now().toString());
        event.put("transactionId", transactionId.toString());
        event.put("userId", UUID.randomUUID().toString());
        event.put("userEmail", email);
        event.put("description", "Lançamento de teste");
        event.put("amount", new BigDecimal(amount));
        event.put("type", type);
        event.put("category", "Teste");
        event.put("date", "2026-09-15");
        return event;
    }

    private static Map<String, Object> removed(UUID eventId, UUID transactionId, String email) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId.toString());
        event.put("eventType", "TransactionRemoved");
        event.put("eventVersion", 1);
        event.put("occurredAt", Instant.now().toString());
        event.put("transactionId", transactionId.toString());
        event.put("userEmail", email);
        return event;
    }

    private double duplicatedCount() {
        Counter counter = meterRegistry.find("finora.ledger.events.duplicated").counter();
        return counter == null ? 0 : counter.count();
    }

    private int messageCount(String queue) {
        QueueInformation info = amqpAdmin.getQueueInfo(queue);
        return info == null ? 0 : info.getMessageCount();
    }
}

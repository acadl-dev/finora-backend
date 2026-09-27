package com.acadl.finora.transaction;

import com.acadl.finora.outbox.config.RabbitMQConfig;
import com.acadl.finora.outbox.repository.OutboxEventRepository;
import com.acadl.finora.support.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de integração do finora com PostgreSQL e RabbitMQ REAIS (Testcontainers).
 * Cobre o fluxo completo: autenticação JWT -> API REST -> banco -> outbox -> RabbitMQ.
 * Uma fila de teste assina o exchange finora.transactions, como o reports-service faz.
 */
@SpringBootTest
@AutoConfigureMockMvc
// o Spring Boot desliga a exportação de métricas nos testes; aqui religamos para validar /actuator/prometheus
@AutoConfigureObservability(metrics = true, tracing = false)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TransactionFlowIT {

    private static final String TEST_QUEUE = "it.finora.transaction-events";
    private static final String PASSWORD = "Senha123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AmqpAdmin amqpAdmin;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void bindTestQueue() {
        // durável: o RabbitMQ 4 não permite mais filas "transient non-exclusive"
        Queue queue = new Queue(TEST_QUEUE, true, false, false);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue)
                .to(new TopicExchange(RabbitMQConfig.TRANSACTIONS_EXCHANGE))
                .with("transaction.#"));
        amqpAdmin.purgeQueue(TEST_QUEUE, false);
    }

    @Test
    void cadastroGravaNaOutboxEPublicaTransactionRegistered() throws Exception {
        String email = uniqueEmail();
        String token = registerAndLogin(email);

        JsonNode created = createTransaction(token, "EXPENSE", "350");
        String transactionId = created.get("id").asText();
        assertThat(created.get("signedAmount").decimalValue()).isEqualByComparingTo("-350");

        JsonNode event = awaitEvent("TransactionRegistered", transactionId);
        assertThat(event.get("userEmail").asText()).isEqualTo(email);
        assertThat(event.get("amount").decimalValue()).isEqualByComparingTo("350");
        assertThat(event.get("type").asText()).isEqualTo("EXPENSE");
        assertThat(event.get("eventVersion").asInt()).isEqualTo(1);

        // a linha da outbox é marcada como publicada após a confirmação do broker
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outboxEventRepository.findAll())
                        .anyMatch(e -> e.getAggregateId().toString().equals(transactionId) && e.isPublished()));
    }

    @Test
    void exclusaoPublicaTransactionRemoved() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        String transactionId = createTransaction(token, "INCOME", "1200").get("id").asText();
        awaitEvent("TransactionRegistered", transactionId);

        mockMvc.perform(delete("/transactions/" + transactionId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        JsonNode event = awaitEvent("TransactionRemoved", transactionId);
        assertThat(event.get("transactionId").asText()).isEqualTo(transactionId);

        mockMvc.perform(get("/transactions").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void usuarioNaoExcluiTransacaoDeOutroUsuario() throws Exception {
        String owner = registerAndLogin(uniqueEmail());
        String intruder = registerAndLogin(uniqueEmail());
        String transactionId = createTransaction(owner, "EXPENSE", "80").get("id").asText();

        mockMvc.perform(delete("/transactions/" + transactionId).header(AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listaSomenteAsTransacoesDoProprioUsuario() throws Exception {
        String ana = registerAndLogin(uniqueEmail());
        String bruno = registerAndLogin(uniqueEmail());
        createTransaction(ana, "EXPENSE", "10");
        createTransaction(ana, "INCOME", "20");
        createTransaction(bruno, "EXPENSE", "30");

        mockMvc.perform(get("/transactions").header(AUTHORIZATION, bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void requisicaoSemTokenEhRecusada() throws Exception {
        mockMvc.perform(get("/transactions")).andExpect(status().isForbidden());
    }

    @Test
    void valorZeroEhRejeitadoComMensagemDeNegocio() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        mockMvc.perform(post("/transactions").header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"description":"Teste","amount":0,"type":"EXPENSE","date":"2026-09-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Valor deve ser maior que zero"));
    }

    @Test
    void loginComSenhaErradaRetorna401() throws Exception {
        String email = uniqueEmail();
        registerAndLogin(email);
        mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "errada"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthCheckEMetricasEstaoExpostos() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("finora_outbox_pending")));
    }

    // ---------------------------------------------------------------- helpers

    private static String uniqueEmail() {
        return "it-" + UUID.randomUUID() + "@finora.com";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content(json(Map.of("name", "Teste", "email", email, "password", PASSWORD))))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private JsonNode createTransaction(String token, String type, String amount) throws Exception {
        String body = mockMvc.perform(post("/transactions").header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"description":"Supermercado","amount":%s,"type":"%s","category":"Alimentação","date":"2026-09-01"}
                                """.formatted(amount, type)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    /** Lê a fila de teste até encontrar o evento esperado (ignora eventos de outros testes). */
    private JsonNode awaitEvent(String eventType, String transactionId) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(TEST_QUEUE, 1000);
            if (message == null) {
                continue;
            }
            JsonNode event = objectMapper.readTree(message.getBody());
            if (eventType.equals(event.path("eventType").asText())
                    && transactionId.equals(event.path("transactionId").asText())) {
                // messageId = eventId: é o que permite a deduplicação no consumidor
                assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.path("eventId").asText());
                assertThat(message.getMessageProperties().getContentType()).contains("json");
                return event;
            }
        }
        throw new AssertionError("Evento " + eventType + " da transação " + transactionId + " não foi publicado");
    }
}

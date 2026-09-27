package com.acadl.finora;

import com.acadl.finora.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** A aplicação inteira sobe com PostgreSQL e RabbitMQ reais (Testcontainers). */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FinoraApplicationTests {

	@Test
	void contextLoads() {
	}
}

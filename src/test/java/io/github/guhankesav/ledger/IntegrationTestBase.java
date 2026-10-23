package io.github.guhankesav.ledger;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Runs the full application against a real PostgreSQL in Docker. The container is started once
 * per test run (not per class) because Spring caches the application context across test classes;
 * Testcontainers' Ryuk sidecar removes it when the JVM exits.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected TestRestTemplate http;

    @SuppressWarnings("unchecked")
    protected UUID openAccount(String owner, String currency, long openingBalanceMinor) {
        ResponseEntity<Map> response = http.postForEntity("/api/v1/accounts",
                Map.of("ownerName", owner, "currency", currency, "openingBalanceMinor", openingBalanceMinor), Map.class);
        return UUID.fromString((String) response.getBody().get("id"));
    }

    @SuppressWarnings("unchecked")
    protected long balanceOf(UUID accountId) {
        Map<String, Object> body = http.getForObject("/api/v1/accounts/" + accountId, Map.class);
        return ((Number) body.get("balanceMinor")).longValue();
    }

    protected ResponseEntity<Map> transfer(String key, UUID from, UUID to, long amountMinor, String currency) {
        HttpHeaders headers = new HttpHeaders();
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        Map<String, Object> body = Map.of("fromAccountId", from, "toAccountId", to,
                "amountMinor", amountMinor, "currency", currency);
        return http.exchange("/api/v1/transfers", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    protected static String newKey() {
        return "key-" + UUID.randomUUID();
    }
}

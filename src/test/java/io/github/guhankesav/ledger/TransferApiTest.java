package io.github.guhankesav.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guhankesav.ledger.transfer.LedgerEntryRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TransferApiTest extends IntegrationTestBase {

    @Autowired
    private LedgerEntryRepository entries;

    @Test
    void openAccountReturnsCreatedWithOpeningBalance() {
        ResponseEntity<Map> response = http.postForEntity("/api/v1/accounts",
                Map.of("ownerName", "Ada", "currency", "USD", "openingBalanceMinor", 2_500), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(((Number) response.getBody().get("balanceMinor")).longValue()).isEqualTo(2_500);
    }

    @Test
    void transferMovesMoneyAndWritesBalancedEntries() {
        UUID from = openAccount("Ada", "USD", 1_000);
        UUID to = openAccount("Grace", "USD", 0);

        ResponseEntity<Map> response = transfer(newKey(), from, to, 400, "USD");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(balanceOf(from)).isEqualTo(600);
        assertThat(balanceOf(to)).isEqualTo(400);
        UUID transferId = UUID.fromString((String) response.getBody().get("id"));
        assertThat(entries.findByTransferId(transferId))
                .extracting(e -> e.getAmountMinor())
                .containsExactlyInAnyOrder(-400L, 400L);
        assertThat(entries.sumForAccount(from)).isEqualTo(600);
        assertThat(entries.sumForAccount(to)).isEqualTo(400);
    }

    @Test
    void retryWithSameKeyAndBodyReplaysWithoutMovingMoneyTwice() {
        UUID from = openAccount("Ada", "USD", 1_000);
        UUID to = openAccount("Grace", "USD", 0);
        String key = newKey();

        ResponseEntity<Map> first = transfer(key, from, to, 250, "USD");
        ResponseEntity<Map> retry = transfer(key, from, to, 250, "USD");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.getBody().get("id")).isEqualTo(first.getBody().get("id"));
        assertThat(balanceOf(from)).isEqualTo(750);
        assertThat(balanceOf(to)).isEqualTo(250);
    }

    @Test
    void reusingKeyWithDifferentBodyIsRejected() {
        UUID from = openAccount("Ada", "USD", 1_000);
        UUID to = openAccount("Grace", "USD", 0);
        String key = newKey();
        transfer(key, from, to, 100, "USD");

        ResponseEntity<Map> reused = transfer(key, from, to, 999, "USD");

        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(balanceOf(from)).isEqualTo(900);
    }

    @Test
    void insufficientFundsLeavesBalancesUntouchedAndFreesTheKey() {
        UUID from = openAccount("Ada", "USD", 100);
        UUID to = openAccount("Grace", "USD", 0);
        String key = newKey();

        ResponseEntity<Map> rejected = transfer(key, from, to, 101, "USD");

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejected.getHeaders().getContentType().toString()).contains("problem+json");
        assertThat(balanceOf(from)).isEqualTo(100);
        assertThat(balanceOf(to)).isZero();
        // The failed attempt rolled back, so the same key can be used for a valid retry.
        assertThat(transfer(key, from, to, 100, "USD").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void currencyMismatchIsRejected() {
        UUID usd = openAccount("Ada", "USD", 1_000);
        UUID eur = openAccount("Grace", "EUR", 0);

        assertThat(transfer(newKey(), usd, eur, 100, "USD").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(balanceOf(usd)).isEqualTo(1_000);
    }

    @Test
    void transferToSameAccountIsRejected() {
        UUID account = openAccount("Ada", "USD", 1_000);
        assertThat(transfer(newKey(), account, account, 100, "USD").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownAccountReturnsNotFound() {
        UUID from = openAccount("Ada", "USD", 1_000);
        assertThat(transfer(newKey(), from, UUID.randomUUID(), 100, "USD").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(balanceOf(from)).isEqualTo(1_000);
    }

    @Test
    void missingOrMalformedIdempotencyKeyIsRejected() {
        UUID from = openAccount("Ada", "USD", 1_000);
        UUID to = openAccount("Grace", "USD", 0);

        assertThat(transfer(null, from, to, 100, "USD").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(transfer("short", from, to, 100, "USD").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(balanceOf(from)).isEqualTo(1_000);
    }

    @Test
    void invalidAmountOrCurrencyIsRejected() {
        UUID from = openAccount("Ada", "USD", 1_000);
        UUID to = openAccount("Grace", "USD", 0);

        assertThat(transfer(newKey(), from, to, 0, "USD").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(transfer(newKey(), from, to, -5, "USD").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(transfer(newKey(), from, to, 100, "usd").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void entriesEndpointListsAccountHistory() {
        UUID from = openAccount("Ada", "USD", 1_000);
        UUID to = openAccount("Grace", "USD", 0);
        transfer(newKey(), from, to, 300, "USD");

        Map[] history = http.getForObject("/api/v1/accounts/" + from + "/entries", Map[].class);

        assertThat(history).extracting(e -> e.get("entryType")).containsExactlyInAnyOrder("OPENING", "DEBIT");
    }
}

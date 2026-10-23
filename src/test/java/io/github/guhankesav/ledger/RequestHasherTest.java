package io.github.guhankesav.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guhankesav.ledger.idempotency.RequestHasher;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestHasherTest {

    private final UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void sameRequestHashesTheSame() {
        assertThat(RequestHasher.transferHash(a, b, 500, "USD"))
                .isEqualTo(RequestHasher.transferHash(a, b, 500, "USD"))
                .hasSize(64);
    }

    @Test
    void anyFieldChangeChangesTheHash() {
        String base = RequestHasher.transferHash(a, b, 500, "USD");
        assertThat(RequestHasher.transferHash(b, a, 500, "USD")).isNotEqualTo(base);
        assertThat(RequestHasher.transferHash(a, b, 501, "USD")).isNotEqualTo(base);
        assertThat(RequestHasher.transferHash(a, b, 500, "EUR")).isNotEqualTo(base);
    }
}

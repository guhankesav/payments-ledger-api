package io.github.guhankesav.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guhankesav.ledger.transfer.LedgerEntryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TransferConcurrencyTest extends IntegrationTestBase {

    private static final int THREADS = 16;

    @Autowired
    private LedgerEntryRepository entries;

    @Test
    void opposingConcurrentTransfersConserveMoneyAndNeverOverdraw() throws Exception {
        UUID a = openAccount("Ada", "USD", 1_000);
        UUID b = openAccount("Grace", "USD", 1_000);

        List<Callable<HttpStatus>> tasks = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            boolean aToB = i % 2 == 0;
            long amount = 1 + (i % 7) * 40;
            tasks.add(() -> HttpStatus.valueOf(
                    transfer(newKey(), aToB ? a : b, aToB ? b : a, amount, "USD").getStatusCode().value()));
        }
        List<HttpStatus> statuses = runAllAtOnce(tasks);

        // Every request either succeeded or was cleanly refused for lack of funds: no deadlocks, no 500s.
        assertThat(statuses).allMatch(s -> s == HttpStatus.CREATED || s == HttpStatus.CONFLICT);
        assertThat(balanceOf(a) + balanceOf(b)).isEqualTo(2_000);
        assertThat(balanceOf(a)).isNotNegative();
        assertThat(balanceOf(b)).isNotNegative();
        assertThat(entries.sumForAccount(a)).isEqualTo(balanceOf(a));
        assertThat(entries.sumForAccount(b)).isEqualTo(balanceOf(b));
    }

    @Test
    void concurrentRetriesWithOneKeyMoveMoneyExactlyOnce() throws Exception {
        UUID from = openAccount("Ada", "USD", 1_000);
        UUID to = openAccount("Grace", "USD", 0);
        String key = newKey();

        List<Callable<ResponseEntity<Map>>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> transfer(key, from, to, 100, "USD"));
        }
        List<ResponseEntity<Map>> responses = runAllAtOnce(tasks);

        assertThat(responses).filteredOn(r -> r.getStatusCode() == HttpStatus.CREATED).hasSize(1);
        assertThat(responses).allMatch(r -> r.getStatusCode() == HttpStatus.CREATED || r.getStatusCode() == HttpStatus.OK);
        assertThat(responses).extracting(r -> r.getBody().get("id")).containsOnly(responses.get(0).getBody().get("id"));
        assertThat(balanceOf(from)).isEqualTo(900);
        assertThat(balanceOf(to)).isEqualTo(100);
    }

    /** Releases all tasks together from a latch to maximise contention on the same rows and keys. */
    private static <T> List<T> runAllAtOnce(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}

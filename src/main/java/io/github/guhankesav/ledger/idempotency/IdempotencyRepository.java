package io.github.guhankesav.ledger.idempotency;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {

    /**
     * Claims a key. Returns 1 if this transaction now owns it, 0 if it already existed.
     * A concurrent claim on the same key blocks on the primary-key index until the first
     * transaction commits (then returns 0) or rolls back (then this insert succeeds).
     */
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_keys (idempotency_key, request_hash, created_at)
            VALUES (:key, :hash, now())
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("key") String key, @Param("hash") String requestHash);

    @Modifying
    @Query(value = "UPDATE idempotency_keys SET transfer_id = :transferId WHERE idempotency_key = :key",
            nativeQuery = true)
    int attachTransfer(@Param("key") String key, @Param("transferId") UUID transferId);
}

package io.github.guhankesav.ledger.transfer;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable page);

    List<LedgerEntry> findByTransferId(UUID transferId);

    @Query("select coalesce(sum(e.amountMinor), 0) from LedgerEntry e where e.accountId = :accountId")
    long sumForAccount(@Param("accountId") UUID accountId);
}

package io.github.guhankesav.ledger.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Amounts are integer minor units (cents) so arithmetic is exact. */
    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(String ownerName, String currency, long balanceMinor) {
        this.id = UUID.randomUUID();
        this.ownerName = ownerName;
        this.currency = currency;
        this.balanceMinor = balanceMinor;
        this.createdAt = Instant.now();
    }

    public void debit(long amountMinor) {
        if (amountMinor > balanceMinor) {
            throw new InsufficientFundsException(id);
        }
        balanceMinor -= amountMinor;
    }

    public void credit(long amountMinor) {
        balanceMinor = Math.addExact(balanceMinor, amountMinor);
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package io.github.guhankesav.ledger.account;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId) {
        super("Account " + accountId + " has insufficient funds");
    }
}

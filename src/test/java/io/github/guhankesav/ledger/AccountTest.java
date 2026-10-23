package io.github.guhankesav.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guhankesav.ledger.account.Account;
import io.github.guhankesav.ledger.account.InsufficientFundsException;
import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void debitAndCreditMoveTheBalance() {
        Account account = new Account("Ada", "USD", 1_000);
        account.debit(300);
        account.credit(50);
        assertThat(account.getBalanceMinor()).isEqualTo(750);
    }

    @Test
    void debitCanEmptyTheAccountExactly() {
        Account account = new Account("Ada", "USD", 1_000);
        account.debit(1_000);
        assertThat(account.getBalanceMinor()).isZero();
    }

    @Test
    void overdraftIsRejectedAndBalanceUnchanged() {
        Account account = new Account("Ada", "USD", 100);
        assertThatThrownBy(() -> account.debit(101)).isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getBalanceMinor()).isEqualTo(100);
    }

    @Test
    void creditOverflowIsRejected() {
        Account account = new Account("Ada", "USD", Long.MAX_VALUE);
        assertThatThrownBy(() -> account.credit(1)).isInstanceOf(ArithmeticException.class);
    }
}

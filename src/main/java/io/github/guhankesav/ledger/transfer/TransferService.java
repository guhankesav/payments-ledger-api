package io.github.guhankesav.ledger.transfer;

import io.github.guhankesav.ledger.account.Account;
import io.github.guhankesav.ledger.account.AccountNotFoundException;
import io.github.guhankesav.ledger.account.AccountRepository;
import io.github.guhankesav.ledger.idempotency.IdempotencyConflictException;
import io.github.guhankesav.ledger.idempotency.IdempotencyRecord;
import io.github.guhankesav.ledger.idempotency.IdempotencyRepository;
import io.github.guhankesav.ledger.idempotency.RequestHasher;
import io.github.guhankesav.ledger.transfer.TransferExceptions.CurrencyMismatchException;
import io.github.guhankesav.ledger.transfer.TransferExceptions.InvalidTransferException;
import io.github.guhankesav.ledger.transfer.TransferExceptions.TransferNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    public record TransferCommand(UUID fromAccountId, UUID toAccountId, long amountMinor, String currency) {
    }

    /** {@code replayed} is true when an earlier request with the same idempotency key already did the work. */
    public record TransferResult(Transfer transfer, boolean replayed) {
    }

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerEntryRepository entries;
    private final IdempotencyRepository idempotency;

    public TransferService(AccountRepository accounts, TransferRepository transfers,
                           LedgerEntryRepository entries, IdempotencyRepository idempotency) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.entries = entries;
        this.idempotency = idempotency;
    }

    /**
     * Moves money between two accounts exactly once per idempotency key.
     *
     * <p>Everything runs in one transaction, so a failed transfer (insufficient funds, unknown
     * account) also releases its key and the client may retry with the same key.
     */
    @Transactional
    public TransferResult transfer(String idempotencyKey, TransferCommand command) {
        if (command.fromAccountId().equals(command.toAccountId())) {
            throw new InvalidTransferException("Source and destination accounts must differ");
        }
        String hash = RequestHasher.transferHash(
                command.fromAccountId(), command.toAccountId(), command.amountMinor(), command.currency());

        if (idempotency.claim(idempotencyKey, hash) == 0) {
            return replay(idempotencyKey, hash);
        }

        // Lock both rows in a global order (by id) so two opposite transfers cannot deadlock.
        UUID first = command.fromAccountId().compareTo(command.toAccountId()) < 0
                ? command.fromAccountId() : command.toAccountId();
        UUID second = first.equals(command.fromAccountId()) ? command.toAccountId() : command.fromAccountId();
        Account firstLocked = lock(first);
        Account secondLocked = lock(second);
        Account from = firstLocked.getId().equals(command.fromAccountId()) ? firstLocked : secondLocked;
        Account to = from == firstLocked ? secondLocked : firstLocked;

        requireCurrency(from, command.currency());
        requireCurrency(to, command.currency());

        from.debit(command.amountMinor());
        to.credit(command.amountMinor());

        Transfer transfer = transfers.saveAndFlush(
                new Transfer(from.getId(), to.getId(), command.amountMinor(), command.currency()));
        entries.saveAll(List.of(
                new LedgerEntry(from.getId(), transfer.getId(), EntryType.DEBIT, -command.amountMinor()),
                new LedgerEntry(to.getId(), transfer.getId(), EntryType.CREDIT, command.amountMinor())));
        entries.flush();
        idempotency.attachTransfer(idempotencyKey, transfer.getId());
        return new TransferResult(transfer, false);
    }

    @Transactional(readOnly = true)
    public Transfer get(UUID id) {
        return transfers.findById(id).orElseThrow(() -> new TransferNotFoundException(id));
    }

    private TransferResult replay(String key, String hash) {
        IdempotencyRecord record = idempotency.findById(key)
                .orElseThrow(() -> new IllegalStateException("Claimed idempotency key vanished: " + key));
        if (!record.getRequestHash().equals(hash)) {
            throw new IdempotencyConflictException("Idempotency key was already used with a different request");
        }
        if (record.getTransferId() == null) {
            throw new IdempotencyConflictException("A request with this idempotency key is still in progress");
        }
        return new TransferResult(get(record.getTransferId()), true);
    }

    private Account lock(UUID id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private static void requireCurrency(Account account, String currency) {
        if (!account.getCurrency().equals(currency)) {
            throw new CurrencyMismatchException(account.getId(), account.getCurrency(), currency);
        }
    }
}

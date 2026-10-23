package io.github.guhankesav.ledger.account;

import io.github.guhankesav.ledger.transfer.EntryType;
import io.github.guhankesav.ledger.transfer.LedgerEntry;
import io.github.guhankesav.ledger.transfer.LedgerEntryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final int MAX_ENTRIES = 100;

    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;

    public AccountService(AccountRepository accounts, LedgerEntryRepository entries) {
        this.accounts = accounts;
        this.entries = entries;
    }

    @Transactional
    public Account open(String ownerName, String currency, long openingBalanceMinor) {
        Account account = accounts.save(new Account(ownerName, currency, openingBalanceMinor));
        if (openingBalanceMinor > 0) {
            entries.save(new LedgerEntry(account.getId(), null, EntryType.OPENING, openingBalanceMinor));
        }
        return account;
    }

    @Transactional(readOnly = true)
    public Account get(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> recentEntries(UUID id) {
        get(id);
        return entries.findByAccountIdOrderByCreatedAtDesc(id, PageRequest.of(0, MAX_ENTRIES));
    }
}

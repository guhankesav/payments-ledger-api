package io.github.guhankesav.ledger.account;

import io.github.guhankesav.ledger.transfer.LedgerEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    public record OpenAccountRequest(
            @NotBlank @Size(max = 100) String ownerName,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 code") String currency,
            @PositiveOrZero long openingBalanceMinor) {
    }

    public record AccountResponse(UUID id, String ownerName, String currency, long balanceMinor, Instant createdAt) {
        static AccountResponse of(Account a) {
            return new AccountResponse(a.getId(), a.getOwnerName(), a.getCurrency(), a.getBalanceMinor(), a.getCreatedAt());
        }
    }

    public record EntryResponse(UUID id, UUID transferId, String entryType, long amountMinor, Instant createdAt) {
        static EntryResponse of(LedgerEntry e) {
            return new EntryResponse(e.getId(), e.getTransferId(), e.getEntryType().name(), e.getAmountMinor(), e.getCreatedAt());
        }
    }

    @PostMapping
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        Account account = service.open(request.ownerName(), request.currency(), request.openingBalanceMinor());
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.getId())).body(AccountResponse.of(account));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.of(service.get(id));
    }

    @GetMapping("/{id}/entries")
    public List<EntryResponse> entries(@PathVariable UUID id) {
        return service.recentEntries(id).stream().map(EntryResponse::of).toList();
    }
}

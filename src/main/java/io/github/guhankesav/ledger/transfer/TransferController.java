package io.github.guhankesav.ledger.transfer;

import io.github.guhankesav.ledger.transfer.TransferService.TransferCommand;
import io.github.guhankesav.ledger.transfer.TransferService.TransferResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    public record TransferRequest(
            @NotNull UUID fromAccountId,
            @NotNull UUID toAccountId,
            @Positive long amountMinor,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 code") String currency) {
    }

    public record TransferResponse(UUID id, UUID fromAccountId, UUID toAccountId, long amountMinor,
                                   String currency, Instant createdAt) {
        static TransferResponse of(Transfer t) {
            return new TransferResponse(t.getId(), t.getFromAccountId(), t.getToAccountId(),
                    t.getAmountMinor(), t.getCurrency(), t.getCreatedAt());
        }
    }

    /** First call returns 201 Created; a retry with the same key and body returns 200 with the original transfer. */
    @PostMapping
    public ResponseEntity<TransferResponse> create(
            @RequestHeader(IDEMPOTENCY_KEY)
            @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "must be 8-64 characters of [A-Za-z0-9_-]")
            String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        TransferResult result = service.transfer(idempotencyKey, new TransferCommand(
                request.fromAccountId(), request.toAccountId(), request.amountMinor(), request.currency()));
        TransferResponse body = TransferResponse.of(result.transfer());
        if (result.replayed()) {
            return ResponseEntity.status(HttpStatus.OK).header(REPLAYED, "true").body(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/transfers/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable UUID id) {
        return TransferResponse.of(service.get(id));
    }
}

package io.github.guhankesav.ledger.error;

import io.github.guhankesav.ledger.account.AccountNotFoundException;
import io.github.guhankesav.ledger.account.InsufficientFundsException;
import io.github.guhankesav.ledger.idempotency.IdempotencyConflictException;
import io.github.guhankesav.ledger.transfer.TransferExceptions.CurrencyMismatchException;
import io.github.guhankesav.ledger.transfer.TransferExceptions.InvalidTransferException;
import io.github.guhankesav.ledger.transfer.TransferExceptions.TransferNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps domain failures to RFC 9457 problem details. Framework errors (validation, missing
 * headers, malformed JSON) are handled by the base class and also return problem details.
 * Unexpected exceptions fall through to Spring's default 500, which never exposes internals.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({AccountNotFoundException.class, TransferNotFoundException.class})
    ProblemDetail notFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail insufficientFunds(InsufficientFundsException e) {
        return problem(HttpStatus.CONFLICT, "Insufficient funds", e);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyConflictException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key conflict", e);
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    ProblemDetail currencyMismatch(CurrencyMismatchException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Currency mismatch", e);
    }

    @ExceptionHandler(InvalidTransferException.class)
    ProblemDetail invalidTransfer(InvalidTransferException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid transfer", e);
    }

    private static ProblemDetail problem(HttpStatus status, String title, RuntimeException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        detail.setTitle(title);
        return detail;
    }
}

package io.github.guhankesav.ledger.transfer;

import java.util.UUID;

/** Domain failures a client can act on; each maps to one HTTP status in ApiExceptionHandler. */
public final class TransferExceptions {

    private TransferExceptions() {
    }

    public static class InvalidTransferException extends RuntimeException {
        public InvalidTransferException(String message) {
            super(message);
        }
    }

    public static class CurrencyMismatchException extends RuntimeException {
        public CurrencyMismatchException(UUID accountId, String accountCurrency, String requested) {
            super("Account " + accountId + " holds " + accountCurrency + ", transfer is in " + requested);
        }
    }

    public static class TransferNotFoundException extends RuntimeException {
        public TransferNotFoundException(UUID transferId) {
            super("Transfer " + transferId + " not found");
        }
    }
}

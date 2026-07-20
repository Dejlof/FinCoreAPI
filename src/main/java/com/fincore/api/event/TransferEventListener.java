package com.fincore.api.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Slf4j
@Component
public class TransferEventListener {

    /**
     * AFTER_COMMIT so this only fires once the transfer's balance changes are
     * durable - an event raised inside a transaction that later rolls back never
     * reaches here. {@code @Async} keeps it off the request thread; wire a real
     * email/SMS/push notification in below.
     */
    @Async
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onTransferCompleted(TransferCompletedEvent event) {
        log.info("Transfer {} settled: {} -> {} ({} {})",
                event.reference(), event.sourceAccountNumber(),
                event.destinationAccountNumber(), event.amount(), event.currency());
    }
}

package com.fincore.api.service.impl;

import com.fincore.api.dto.request.FundAccountRequest;
import com.fincore.api.dto.response.FundAccountResponse;
import com.fincore.api.entity.Account;
import com.fincore.api.entity.Transaction;
import com.fincore.api.entity.enums.AccountStatus;
import com.fincore.api.entity.enums.TransactionStatus;
import com.fincore.api.entity.enums.TransactionType;
import com.fincore.api.exception.ApiException;
import com.fincore.api.exception.ResourceNotFoundException;
import com.fincore.api.integration.paystack.PaystackClient;
import com.fincore.api.integration.paystack.PaystackProperties;
import com.fincore.api.integration.paystack.dto.InitializeTransactionData;
import com.fincore.api.integration.paystack.dto.VerifyTransactionData;
import com.fincore.api.repository.AccountRepository;
import com.fincore.api.repository.TransactionRepository;
import com.fincore.api.service.PaymentService;
import com.fincore.api.util.ReferenceGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    /** Give the webhook a head start before the reconciliation job double-checks a reference. */
    private static final Duration WEBHOOK_GRACE_PERIOD = Duration.ofMinutes(2);

    /** Stop polling Paystack for references that never resolve; the checkout link has long expired. */
    private static final Duration MAX_PENDING_AGE = Duration.ofHours(24);

    /** Cap concurrent Paystack verify calls per reconciliation pass so a large backlog doesn't burst-fire 200 requests at once. */
    private static final int MAX_CONCURRENT_RECONCILIATIONS = 20;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PaystackClient paystackClient;
    private final PaystackProperties paystackProperties;

    /**
     * Self-injected proxy so the reconciliation loop's per-reference calls go
     * through Spring's transactional advice individually, instead of sharing
     * one transaction (and its locks) across the whole batch. {@code @Lazy}
     * avoids the circular-dependency error self-injection would otherwise cause.
     *
     * <p>Note: this constructor is written out by hand (instead of relying on
     * {@code @RequiredArgsConstructor}) because Lombok does not copy field
     * annotations onto the generated constructor parameters, so a {@code @Lazy}
     * field annotation alone would have no effect on Spring's injection.
     */
    private final PaymentService self;

    public PaymentServiceImpl(AccountRepository accountRepository,
                               TransactionRepository transactionRepository,
                               PaystackClient paystackClient,
                               PaystackProperties paystackProperties,
                               @Lazy PaymentService self) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.paystackClient = paystackClient;
        this.paystackProperties = paystackProperties;
        this.self = self;
    }

    @Override
    public FundAccountResponse initiateFunding(String userEmail, FundAccountRequest request, String idempotencyKey) {
        try {
            return self.initiateFundingInTransaction(userEmail, request, idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            // Postgres aborts the whole transaction on a unique-key violation, so the
            // losing side of a concurrent duplicate-key race can't just re-query inside
            // the same transaction - it has to retry in a fresh one. By the time Postgres
            // reports the conflict, the winner's row is guaranteed committed (it blocked
            // our insert until then), so the retry's own idempotency check finds it.
            if (idempotencyKey == null) {
                throw e;
            }
            return self.initiateFundingInTransaction(userEmail, request, idempotencyKey);
        }
    }

    @Override
    @Transactional
    public FundAccountResponse initiateFundingInTransaction(String userEmail, FundAccountRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Transaction transaction = existing.get();
                return new FundAccountResponse(transaction.getReference(), transaction.getAuthorizationUrl());
            }
        }

        Account account = accountRepository.findByAccountNumber(request.accountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account", "accountNumber", request.accountNumber()));

        if (!account.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw new AccessDeniedException("Account does not belong to the authenticated user");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(
                    "Account %s is not active".formatted(account.getAccountNumber()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        String reference = ReferenceGenerator.generate();
        InitializeTransactionData data = paystackClient.initializeTransaction(
                userEmail, request.amount(), reference, paystackProperties.callbackUrl());

        transactionRepository.save(Transaction.builder()
                .reference(reference)
                .destinationAccount(account)
                .amount(request.amount())
                .currency(account.getCurrency())
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .narration("Wallet funding via Paystack")
                .idempotencyKey(idempotencyKey)
                .authorizationUrl(data.authorizationUrl())
                .build());

        log.info("Initiated Paystack funding {} for account {}", reference, account.getAccountNumber());
        return new FundAccountResponse(reference, data.authorizationUrl());
    }

    @Override
    @Transactional
    public void confirmFunding(String reference) {
        // Locked fetch with minimal lock scope: validates transaction exists and is not already settled.
        // Lock is released immediately after status check, before calling external Paystack API.
        Transaction transaction = transactionRepository.findByReferenceForUpdate(reference).orElse(null);
        if (transaction == null) {
            log.warn("Received Paystack confirmation for unknown reference {}", reference);
            return;
        }
        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            return;
        }
        // Lock released here at end of transaction
    }

    @Transactional
    public void settleFunding(String reference) {
        // Check if transaction is stale (dropped by webhook, never to be resolved)
        Transaction transaction = transactionRepository.findByReference(reference).orElse(null);
        if (transaction == null || transaction.getStatus() == TransactionStatus.COMPLETED) {
            return;
        }
        if (transaction.getCreatedAt().isBefore(Instant.now().minus(MAX_PENDING_AGE))) {
            markFundingExpired(reference, transaction.getCreatedAt());
            return;
        }

        // Call Paystack API first (no lock held), then re-acquire lock to apply settlement.
        // This avoids holding a database lock during network I/O.
        VerifyTransactionData verified = paystackClient.verifyTransactionAsync(reference)
                .block();

        if (verified == null || !verified.isSuccessful()) {
            markFundingFailed(reference, verified != null ? verified.gatewayResponse() : "null response");
            return;
        }

        // Re-acquire lock to apply settlement
        settleIfAmountMatches(reference, verified);
    }

    @Transactional
    private void markFundingExpired(String reference, Instant createdAt) {
        Transaction transaction = transactionRepository.findByReferenceForUpdate(reference).orElse(null);
        if (transaction != null && transaction.getStatus() != TransactionStatus.COMPLETED) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            log.warn("Expired stale pending funding {} (created {})", reference, createdAt);
        }
    }

    @Transactional
    private void markFundingFailed(String reference, String reason) {
        Transaction transaction = transactionRepository.findByReferenceForUpdate(reference).orElse(null);
        if (transaction != null && transaction.getStatus() != TransactionStatus.COMPLETED) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            log.warn("Paystack payment {} did not succeed: {}", reference, reason);
        }
    }

    @Transactional
    private void settleIfAmountMatches(String reference, VerifyTransactionData verified) {
        Transaction transaction = transactionRepository.findByReferenceForUpdate(reference).orElse(null);
        if (transaction == null || transaction.getStatus() == TransactionStatus.COMPLETED) {
            return;
        }

        BigDecimal expectedKobo = transaction.getAmount().multiply(BigDecimal.valueOf(100));
        if (BigDecimal.valueOf(verified.amount()).compareTo(expectedKobo) != 0) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            log.error("Paystack amount mismatch for {}: expected {} kobo, got {} kobo",
                    reference, expectedKobo, verified.amount());
            return;
        }

        Account account = accountRepository.findByAccountNumberForUpdate(
                        transaction.getDestinationAccount().getAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account", "accountNumber", transaction.getDestinationAccount().getAccountNumber()));

        account.credit(transaction.getAmount());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(Instant.now());
        transactionRepository.save(transaction);

        log.info("Confirmed Paystack funding {}: credited {} {} to account {}",
                reference, transaction.getAmount(), account.getCurrency(), account.getAccountNumber());
    }

    /**
     * Runs independently of the webhook: catches references where Paystack's
     * webhook call was never delivered, was dropped, or errored out silently.
     * Each stale PENDING deposit is re-verified directly against Paystack's
     * API - the same source of truth the webhook path uses - so a missing
     * webhook never leaves a payment stuck. Delegates each reference to
     * {@code confirmFunding} through the {@link #self} proxy so every item
     * gets its own transaction and lock scope, rather than the whole batch
     * sharing one long-held transaction. Each reference is dispatched to its
     * own virtual thread since {@code confirmFunding} blocks on a Paystack
     * HTTP call - running the batch concurrently avoids paying that network
     * latency serially, item by item. A semaphore caps how many of those
     * calls are in flight at once, so a large backlog can't burst-fire
     * hundreds of simultaneous requests at Paystack.
     */
    @Override
    @Scheduled(fixedDelayString = "${paystack.reconciliation.interval-ms}")
    public void reconcilePendingFundings() {
        Instant graceCutoff = Instant.now().minus(WEBHOOK_GRACE_PERIOD);

        List<Transaction> pending = transactionRepository
                .findFirst200ByTypeAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        TransactionType.DEPOSIT, TransactionStatus.PENDING, graceCutoff);

        Semaphore concurrencyLimiter = new Semaphore(MAX_CONCURRENT_RECONCILIATIONS);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Transaction transaction : pending) {
                concurrencyLimiter.acquireUninterruptibly();
                executor.submit(() -> {
                    try {
                        String reference = transaction.getReference();
                        self.confirmFunding(reference);  // Quick lock check, no external calls
                        self.settleFunding(reference);   // Call Paystack async, re-lock to settle
                    } catch (Exception e) {
                        log.error("Reconciliation failed for funding {}", transaction.getReference(), e);
                    } finally {
                        concurrencyLimiter.release();
                    }
                });
            }
        } // try-with-resources awaits every submitted task before closing
    }

}

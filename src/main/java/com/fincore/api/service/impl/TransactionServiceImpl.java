package com.fincore.api.service.impl;

import com.fincore.api.dto.request.DepositRequest;
import com.fincore.api.dto.request.TransferRequest;
import com.fincore.api.dto.response.PagedResponse;
import com.fincore.api.dto.response.TransactionResponse;
import com.fincore.api.entity.Account;
import com.fincore.api.entity.Transaction;
import com.fincore.api.entity.enums.AccountStatus;
import com.fincore.api.entity.enums.TransactionStatus;
import com.fincore.api.entity.enums.TransactionType;
import com.fincore.api.exception.ApiException;
import com.fincore.api.exception.InsufficientBalanceException;
import com.fincore.api.exception.ResourceNotFoundException;
import com.fincore.api.event.TransferCompletedEvent;
import com.fincore.api.mapper.TransactionMapper;
import com.fincore.api.repository.AccountRepository;
import com.fincore.api.repository.TransactionRepository;
import com.fincore.api.service.TransactionService;
import com.fincore.api.util.ReferenceGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionMapper transactionMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Self-injected proxy so a retry after a concurrent idempotency-key race
     * (see {@link #transfer}/{@link #deposit}) runs in its own fresh
     * transaction. {@code @Lazy} avoids the circular-dependency error
     * self-injection would otherwise cause.
     *
     * <p>Constructor is written out by hand (instead of relying on
     * {@code @RequiredArgsConstructor}) because Lombok does not copy field
     * annotations onto the generated constructor parameters, so a
     * {@code @Lazy} field annotation alone would have no effect on Spring's
     * injection.
     */
    private final TransactionService self;

    public TransactionServiceImpl(TransactionRepository transactionRepository,
                                   AccountRepository accountRepository,
                                   TransactionMapper transactionMapper,
                                   ApplicationEventPublisher eventPublisher,
                                   @Lazy TransactionService self) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.transactionMapper = transactionMapper;
        this.eventPublisher = eventPublisher;
        this.self = self;
    }

    @Override
    public TransactionResponse transfer(String userEmail, TransferRequest request, String idempotencyKey) {
        try {
            return self.transferInTransaction(userEmail, request, idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            // Postgres aborts the whole transaction on a unique-key violation, so the
            // losing side of a concurrent duplicate-key race can't just re-query inside
            // the same transaction - it has to retry in a fresh one. By the time Postgres
            // reports the conflict, the winner's row is guaranteed committed (it blocked
            // our insert until then), so the retry's own idempotency check finds it.
            if (idempotencyKey == null) {
                throw e;
            }
            return self.transferInTransaction(userEmail, request, idempotencyKey);
        }
    }

    @Override
    @Transactional
    public TransactionResponse transferInTransaction(String userEmail, TransferRequest request, String idempotencyKey) {
        // Idempotency: replay the stored result for a repeated key
        if (idempotencyKey != null) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return transactionMapper.toResponse(existing.get());
            }
        }

        if (request.sourceAccountNumber().equals(request.destinationAccountNumber())) {
            throw new ApiException("Source and destination accounts must differ", HttpStatus.BAD_REQUEST);
        }

        // Lock accounts in a deterministic order to prevent deadlocks
        String first = request.sourceAccountNumber().compareTo(request.destinationAccountNumber()) < 0
                ? request.sourceAccountNumber() : request.destinationAccountNumber();
        String second = first.equals(request.sourceAccountNumber())
                ? request.destinationAccountNumber() : request.sourceAccountNumber();

        Account firstLocked = lockAccount(first);
        Account secondLocked = lockAccount(second);

        Account source = firstLocked.getAccountNumber().equals(request.sourceAccountNumber())
                ? firstLocked : secondLocked;
        Account destination = source == firstLocked ? secondLocked : firstLocked;

        if (!source.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw new AccessDeniedException("Source account does not belong to the authenticated user");
        }
        assertActive(source);
        assertActive(destination);
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new ApiException("Cross-currency transfers are not supported", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!source.hasSufficientBalance(request.amount())) {
            throw new InsufficientBalanceException();
        }

        source.debit(request.amount());
        destination.credit(request.amount());

        Transaction transaction = transactionRepository.save(Transaction.builder()
                .reference(ReferenceGenerator.generate())
                .sourceAccount(source)
                .destinationAccount(destination)
                .amount(request.amount())
                .currency(source.getCurrency())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .narration(request.narration())
                .idempotencyKey(idempotencyKey)
                .completedAt(Instant.now())
                .build());

        log.info("Transfer {} completed: {} -> {} ({} {})",
                transaction.getReference(), source.getAccountNumber(),
                destination.getAccountNumber(), request.amount(), source.getCurrency());

        eventPublisher.publishEvent(new TransferCompletedEvent(
                transaction.getReference(), source.getAccountNumber(),
                destination.getAccountNumber(), request.amount(), source.getCurrency()));

        return transactionMapper.toResponse(transaction);
    }

    @Override
    public TransactionResponse deposit(DepositRequest request, String idempotencyKey) {
        try {
            return self.depositInTransaction(request, idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            if (idempotencyKey == null) {
                throw e;
            }
            return self.depositInTransaction(request, idempotencyKey);
        }
    }

    @Override
    @Transactional
    public TransactionResponse depositInTransaction(DepositRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return transactionMapper.toResponse(existing.get());
            }
        }

        Account account = lockAccount(request.accountNumber());
        assertActive(account);
        account.credit(request.amount());

        Transaction transaction = transactionRepository.save(Transaction.builder()
                .reference(ReferenceGenerator.generate())
                .destinationAccount(account)
                .amount(request.amount())
                .currency(account.getCurrency())
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .narration(request.narration())
                .idempotencyKey(idempotencyKey)
                .completedAt(Instant.now())
                .build());

        return transactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getByReference(String userEmail, String reference) {
        Transaction transaction = transactionRepository.findByReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "reference", reference));
        assertParticipant(userEmail, transaction);
        return transactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> getAccountHistory(String userEmail, String accountNumber, Pageable pageable) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "accountNumber", accountNumber));
        if (!account.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw new AccessDeniedException("Account does not belong to the authenticated user");
        }
        return PagedResponse.from(
                transactionRepository.findAllByAccountId(account.getId(), pageable)
                        .map(transactionMapper::toResponse));
    }

    private Account lockAccount(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "accountNumber", accountNumber));
    }

    private void assertActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(
                    "Account %s is not active".formatted(account.getAccountNumber()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private void assertParticipant(String userEmail, Transaction transaction) {
        boolean isSource = transaction.getSourceAccount() != null
                && transaction.getSourceAccount().getUser().getEmail().equalsIgnoreCase(userEmail);
        boolean isDestination = transaction.getDestinationAccount() != null
                && transaction.getDestinationAccount().getUser().getEmail().equalsIgnoreCase(userEmail);
        if (!isSource && !isDestination) {
            throw new AccessDeniedException("You are not a participant in this transaction");
        }
    }
}

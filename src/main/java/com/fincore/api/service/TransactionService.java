package com.fincore.api.service;

import com.fincore.api.dto.request.DepositRequest;
import com.fincore.api.dto.request.TransferRequest;
import com.fincore.api.dto.response.PagedResponse;
import com.fincore.api.dto.response.TransactionResponse;
import org.springframework.data.domain.Pageable;

public interface TransactionService {

    TransactionResponse transfer(String userEmail, TransferRequest request, String idempotencyKey);

    /**
     * Transactional worker behind {@link #transfer}, called through the
     * self-proxy so a retry after a concurrent idempotency-key race runs in its
     * own fresh transaction instead of the one that already rolled back.
     */
    TransactionResponse transferInTransaction(String userEmail, TransferRequest request, String idempotencyKey);

    TransactionResponse deposit(DepositRequest request, String idempotencyKey);

    /**
     * Transactional worker behind {@link #deposit}, called through the
     * self-proxy so a retry after a concurrent idempotency-key race runs in its
     * own fresh transaction instead of the one that already rolled back.
     */
    TransactionResponse depositInTransaction(DepositRequest request, String idempotencyKey);

    TransactionResponse getByReference(String userEmail, String reference);

    PagedResponse<TransactionResponse> getAccountHistory(String userEmail, String accountNumber, Pageable pageable);
}

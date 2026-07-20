package com.fincore.api.service;

import com.fincore.api.dto.request.FundAccountRequest;
import com.fincore.api.dto.response.FundAccountResponse;

public interface PaymentService {

    FundAccountResponse initiateFunding(String userEmail, FundAccountRequest request, String idempotencyKey);

    /**
     * Transactional worker behind {@link #initiateFunding}, called through the
     * self-proxy so a retry after a concurrent idempotency-key race runs in its
     * own fresh transaction instead of the one that already rolled back.
     */
    FundAccountResponse initiateFundingInTransaction(String userEmail, FundAccountRequest request, String idempotencyKey);

    void confirmFunding(String reference);

    void settleFunding(String reference);

    /**
     * Independently re-verifies any Paystack funding still PENDING past the
     * webhook grace period, directly against Paystack's API. Safety net for
     * missed or delayed webhook deliveries.
     */
    void reconcilePendingFundings();
}

package com.fincore.api.service;

import com.fincore.api.dto.request.CreateAccountRequest;
import com.fincore.api.dto.response.AccountResponse;
import com.fincore.api.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;

public interface AccountService {

    AccountResponse createAccount(String userEmail, CreateAccountRequest request);

    AccountResponse getByAccountNumber(String userEmail, String accountNumber);

    PagedResponse<AccountResponse> getUserAccounts(String userEmail, Pageable pageable);
}

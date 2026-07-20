package com.fincore.api.service.impl;

import com.fincore.api.dto.request.CreateAccountRequest;
import com.fincore.api.dto.response.AccountResponse;
import com.fincore.api.dto.response.PagedResponse;
import com.fincore.api.entity.Account;
import com.fincore.api.entity.User;
import com.fincore.api.exception.ResourceNotFoundException;
import com.fincore.api.mapper.AccountMapper;
import com.fincore.api.repository.AccountRepository;
import com.fincore.api.repository.UserRepository;
import com.fincore.api.service.AccountService;
import com.fincore.api.util.AccountNumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountMapper accountMapper;

    @Override
    @Transactional
    public AccountResponse createAccount(String userEmail, CreateAccountRequest request) {
        User user = findUser(userEmail);

        String accountNumber;
        do {
            accountNumber = AccountNumberGenerator.generate();
        } while (accountRepository.existsByAccountNumber(accountNumber));

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .user(user)
                .accountType(request.accountType())
                .currency(request.currency() != null ? request.currency() : "NGN")
                .build();

        account = accountRepository.save(account);
        log.info("Account {} created for user {}", account.getAccountNumber(), user.getId());
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getByAccountNumber(String userEmail, String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "accountNumber", accountNumber));
        assertOwnership(userEmail, account);
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AccountResponse> getUserAccounts(String userEmail, Pageable pageable) {
        User user = findUser(userEmail);
        return PagedResponse.from(
                accountRepository.findAllByUserId(user.getId(), pageable)
                        .map(accountMapper::toResponse));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private void assertOwnership(String userEmail, Account account) {
        if (!account.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw new AccessDeniedException("Account does not belong to the authenticated user");
        }
    }
}

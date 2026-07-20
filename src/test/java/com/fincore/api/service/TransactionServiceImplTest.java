package com.fincore.api.service;

import com.fincore.api.dto.request.TransferRequest;
import com.fincore.api.dto.response.TransactionResponse;
import com.fincore.api.entity.Account;
import com.fincore.api.entity.Transaction;
import com.fincore.api.entity.User;
import com.fincore.api.entity.enums.AccountStatus;
import com.fincore.api.entity.enums.AccountType;
import com.fincore.api.exception.ApiException;
import com.fincore.api.exception.InsufficientBalanceException;
import com.fincore.api.mapper.TransactionMapper;
import com.fincore.api.repository.AccountRepository;
import com.fincore.api.repository.TransactionRepository;
import com.fincore.api.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionMapper transactionMapper;

    @InjectMocks private TransactionServiceImpl transactionService;

    private Account source;
    private Account destination;
    private static final String OWNER_EMAIL = "amos@example.com";

    @BeforeEach
    void setUp() {
        User owner = User.builder().email(OWNER_EMAIL).firstName("Amos").lastName("A").build();
        User other = User.builder().email("other@example.com").firstName("Other").lastName("B").build();

        source = Account.builder()
                .accountNumber("1234567890").user(owner).accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("1000.00")).status(AccountStatus.ACTIVE).build();
        destination = Account.builder()
                .accountNumber("9876543210").user(other).accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("50.00")).status(AccountStatus.ACTIVE).build();
    }

    private void stubLocks() {
        when(accountRepository.findByAccountNumberForUpdate("1234567890")).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumberForUpdate("9876543210")).thenReturn(Optional.of(destination));
    }

    @Test
    @DisplayName("transfer debits source and credits destination")
    void transfer_happyPath() {
        stubLocks();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionMapper.toResponse(any(Transaction.class)))
                .thenReturn(mock(TransactionResponse.class));

        transactionService.transfer(OWNER_EMAIL,
                new TransferRequest("1234567890", "9876543210", new BigDecimal("200.00"), "Rent"), null);

        assertThat(source.getBalance()).isEqualByComparingTo("800.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("250.00");
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("transfer fails when balance is insufficient")
    void transfer_insufficientBalance() {
        stubLocks();

        assertThatThrownBy(() -> transactionService.transfer(OWNER_EMAIL,
                new TransferRequest("1234567890", "9876543210", new BigDecimal("5000.00"), null), null))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(source.getBalance()).isEqualByComparingTo("1000.00");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("transfer rejects same source and destination")
    void transfer_sameAccount() {
        assertThatThrownBy(() -> transactionService.transfer(OWNER_EMAIL,
                new TransferRequest("1234567890", "1234567890", BigDecimal.TEN, null), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("must differ");
    }

    @Test
    @DisplayName("transfer rejects a source account not owned by the caller")
    void transfer_notOwner() {
        stubLocks();

        assertThatThrownBy(() -> transactionService.transfer("intruder@example.com",
                new TransferRequest("1234567890", "9876543210", BigDecimal.TEN, null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("repeated Idempotency-Key replays the original transaction")
    void transfer_idempotentReplay() {
        Transaction existing = Transaction.builder().reference("FCR-EXISTING").build();
        when(transactionRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.of(existing));
        when(transactionMapper.toResponse(existing)).thenReturn(mock(TransactionResponse.class));

        transactionService.transfer(OWNER_EMAIL,
                new TransferRequest("1234567890", "9876543210", BigDecimal.TEN, null), "key-123");

        verify(accountRepository, never()).findByAccountNumberForUpdate(any());
        verify(transactionRepository, never()).save(any());
    }
}

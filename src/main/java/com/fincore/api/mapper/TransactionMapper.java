package com.fincore.api.mapper;

import com.fincore.api.dto.response.TransactionResponse;
import com.fincore.api.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "sourceAccountNumber", source = "sourceAccount.accountNumber")
    @Mapping(target = "destinationAccountNumber", source = "destinationAccount.accountNumber")
    TransactionResponse toResponse(Transaction transaction);
}

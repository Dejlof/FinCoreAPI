package com.fincore.api.mapper;

import com.fincore.api.dto.response.AccountResponse;
import com.fincore.api.entity.Account;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    AccountResponse toResponse(Account account);
}

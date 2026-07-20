package com.fincore.api.mapper;

import com.fincore.api.dto.response.UserResponse;
import com.fincore.api.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}

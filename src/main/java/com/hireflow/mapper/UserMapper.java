package com.hireflow.mapper;

import com.hireflow.domain.User;
import com.hireflow.web.controller.UserController.UserResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}

package com.acadl.finora.auth.mapper;

import com.acadl.finora.auth.dto.RegisterDTO;
import com.acadl.finora.auth.model.User;

public class UserMapper {

    public static User toEntity(RegisterDTO dto) {
        return new User(dto.name());
    }
}
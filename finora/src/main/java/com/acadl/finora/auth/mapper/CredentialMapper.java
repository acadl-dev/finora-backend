package com.acadl.finora.auth.mapper;

import com.acadl.finora.auth.dto.RegisterDTO;
import com.acadl.finora.auth.model.Credential;
import com.acadl.finora.auth.model.User;

public class CredentialMapper {
    public static Credential toEntity(RegisterDTO dto) {

        return new Credential(dto.email(), dto.password());
    }

    public static RegisterDTO toDTO(Credential credential) {
        return new RegisterDTO(
                credential.getUser().getName(),
                credential.getEmail(),
                credential.getHashPassword()

        );
    }
}

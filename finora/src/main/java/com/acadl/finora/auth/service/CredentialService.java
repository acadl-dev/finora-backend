package com.acadl.finora.auth.service;

import com.acadl.finora.auth.dto.RegisterDTO;
import com.acadl.finora.auth.mapper.CredentialMapper;
import com.acadl.finora.auth.mapper.UserMapper;
import com.acadl.finora.auth.model.Credential;
import com.acadl.finora.auth.model.User;
import com.acadl.finora.auth.repository.CredentialRepository;
import com.acadl.finora.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CredentialService {

    private final CredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // injetado, não criado aqui

    public RegisterDTO registerNewUser(RegisterDTO registerDTO) {
        User user = UserMapper.toEntity(registerDTO);
        User userSalvo = userRepository.save(user);

        String cryptographicPassword = passwordEncoder.encode(registerDTO.password());

        Credential credential = CredentialMapper.toEntity(registerDTO);
        credential.setUser(userSalvo);
        credential.setHashPassword(cryptographicPassword);
        credentialRepository.save(credential);

        return CredentialMapper.toDTO(credential);
    }
}







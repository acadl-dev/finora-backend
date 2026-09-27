package com.acadl.finora.auth.controller;
import com.acadl.finora.auth.dto.AuthResponse;
import com.acadl.finora.auth.dto.LoginRequest;
import com.acadl.finora.auth.dto.RegisterDTO;
import com.acadl.finora.auth.model.Credential;
import com.acadl.finora.auth.model.JwtService;
import com.acadl.finora.auth.repository.UserRepository;
import com.acadl.finora.auth.service.CredentialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final CredentialService authService;

    @PostMapping("/register") //retirar o patch desnecessario
    public ResponseEntity<RegisterDTO> registerNewUser(@RequestBody RegisterDTO registerDTO) {
        RegisterDTO registeredUser = authService.registerNewUser(registerDTO);
        return ResponseEntity.status(HttpStatus.OK).body(registeredUser);
    }



   //LOGIN:

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        var credential = (Credential) authentication.getPrincipal();

        String accessToken = jwtService.generateAccessToken(credential);
        String refreshToken = jwtService.generateRefreshToken(credential);

        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken));
    }

}
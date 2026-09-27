package com.acadl.reports.auth.model;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Optional;

/**
 * Valida os tokens emitidos pelo finora. O reports-service não emite tokens:
 * ele apenas confia na assinatura (mesmo segredo) e extrai o usuário (e-mail).
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    /** Retorna o e-mail do usuário se o token for válido (assinatura e expiração). */
    public Optional<String> extractValidSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token) // lança exceção se expirado ou adulterado
                    .getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

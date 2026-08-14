package com.finsight.finsight_ai.Service;


import com.finsight.finsight_ai.entity.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;


@Service
public class JwtService {

    private static final int MINIMUM_HMAC_KEY_BYTES = 32;

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-ms}") long accessTokenExpirationMs
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT signing secret must be configured");
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_HMAC_KEY_BYTES) {
            throw new IllegalStateException("JWT signing secret must be at least 32 bytes");
        }
        if (accessTokenExpirationMs <= 0) {
            throw new IllegalStateException("JWT access token expiration must be positive");
        }

        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    public String generateToken(User user){
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("name", user.getDisplayName())
                .claim("email", user.getEmail())
                .claim("id", user.getId())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(signingKey)
                .compact();
    }

    //Extract the userId from the token.
    public UUID extractUserId(String token) {
        return UUID.fromString(Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject());
    }

    //Extract the user's email from the custom class.
    public String extractEmail(String token) {

        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("email", String.class);
    }



    //verify that token hasn't been tampered with and hasn't been expired.
    public boolean isTokenValid(String token){
        try{
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }
}

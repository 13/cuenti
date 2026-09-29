package com.cuenti.app.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey key;
    private final long jwtExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiration}") long jwtExpiration) {
        
        if (jwtSecret == null || jwtSecret.isBlank() || 
            jwtSecret.equals("generate-secure-key-at-runtime") || 
            jwtSecret.contains("change-this-in-production")) {
            
            logger.warn("JWT secret key is not properly configured. Generating a secure random key for this session.");
            this.key = Jwts.SIG.HS256.key().build();
        } else {
            this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        }
        this.jwtExpiration = jwtExpiration;
    }

    static final String VERSION_CLAIM = "ver";

    /** Issues a token bound to the user's current {@code tokenVersion}; bumping it revokes the token. */
    public String generateToken(String username, int tokenVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(username)
                .claim(VERSION_CLAIM, tokenVersion)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /** Signature and expiry checked; null if the token is invalid. */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Version the token was issued for; tokens from before versioning count as 0. */
    public static int versionOf(Claims claims) {
        Integer version = claims.get(VERSION_CLAIM, Integer.class);
        return version != null ? version : 0;
    }
}

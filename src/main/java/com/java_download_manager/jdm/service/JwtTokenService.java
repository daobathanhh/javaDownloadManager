package com.java_download_manager.jdm.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtTokenService {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final TokenService tokenService;

    @Value("${jdm.token.access-token-expiry-seconds}")
    private long accessTokenExpirySeconds;

    @Value("${jdm.token.refresh-token-expiry-seconds}")
    private long refreshTokenExpirySeconds;

    public String generateAccessToken(long accountId, String jti) {
        return buildToken(accountId, jti, TYPE_ACCESS, accessTokenExpirySeconds);
    }

    public String generateRefreshToken(long accountId, String jti) {
        return buildToken(accountId, jti, TYPE_REFRESH, refreshTokenExpirySeconds);
    }

    public long getAccessTokenExpirySeconds() {
        return accessTokenExpirySeconds;
    }

    public long getRefreshTokenExpirySeconds() {
        return refreshTokenExpirySeconds;
    }

    private String buildToken(long accountId, String jti, String type, long expirySeconds) {
        String kid = tokenService.getCurrentKeyId();
        PrivateKey privateKey = tokenService.getCurrentPrivateKey();
        if (kid == null || privateKey == null) {
            throw new IllegalStateException("Token signing key not initialized");
        }
        long now = System.currentTimeMillis();
        Date iat = new Date(now);
        Date exp = new Date(now + expirySeconds * 1000);

        return Jwts.builder()
                .header().add("kid", kid).and()
                .subject(String.valueOf(accountId))
                .id(jti)
                .claim(CLAIM_TYPE, type)
                .issuedAt(iat)
                .expiration(exp)
                .signWith(privateKey)
                .compact();
    }

    public Optional<JwtClaims> parseAndVerify(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            String kid = getKidFromHeader(token);
            Optional<PublicKey> publicKeyOpt = tokenService.getPublicKeyForVerification(kid);
            if (publicKeyOpt.isEmpty()) {
                log.warn("No public key for kid={}", kid);
                return Optional.empty();
            }
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(publicKeyOpt.get())
                    .build()
                    .parseSignedClaims(token);
            Claims claims = jws.getPayload();
            long accountId = Long.parseLong(claims.getSubject());
            String jti = claims.getId();
            String type = claims.get(CLAIM_TYPE, String.class);
            return Optional.of(new JwtClaims(accountId, jti, type));
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired");
            return Optional.empty();
        } catch (SignatureException | SecurityException e) {
            log.warn("JWT signature invalid: {}", e.getMessage());
            return Optional.empty();
        } catch (JwtException e) {
            log.warn("JWT parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String getKidFromHeader(String token) {
        int dot = token.indexOf('.');
        if (dot <= 0) throw new JwtException("Invalid JWT format");
        String headerB64 = token.substring(0, dot);
        byte[] decoded = java.util.Base64.getUrlDecoder().decode(headerB64);
        String header = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        if (header.contains("\"kid\"")) {
            int start = header.indexOf("\"kid\"") + 6;
            int end = header.indexOf('"', start);
            if (end > start) return header.substring(start, end);
        }
        throw new JwtException("JWT header missing kid");
    }

    public record JwtClaims(long accountId, String jti, String type) {}
}

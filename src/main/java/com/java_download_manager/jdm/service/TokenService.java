package com.java_download_manager.jdm.service;

import com.java_download_manager.jdm.entities.TokenPublicKey;
import com.java_download_manager.jdm.redis.TokenPublicKeyCache;
import com.java_download_manager.jdm.repository.TokenPublicKeyRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.*;
import java.util.Base64;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenService {

    private static final String RSA = "RSA";
    private static final int KEY_SIZE = 2048;

    private final TokenPublicKeyRepository tokenPublicKeyRepository;
    private final TokenPublicKeyCache tokenPublicKeyCache;

    private volatile String currentKeyId;
    private volatile PrivateKey currentPrivateKey;

    @PostConstruct
    void generateAndStoreKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance(RSA);
            gen.initialize(KEY_SIZE);
            KeyPair pair = gen.generateKeyPair();

            String publicKeyPem = toPem(pair.getPublic());
            TokenPublicKey entity = TokenPublicKey.builder()
                    .publicKey(publicKeyPem)
                    .isActive(true)
                    .build();
            entity = tokenPublicKeyRepository.save(entity);

            currentKeyId = String.valueOf(entity.getId());
            currentPrivateKey = pair.getPrivate();

            tokenPublicKeyCache.put(currentKeyId, publicKeyPem);

            log.info("Generated and stored token signing key, kid={}", currentKeyId);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    public String getCurrentKeyId() {
        return currentKeyId;
    }

    public PrivateKey getCurrentPrivateKey() {
        return currentPrivateKey;
    }

    /**
     * Load public key by kid (for JWT verification). Tries cache first, then DB. Caches when loaded from DB.
     */
    public Optional<PublicKey> getPublicKeyForVerification(String kid) {
        if (kid == null || kid.isBlank()) return Optional.empty();
        Optional<String> pemOpt = tokenPublicKeyCache.get(kid);
        if (pemOpt.isEmpty()) {
            try {
                long id = Long.parseLong(kid);
                pemOpt = tokenPublicKeyRepository.findById(id)
                        .filter(TokenPublicKey::getIsActive)
                        .map(TokenPublicKey::getPublicKey);
                pemOpt.ifPresent(pem -> tokenPublicKeyCache.put(kid, pem));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return pemOpt.flatMap(TokenService::parsePemToPublicKey);
    }

    /** 
     * Decode PEM string (from DB) back to PublicKey. 
     */
    static Optional<PublicKey> parsePemToPublicKey(String pem) {
        try {
            String content = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(content);
            return Optional.of(KeyFactory.getInstance(RSA).generatePublic(new java.security.spec.X509EncodedKeySpec(decoded)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Encode public key to PEM string for DB storage. */
    private static String toPem(PublicKey key) {
        String base64 = Base64.getEncoder().encodeToString(key.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n"
                + base64.replaceAll("(.{64})", "$1\n").trim()
                + "\n-----END PUBLIC KEY-----";
    }
}

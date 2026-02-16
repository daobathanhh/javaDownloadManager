package com.java_download_manager.jdm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    /**
     * BCrypt strength (cost factor). Log2 of the number of rounds (e.g. 10 -> 2^10 rounds).
     * Higher = more secure but slower. Typical range 10–12; default 10.
     */
    @Value("${jdm.password.bcrypt-strength:10}")
    private int bcryptStrength;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(bcryptStrength);
    }
}

package com.java_download_manager.jdm.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.redis.host")
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public RedisConnectionFactory redisConnectionFactory(
            @Value("${spring.redis.host}") String host,
            @Value("${spring.redis.port:6379}") int port,
            @Value("${spring.redis.password:}") String password) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        if (password != null && !password.isEmpty()) {
            config.setPassword(RedisPassword.of(password));
        }
        return new CompatibleLettuceConnectionFactory(config);
    }

    @Bean("jdmRedisTemplate")
    @ConditionalOnProperty(name = "spring.redis.host")
    @ConditionalOnMissingBean(name = "jdmRedisTemplate")
    public RedisTemplate<String, String> jdmRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(StringRedisSerializer.UTF_8);
        template.setValueSerializer(StringRedisSerializer.UTF_8);
        template.setHashKeySerializer(StringRedisSerializer.UTF_8);
        template.setHashValueSerializer(StringRedisSerializer.UTF_8);
        template.afterPropertiesSet();
        return template;
    }

    private static final class CompatibleLettuceConnectionFactory extends LettuceConnectionFactory {

        private final RedisStandaloneConfiguration standaloneConfig;

        CompatibleLettuceConnectionFactory(RedisStandaloneConfiguration standaloneConfig) {
            super(standaloneConfig);
            this.standaloneConfig = standaloneConfig;
        }

        @Override
        protected io.lettuce.core.AbstractRedisClient createClient() {
            RedisURI.Builder builder = RedisURI.builder()
                    .withHost(standaloneConfig.getHostName())
                    .withPort(standaloneConfig.getPort())
                    .withDatabase(standaloneConfig.getDatabase())
                    .withLibraryName("")
                    .withLibraryVersion("");
            RedisPassword pwd = standaloneConfig.getPassword();
            if (pwd != null && pwd.isPresent()) {
                builder.withPassword(pwd.get());
            }
            RedisURI uri = builder.build();
            return getClientConfiguration().getClientResources()
                    .map(resources -> RedisClient.create(resources, uri))
                    .orElseGet(() -> RedisClient.create(uri));
        }
    }
}

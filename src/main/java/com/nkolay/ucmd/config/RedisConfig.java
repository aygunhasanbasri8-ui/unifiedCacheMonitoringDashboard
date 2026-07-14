package com.nkolay.ucmd.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.timeout:60000}")
    private long timeoutMs;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
     RedisStandaloneConfiguration serverConfig =
             new RedisStandaloneConfiguration(host, port);
     if (password != null && !password.isEmpty()) {
         serverConfig.setPassword(password);
     }

     ClientOptions clientOptions = ClientOptions.builder()
             .socketOptions(
                     SocketOptions.builder()
                             .connectTimeout(Duration.ofMillis(timeoutMs))
                             .build()
             )
             .disconnectedBehavior(
                     ClientOptions.DisconnectedBehavior.REJECT_COMMANDS
             )
             .build();

        LettuceClientConfiguration clientConfig =
                LettuceClientConfiguration.builder()
                        .clientOptions(clientOptions)
                        .commandTimeout(Duration.ofMillis(timeoutMs))
                        .build();
        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }
}

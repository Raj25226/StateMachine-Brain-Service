package com.example.statemachine.config;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SslOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.util.StringUtils;

/** Shared connection factory for Redis workflow idempotency operations. */
@Configuration(proxyBeanMethods = false)
public class RedisSSLConfiguration {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.ssl.enabled}") boolean sslEnabled,
            @Value("${spring.data.redis.ca-cert}") String certFileLocation,
            @Value("${spring.data.redis.connect-timeout:2s}") Duration connectTimeout,
            @Value("${spring.data.redis.timeout:2s}") Duration commandTimeout,
            ResourceLoader resourceLoader)
            throws IOException {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(host, port);
        if (StringUtils.hasText(password)) {
            standalone.setPassword(password);
        }

        ClientOptions.Builder options =
                ClientOptions.builder()
                        .socketOptions(
                                io.lettuce.core.SocketOptions.builder()
                                        .connectTimeout(connectTimeout)
                                        .build())
                        .protocolVersion(ProtocolVersion.RESP3);
        LettuceClientConfiguration.LettuceClientConfigurationBuilder client =
                LettuceClientConfiguration.builder().commandTimeout(commandTimeout);
        if (sslEnabled) {
            if (!StringUtils.hasText(certFileLocation)) {
                throw new IllegalArgumentException(
                        "COMMON_DS_REDIS_CA_CERT is required when Redis TLS is enabled.");
            }
            options.sslOptions(
                    SslOptions.builder()
                            .trustManager(
                                    resourceLoader
                                            .getResource("file:" + certFileLocation)
                                            .getFile())
                            .build());
            client.useSsl().disablePeerVerification();
        }
        client.clientOptions(options.build());

        return new LettuceConnectionFactory(standalone, client.build()) {
            @Override
            protected AbstractRedisClient createClient() {
                LettuceClientConfiguration configuration = getClientConfiguration();
                RedisURI.Builder uri =
                        RedisURI.Builder.redis(host, port)
                                .withDatabase(getDatabase())
                                .withSsl(configuration.isUseSsl())
                                .withVerifyPeer(configuration.isVerifyPeer())
                                .withStartTls(configuration.isStartTls())
                                .withTimeout(configuration.getCommandTimeout())
                                .withLibraryName("")
                                .withLibraryVersion("");
                configuration.getClientName().ifPresent(uri::withClientName);
                if (StringUtils.hasText(password)) {
                    uri.withPassword(password.toCharArray());
                }
                RedisURI redisUri = uri.build();
                RedisClient redisClient =
                        configuration
                                .getClientResources()
                                .map(resources -> RedisClient.create(resources, redisUri))
                                .orElseGet(() -> RedisClient.create(redisUri));
                configuration.getClientOptions().ifPresent(redisClient::setOptions);
                return redisClient;
            }
        };
    }
}

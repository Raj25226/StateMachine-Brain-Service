package com.example.statemachine.config;

import static org.junit.jupiter.api.Assertions.*;

import io.lettuce.core.protocol.ProtocolVersion;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class RedisSSLConfigurationTest {
    @Test
    void configuresPasswordResp3AndTimeoutsWithoutOpeningConnection() throws Exception {
        var factory =
                new RedisSSLConfiguration()
                        .redisConnectionFactory(
                                "redis.internal",
                                6380,
                                "secret",
                                false,
                                "",
                                Duration.ofSeconds(3),
                                Duration.ofSeconds(4),
                                new DefaultResourceLoader());
        assertEquals("redis.internal", factory.getHostName());
        assertEquals(6380, factory.getPort());
        assertArrayEquals(
                "secret".toCharArray(), factory.getStandaloneConfiguration().getPassword().get());
        var client = factory.getClientConfiguration();
        assertFalse(client.isUseSsl());
        assertEquals(Duration.ofSeconds(4), client.getCommandTimeout());
        var options = client.getClientOptions().orElseThrow();
        assertEquals(ProtocolVersion.RESP3, options.getProtocolVersion());
        assertEquals(Duration.ofSeconds(3), options.getSocketOptions().getConnectTimeout());
    }

    @Test
    void supportsRedisWithoutAuthentication() throws Exception {
        var factory =
                new RedisSSLConfiguration()
                        .redisConnectionFactory(
                                "localhost",
                                6379,
                                "",
                                false,
                                "",
                                Duration.ofSeconds(2),
                                Duration.ofSeconds(2),
                                new DefaultResourceLoader());
        assertFalse(factory.getStandaloneConfiguration().getPassword().isPresent());
    }

    @Test
    void requiresCaCertificateWhenTlsIsEnabled() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RedisSSLConfiguration()
                                .redisConnectionFactory(
                                        "localhost",
                                        6379,
                                        "",
                                        true,
                                        "",
                                        Duration.ofSeconds(2),
                                        Duration.ofSeconds(2),
                                        new DefaultResourceLoader()));
    }
}

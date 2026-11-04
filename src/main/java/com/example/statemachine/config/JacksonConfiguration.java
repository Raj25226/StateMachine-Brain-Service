package com.example.statemachine.config;

import com.example.statemachine.enums.JacksonRequestBindingConsumer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfiguration {

    @Bean
    JsonMapperBuilderCustomizer strictRequestBinding() {
        return JacksonRequestBindingConsumer.STRICT_REQUEST_BINDING::accept;
    }
}

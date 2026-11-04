package com.example.statemachine.config;

import com.example.statemachine.enums.WorkflowContextGsonSupplier;
import com.google.gson.Gson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GsonConfiguration {

    @Bean
    public Gson gson() {
        return WorkflowContextGsonSupplier.WORKFLOW_CONTEXT.get();
    }
}

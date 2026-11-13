package com.example.statemachine.config;

import com.example.statemachine.serialization.WorkflowGsonHttpMessageConverter;
import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@AllArgsConstructor
public class WorkflowJsonConfiguration implements WebMvcConfigurer {
    private final Gson gson;

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(
                converters -> converters.addFirst(new WorkflowGsonHttpMessageConverter(gson)));
    }
}

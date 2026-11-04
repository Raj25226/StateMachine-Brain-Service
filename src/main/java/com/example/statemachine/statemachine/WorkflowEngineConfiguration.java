package com.example.statemachine.statemachine;

import com.example.statemachine.repository.JdbcWorkflowStateMachinePersistence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineModelConfigurer;
import org.springframework.statemachine.persist.DefaultStateMachinePersister;
import org.springframework.statemachine.persist.StateMachinePersister;

@Configuration
@EnableStateMachineFactory
public class WorkflowEngineConfiguration extends StateMachineConfigurerAdapter<String, String> {
    private final JdbcWorkflowModelFactory workflowModelFactory;

    public WorkflowEngineConfiguration(JdbcWorkflowModelFactory workflowModelFactory) {
        this.workflowModelFactory = workflowModelFactory;
    }

    @Override
    public void configure(StateMachineModelConfigurer<String, String> modelConfigurer) throws Exception {
        modelConfigurer.withModel()
                .factory(workflowModelFactory);
    }

    @Bean
    StateMachinePersister<String, String, String> workflowStateMachinePersister(JdbcWorkflowStateMachinePersistence workflowStateMachinePersistence) {
        return new DefaultStateMachinePersister<>(workflowStateMachinePersistence);
    }
}

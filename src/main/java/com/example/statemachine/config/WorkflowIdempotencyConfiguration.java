package com.example.statemachine.config;

import com.example.statemachine.dto.workflow.WorkflowCommand;
import com.example.statemachine.interceptor.WorkflowIdempotencyInterceptor;
import com.example.statemachine.service.WorkflowCommandGateway;
import java.lang.reflect.Method;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class WorkflowIdempotencyConfiguration {

    /** Use Spring's transaction auto-proxy infrastructure without an additional proxy layer. */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public Advisor workflowIdempotencyAdvisor(WorkflowIdempotencyInterceptor interceptor) {
        return new StaticMethodMatcherPointcutAdvisor(interceptor) {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                return WorkflowCommandGateway.class.isAssignableFrom(targetClass)
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == WorkflowCommand.class;
            }
        };
    }
}

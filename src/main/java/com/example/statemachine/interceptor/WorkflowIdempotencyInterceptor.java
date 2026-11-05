package com.example.statemachine.interceptor;

import com.example.statemachine.dto.workflow.WorkflowCommand;
import com.example.statemachine.enums.WorkflowOperation;
import com.example.statemachine.enums.WorkflowRequestFingerprintFunction;
import com.example.statemachine.service.WorkflowIdempotencyService;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Applies Redis idempotency to validated commands before database execution. */
@Component
@Slf4j
public class WorkflowIdempotencyInterceptor implements MethodInterceptor {
    private final WorkflowIdempotencyService workflowIdempotencyService;
    private final Gson gson;

    // Resolve the service after advisors are ready so its JDBC transaction proxy is retained.
    public WorkflowIdempotencyInterceptor(@Lazy WorkflowIdempotencyService workflowIdempotencyService, Gson gson) {
        this.workflowIdempotencyService = workflowIdempotencyService;
        this.gson = gson;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        WorkflowCommand command = (WorkflowCommand) invocation.getArguments()[0];
        if (command.operation() == WorkflowOperation.GET_STATE) {
            return invocation.proceed();
        }

        log.debug("workflow_idempotency_intercepted operation={}", command.operation());
        String requestFingerprint = WorkflowRequestFingerprintFunction.SHA_256.apply(command, gson);
        // The service handles claims/replays and completes Redis only after the JDBC commit.
        return workflowIdempotencyService.execute(command, requestFingerprint);
    }
}

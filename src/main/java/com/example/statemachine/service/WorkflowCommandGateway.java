package com.example.statemachine.service;

import com.example.statemachine.dto.api.WorkflowResponse;
import com.example.statemachine.dto.workflow.WorkflowCommand;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/** Entry point for validated commands; mutations are handled by the idempotency interceptor. */
@Service
@AllArgsConstructor
public class WorkflowCommandGateway {
    private final WorkflowCommandService workflowCommandService;

    public ResponseEntity<WorkflowResponse> executeWorkflowCommand(WorkflowCommand command) {
        return workflowCommandService.dispatchWorkflowCommand(command);
    }
}

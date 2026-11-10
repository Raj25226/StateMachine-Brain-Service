package com.example.statemachine.service;

import com.example.statemachine.constants.ApiErrorConstants;
import com.example.statemachine.dto.api.WorkflowResponse;
import com.example.statemachine.dto.api.WorkflowResponseMetadata;
import com.example.statemachine.dto.persistence.WorkflowIdempotencyClaim;
import com.example.statemachine.dto.workflow.WorkflowCommand;
import com.example.statemachine.exception.WorkflowException;
import com.example.statemachine.repository.RedisWorkflowIdempotencyRepository;
import com.google.gson.Gson;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class WorkflowIdempotencyService {
    private final RedisWorkflowIdempotencyRepository idempotencyRepository;
    private final WorkflowCommandService commandService;
    private final Gson gson;

    public ResponseEntity<WorkflowResponse> execute(WorkflowCommand command, String fingerprint) {
        String key = command.metadata().idempotencyKey();
        String ownerToken = UUID.randomUUID().toString();
        WorkflowIdempotencyClaim claim = idempotencyRepository.claim(key, fingerprint, ownerToken);
        log.debug(
                "workflow_idempotency_claim outcome={} operation={}",
                claim.outcome(),
                command.operation());
        return switch (claim.outcome()) {
            case CONFLICT ->
                    throw WorkflowException.conflict(
                            ApiErrorConstants.IDEMPOTENCY_CONFLICT,
                            "This idempotencyKey is bound to different request inputs.");
            case IN_PROGRESS ->
                    throw WorkflowException.conflict(
                            ApiErrorConstants.IDEMPOTENCY_IN_PROGRESS,
                            "This command is processing or requires recovery. Retry later with the same idempotency key.");
            case REPLAY -> replay(claim, command.metadata().requestId());
            case CLAIMED -> {
                // The proxied transactional service returns only after its JDBC commit completes.
                ResponseEntity<WorkflowResponse> response =
                        commandService.executeTransactionalCommand(command, ownerToken);
                idempotencyRepository.complete(
                        key,
                        ownerToken,
                        gson.toJson(response.getBody()),
                        response.getStatusCode().value());
                yield response;
            }
        };
    }

    private ResponseEntity<WorkflowResponse> replay(
            WorkflowIdempotencyClaim claim, String requestId) {
        WorkflowResponse original = gson.fromJson(claim.responseJson(), WorkflowResponse.class);
        WorkflowResponseMetadata metadata = original.metadata();
        WorkflowResponseMetadata replayed =
                new WorkflowResponseMetadata(
                        metadata.operation(),
                        requestId,
                        metadata.referenceId(),
                        metadata.event(),
                        metadata.idempotencyKey(),
                        metadata.workflowKey(),
                        metadata.status(),
                        metadata.processedAt(),
                        true);
        log.debug("workflow_idempotency_replayed status={}", claim.httpStatus());
        return ResponseEntity.status(claim.httpStatus())
                .body(new WorkflowResponse(replayed, original.payload(), null));
    }
}

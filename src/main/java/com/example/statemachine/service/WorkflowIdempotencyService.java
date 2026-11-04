package com.example.statemachine.service;

import lombok.AllArgsConstructor;
import com.example.statemachine.repository.JdbcWorkflowIdempotencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class WorkflowIdempotencyService {
    private final JdbcWorkflowIdempotencyRepository idempotencyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void reserveIdempotencyKey(String idempotencyKey, String requestFingerprint) {
        if (!idempotencyRepository.existsByIdempotencyKey(idempotencyKey)) {
            idempotencyRepository.reserveIdempotencyKey(idempotencyKey, requestFingerprint);
        }
    }
}

package com.example.statemachine.service;

import com.example.statemachine.repository.JdbcWorkflowReplayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {
    private final JdbcWorkflowReplayRepository replayRepository;

    public IdempotencyService(JdbcWorkflowReplayRepository replayRepository) {
        this.replayRepository = replayRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void reserve(String idempotencyKey, String fingerprint) {
        if (!replayRepository.exists(idempotencyKey)) {
            replayRepository.reserve(idempotencyKey, fingerprint);
        }
    }
}

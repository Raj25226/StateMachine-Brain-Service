package com.example.statemachine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
@Slf4j
public class StateMachineBrainApplication {

    public static void main(String[] args) {
        SpringApplication.run(StateMachineBrainApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logReady() {
        log.info(
                "workflow_service_ready endpoint=/api/workflows operations=CREATE,GET_STATE,SUBMIT_EVENT");
    }
}

package com.example.statemachine.config;

import static org.junit.jupiter.api.Assertions.*;

import com.example.statemachine.controller.WorkflowController;
import com.example.statemachine.dto.api.WorkflowRequest;
import com.example.statemachine.dto.api.WorkflowResponse;
import com.example.statemachine.validation.WorkflowRequestValidator;
import com.google.gson.Gson;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WorkflowApiDocumentationTest {
    private final Gson gson = new GsonConfiguration().gson();

    private Method endpoint() throws NoSuchMethodException {
        return WorkflowController.class.getMethod(
                "processWorkflowCommand", WorkflowRequest.class, HttpServletRequest.class);
    }

    @Test
    void allDocumentedRequestsPassTheRealValidator() throws NoSuchMethodException {
        Operation operation = endpoint().getAnnotation(Operation.class);
        WorkflowRequestValidator validator = new WorkflowRequestValidator();
        for (ExampleObject example : operation.requestBody().content()[0].examples()) {
            assertDoesNotThrow(
                    () -> validator.validate(gson.fromJson(example.value(), WorkflowRequest.class)),
                    example.name());
        }
    }

    @Test
    void allDocumentedResponsesMatchRuntimeDtosAndHttpStatus() throws NoSuchMethodException {
        for (ApiResponse response : endpoint().getAnnotation(ApiResponses.class).value()) {
            assertTrue(
                    Arrays.stream(response.headers())
                            .anyMatch(header -> header.name().equals("X-Correlation-Id")));
            for (ExampleObject example : response.content()[0].examples()) {
                WorkflowResponse body = gson.fromJson(example.value(), WorkflowResponse.class);
                if (Integer.parseInt(response.responseCode()) >= 400) {
                    assertNull(body.payload(), example.name());
                    assertNotNull(body.error(), example.name());
                    assertEquals("FAILED", body.metadata().status(), example.name());
                } else {
                    assertNotNull(body.payload(), example.name());
                    assertNull(body.error(), example.name());
                    assertEquals("SUCCESS", body.metadata().status(), example.name());
                    if (body.payload().completed()) {
                        assertTrue(body.payload().availableTransitions().isEmpty(), example.name());
                    }
                }
            }
        }
    }

    @Test
    void annotatedResponseSchemasResolveFromRuntimeTypes() throws NoSuchMethodException {
        for (ApiResponse response : endpoint().getAnnotation(ApiResponses.class).value()) {
            Class<?> type = response.content()[0].schema().implementation();
            assertEquals(WorkflowResponse.class, type);
            assertTrue(ModelConverters.getInstance().readAll(type).containsKey("WorkflowResponse"));
        }
    }
}

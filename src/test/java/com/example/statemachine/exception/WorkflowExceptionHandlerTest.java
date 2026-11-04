package com.example.statemachine.exception;

import com.example.statemachine.constants.RequestAttributeConstants;
import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.api.WorkflowResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowExceptionHandlerTest {
    static Stream<Arguments> exceptions() {
        return Stream.of(
                Arguments.of(WorkflowException.invalid("Invalid event."), 400, "INVALID_REQUEST"),
                Arguments.of(new DuplicateKeyException("duplicate"), 409, "WORKFLOW_ALREADY_EXISTS"),
                Arguments.of(new HttpMessageNotReadableException("invalid", (HttpInputMessage) null), 400, "INVALID_REQUEST"),
                Arguments.of(new HttpMediaTypeNotSupportedException("unsupported"), 415, "INVALID_REQUEST"),
                Arguments.of(new HttpRequestMethodNotSupportedException("GET"), 405, "INVALID_REQUEST"),
                Arguments.of(new NoResourceFoundException(HttpMethod.GET, "/missing", "/missing"), 404, "ENDPOINT_NOT_FOUND"),
                Arguments.of(new IllegalStateException("private internal details"), 500, "INTERNAL_ERROR"));
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void springRoutesExceptionsToTheirSpecificHandlers(Exception exception, int status, String code) throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(RequestAttributeConstants.METADATA_ATTRIBUTE,
                new WorkflowCommandMetadata("GET_STATE", "req-1", "APP-101", null, null, null));
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getAttribute")) {
                        return attributes.get(arguments[0]);
                    }
                    if (method.getName().equals("setAttribute")) {
                        attributes.put((String) arguments[0], arguments[1]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(WorkflowExceptionHandler.class);
        var handlerMethod = resolver.resolveMethod(exception);
        assertNotNull(handlerMethod);
        assertEquals(exception instanceof IllegalStateException ? Exception.class : exception.getClass(),
                handlerMethod.getParameterTypes()[0]);
        ResponseEntity<?> result = (ResponseEntity<?>) handlerMethod.invoke(new WorkflowExceptionHandler(), exception, request);
        assertEquals(status, result.getStatusCode().value());
        WorkflowResponse body = (WorkflowResponse) result.getBody();
        assertNotNull(body);
        assertNull(body.payload());
        assertEquals(code, body.error().code());
        assertEquals("FAILED", body.metadata().status());
        assertEquals("req-1", body.metadata().requestId());
        assertEquals("APP-101", body.metadata().referenceId());
        assertSame(body, attributes.get(RequestAttributeConstants.RESPONSE_ATTRIBUTE));
        if (status == 500) {
            assertEquals("An unexpected error occurred.", body.error().description());
        }
    }
}

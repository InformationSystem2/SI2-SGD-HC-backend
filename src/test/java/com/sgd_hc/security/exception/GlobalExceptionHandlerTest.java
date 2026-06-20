package com.sgd_hc.security.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    private WebRequest mockRequest() {
        WebRequest req = mock(WebRequest.class);
        when(req.getDescription(false)).thenReturn("uri=/api/test");
        return req;
    }

    // ── PlanLimitExceededException ───────────────────────────────────────────

    @Test
    @DisplayName("handlePlanLimitExceeded returns 403 with correct body")
    void handlePlanLimitExceeded() {
        PlanLimitExceededException ex = new PlanLimitExceededException("usuarios", 10L, 10L);
        WebRequest request = mockRequest();

        ResponseEntity<Object> response = handler.handlePlanLimitExceeded(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(403, body.get("status"));
        assertEquals("Plan Limit Exceeded", body.get("error"));
        assertEquals("usuarios", body.get("resourceType"));
        assertEquals(10L, body.get("currentCount"));
        assertEquals(10L, body.get("maxLimit"));
        assertEquals("/api/test", body.get("path"));
        assertNotNull(body.get("timestamp"));
        assertTrue(body.get("message").toString().contains("usuarios"));
    }

    // ── TenantSuspendedException ─────────────────────────────────────────────

    @Test
    @DisplayName("handleTenantSuspendedException returns 403 with tenant info")
    void handleTenantSuspended() {
        TenantSuspendedException ex = new TenantSuspendedException("clinica-abc", "Clínica ABC");
        WebRequest request = mockRequest();

        ResponseEntity<Object> response = handler.handleTenantSuspendedException(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(403, body.get("status"));
        assertEquals("Tenant Suspended", body.get("error"));
        assertEquals("clinica-abc", body.get("tenantSlug"));
        assertEquals("Clínica ABC", body.get("tenantName"));
    }

    // ── AccessDeniedException ────────────────────────────────────────────────

    @Test
    @DisplayName("handleAccessDeniedException returns 403")
    void handleAccessDenied() {
        ResponseEntity<Object> response = handler.handleAccessDeniedException(
                new org.springframework.security.access.AccessDeniedException("denied"),
                mockRequest());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── AuthenticationException ──────────────────────────────────────────────

    @Test
    @DisplayName("handleAuthenticationException returns 401")
    void handleAuthentication() {
        ResponseEntity<Object> response = handler.handleAuthenticationException(
                new org.springframework.security.authentication.BadCredentialsException("bad creds"),
                mockRequest());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── IllegalArgumentException ─────────────────────────────────────────────

    @Test
    @DisplayName("handleIllegalArgument returns 400")
    void handleIllegalArgument() {
        ResponseEntity<Object> response = handler.handleIllegalArgument(
                new IllegalArgumentException("bad arg"),
                mockRequest());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ── IllegalStateException ────────────────────────────────────────────────

    @Test
    @DisplayName("handleIllegalState returns 400")
    void handleIllegalState() {
        ResponseEntity<Object> response = handler.handleIllegalState(
                new IllegalStateException("bad state"),
                mockRequest());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ── EmptyResultDataAccessException ───────────────────────────────────────

    @Test
    @DisplayName("handleEmptyResultDataAccessException returns 404")
    void handleEmptyResult() {
        ResponseEntity<Object> response = handler.handleEmptyResultDataAccessException(
                new org.springframework.dao.EmptyResultDataAccessException("not found", 1),
                mockRequest());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ── Generic Exception ────────────────────────────────────────────────────

    @Test
    @DisplayName("handleGenericException returns 500")
    void handleGeneric() {
        ResponseEntity<Object> response = handler.handleGenericException(
                new RuntimeException("something broke"),
                mockRequest());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    // ── PlanLimitExceededException various resource types ────────────────────

    @Test
    @DisplayName("handles storage resource type correctly")
    void handlesStorage() {
        PlanLimitExceededException ex = new PlanLimitExceededException("almacenamiento", 1024L, 1024L);
        ResponseEntity<Object> response = handler.handlePlanLimitExceeded(ex, mockRequest());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("almacenamiento", body.get("resourceType"));
        assertEquals(1024L, body.get("currentCount"));
        assertEquals(1024L, body.get("maxLimit"));
    }

    @Test
    @DisplayName("handles unlimited-like values in exception")
    void handlesEdgeCaseValues() {
        PlanLimitExceededException ex = new PlanLimitExceededException("documentos", 0L, 0L);
        ResponseEntity<Object> response = handler.handlePlanLimitExceeded(ex, mockRequest());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(0L, body.get("currentCount"));
        assertEquals(0L, body.get("maxLimit"));
    }
}

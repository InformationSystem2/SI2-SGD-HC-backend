package com.sgd_hc.tenants.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgd_hc.security.exception.PlanLimitExceededException;
import com.sgd_hc.tenants.entity.SubscriptionPlan;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.repository.ApiCallUsageRepository;
import com.sgd_hc.tenants.repository.TenantRepository;
import com.sgd_hc.tenants.service.PlanLimitValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiCallTrackingFilterTest {

    @Mock private PlanLimitValidator planLimitValidator;
    @Mock private ApiCallUsageRepository apiCallUsageRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private FilterChain filterChain;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private ApiCallTrackingFilter filter;

    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSubscriptionPlan(SubscriptionPlan.BASIC);

        lenient().when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        ReflectionTestUtils.setField(filter, "objectMapper", objectMapper);
    }

    private MockHttpServletRequest createApiRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("X-Tenant-ID", tenantId.toString());
        return request;
    }

    @Nested
    @DisplayName("skips tracking for non-API paths")
    class SkipNonApiPaths {

        @Test
        @DisplayName("does not increment for non-/api/ paths")
        void doesNotIncrementForNonApiPaths() throws ServletException, IOException {
            MockHttpServletRequest request = createApiRequest("/health");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(apiCallUsageRepository, never()).incrementCallCount(any(), anyString());
        }

        @Test
        @DisplayName("does not increment for excluded /api/auth paths")
        void doesNotIncrementForExcludedPaths() throws ServletException, IOException {
            MockHttpServletRequest request = createApiRequest("/api/auth/login");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(apiCallUsageRepository, never()).incrementCallCount(any(), anyString());
        }

        @Test
        @DisplayName("does not increment for /api/public paths")
        void doesNotIncrementForPublicPaths() throws ServletException, IOException {
            MockHttpServletRequest request = createApiRequest("/api/public/tenant");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(apiCallUsageRepository, never()).incrementCallCount(any(), anyString());
        }
    }

    @Nested
    @DisplayName("skips when tenant ID is missing")
    class SkipMissingTenantId {

        @Test
        @DisplayName("does not increment when no tenant header")
        void doesNotIncrementWithoutTenantHeader() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(apiCallUsageRepository, never()).incrementCallCount(any(), anyString());
        }
    }

    @Nested
    @DisplayName("blocks requests when limit exceeded")
    class BlockWhenLimitExceeded {

        @Test
        @DisplayName("returns 403 when API call limit exceeded")
        void returns403WhenLimitExceeded() throws ServletException, IOException {
            doThrow(new PlanLimitExceededException("llamadas API (este mes)", 500L, 500L))
                    .when(planLimitValidator).checkApiCallsLimit(tenantId);

            MockHttpServletRequest request = createApiRequest("/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertEquals(403, response.getStatus());
            assertTrue(response.getContentType().contains("application/json"));
            String body = response.getContentAsString();
            assertTrue(body.contains("Plan Limit Exceeded"));
            assertTrue(body.contains("currentCount"));
            assertTrue(body.contains("maxLimit"));
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("does not increment counter when blocked")
        void doesNotIncrementWhenBlocked() throws ServletException, IOException {
            doThrow(new PlanLimitExceededException("llamadas API (este mes)", 501L, 500L))
                    .when(planLimitValidator).checkApiCallsLimit(tenantId);

            MockHttpServletRequest request = createApiRequest("/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(apiCallUsageRepository, never()).incrementCallCount(any(), anyString());
        }

        @Test
        @DisplayName("does not block when under limit")
        void doesNotBlockWhenUnderLimit() throws ServletException, IOException {
            MockHttpServletRequest request = createApiRequest("/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("does not block when unlimited (-1)")
        void doesNotBlockWhenUnlimited() throws ServletException, IOException {
            MockHttpServletRequest request = createApiRequest("/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("increments counter after successful request")
    class IncrementAfterSuccess {

        @Test
        @DisplayName("increments on 200 response")
        void incrementsOn200() throws ServletException, IOException {
            MockHttpServletRequest request = createApiRequest("/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();
            response.setStatus(200);

            filter.doFilterInternal(request, response, filterChain);

            verify(apiCallUsageRepository).incrementCallCount(eq(tenantId), anyString());
        }

        @Test
        @DisplayName("does not increment on 401 response")
        void doesNotIncrementOn401() throws ServletException, IOException {
            MockHttpServletRequest request = createApiRequest("/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();

            doAnswer(invocation -> {
                response.setStatus(401);
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilterInternal(request, response, filterChain);

            verify(apiCallUsageRepository, never()).incrementCallCount(any(), anyString());
        }

        @Test
        @DisplayName("does not increment on 500 response")
        void doesNotIncrementOn500() throws ServletException, IOException {
            MockHttpServletRequest request = createApiRequest("/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();

            doAnswer(invocation -> {
                response.setStatus(500);
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilterInternal(request, response, filterChain);

            verify(apiCallUsageRepository, never()).incrementCallCount(any(), anyString());
        }
    }
}

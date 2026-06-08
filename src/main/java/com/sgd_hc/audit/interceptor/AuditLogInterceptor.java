package com.sgd_hc.audit.interceptor;

import com.sgd_hc.audit.filter.CachedBodyHttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class AuditLogInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        request.setAttribute("auditStartTime", System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute("auditStartTime");
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            if (duration > 5000) {
                log.warn("Slow request detected: {} {} took {}ms [status={}]",
                        request.getMethod(), request.getRequestURI(),
                        duration, response.getStatus());
            }
        }
    }
}

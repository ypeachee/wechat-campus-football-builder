package com.campusfootball.common.ratelimit;

import com.campusfootball.common.api.ApiError;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.common.api.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {
    private final FixedWindowRateLimiter limiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(FixedWindowRateLimiter limiter, RateLimitProperties properties,
                           ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        Rule rule = ruleFor(request.getMethod(), request.getRequestURI());
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = rule.scope() + ":" + identity(request);
        FixedWindowRateLimiter.Decision decision = limiter.tryAcquire(
                key, rule.limit(), rule.window(), Instant.now());
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        objectMapper.writeValue(response.getWriter(), new ApiError(
                ErrorCode.RATE_LIMITED.name(), "请求过于频繁，请稍后再试",
                requestId == null ? "" : requestId.toString()));
    }

    private Rule ruleFor(String method, String path) {
        if ("POST".equals(method) && "/api/v1/auth/wechat-login".equals(path)) {
            return new Rule("wechat-login", properties.getLoginPerMinute(), Duration.ofMinutes(1));
        }
        if ("PUT".equals(method)
                && (matches(path, "/api/v1/fixtures/\\d+/players/\\d+/rating")
                || matches(path, "/api/v1/fixtures/\\d+/referee-rating"))) {
            return new Rule("rating-submit", properties.getRatingPerMinute(), Duration.ofMinutes(1));
        }
        if ("POST".equals(method) && matches(path, "/api/v1/ratings/\\d+/report")) {
            return new Rule("rating-report", properties.getReportPerTenMinutes(), Duration.ofMinutes(10));
        }
        if (("POST".equals(method) || "PUT".equals(method))
                && matches(path, "/api/v1/manage/suspensions(?:/\\d+)?")) {
            return new Rule("discipline-write", properties.getDisciplinePerMinute(), Duration.ofMinutes(1));
        }
        if (("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))
                && isMatchWrite(path)) {
            return new Rule("match-write", properties.getMatchWritePerMinute(), Duration.ofMinutes(1));
        }
        return null;
    }

    private boolean isMatchWrite(String path) {
        return matches(path, "/api/v1/(?:manage|admin)/fixtures(?:/.*)?")
                || matches(path, "/api/v1/manage/events(?:/.*)?")
                || matches(path, "/api/v1/manage/appearances(?:/.*)?");
    }

    private boolean matches(String path, String pattern) {
        return path.matches(pattern);
    }

    private String identity(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            return "token-" + sha256(authorization);
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip-" + forwarded.split(",", 2)[0].trim();
        }
        return "ip-" + request.getRemoteAddr();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record Rule(String scope, int limit, Duration window) {
    }
}
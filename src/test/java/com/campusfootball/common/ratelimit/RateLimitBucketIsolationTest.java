package com.campusfootball.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.*;

class RateLimitBucketIsolationTest {
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter();
    private final RateLimitProperties properties = new RateLimitProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setLoginPerMinute(2);
        filter = new RateLimitFilter(limiter, properties, objectMapper);
    }

    private HttpServletResponse mockResponse() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
        return response;
    }

    @Test
    void webAndWechatLoginUseSeparateBuckets() throws Exception {
        HttpServletRequest webRequest = mockRequest("POST", "/api/v1/auth/web-login", "1.2.3.4");
        HttpServletRequest wechatRequest = mockRequest("POST", "/api/v1/auth/wechat-login", "1.2.3.4");
        HttpServletResponse response = mockResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(webRequest, response, chain);
        filter.doFilter(webRequest, response, chain);
        verify(chain, times(2)).doFilter(webRequest, response);

        filter.doFilter(wechatRequest, response, chain);
        verify(chain).doFilter(wechatRequest, response);

        filter.doFilter(wechatRequest, response, chain);
        verify(chain, times(2)).doFilter(wechatRequest, response);

        filter.doFilter(webRequest, response, chain);
        verify(response).setStatus(429);
        verify(chain, times(2)).doFilter(webRequest, response);
    }

    @Test
    void bucketsAreIsolatedPerIdentity() throws Exception {
        HttpServletRequest request1 = mockRequest("POST", "/api/v1/auth/web-login", "1.2.3.4");
        HttpServletRequest request2 = mockRequest("POST", "/api/v1/auth/web-login", "5.6.7.8");
        HttpServletResponse response = mockResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request1, response, chain);
        filter.doFilter(request1, response, chain);
        filter.doFilter(request2, response, chain);
        filter.doFilter(request2, response, chain);

        verify(chain, times(4)).doFilter(any(), eq(response));

        filter.doFilter(request1, response, chain);
        verify(response).setStatus(429);
    }

    @Test
    void trustForwardedHeadersDisabledUsesRemoteAddr() throws Exception {
        properties.setTrustForwardedHeaders(false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/auth/web-login");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 5.6.7.8");
        HttpServletResponse response = mockResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain);

        verify(chain, times(2)).doFilter(request, response);
    }

    @Test
    void trustForwardedHeadersEnabledUsesFirstHop() throws Exception {
        properties.setTrustForwardedHeaders(true);
        HttpServletRequest request1 = mock(HttpServletRequest.class);
        when(request1.getMethod()).thenReturn("POST");
        when(request1.getRequestURI()).thenReturn("/api/v1/auth/web-login");
        when(request1.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request1.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 5.6.7.8");

        HttpServletRequest request2 = mock(HttpServletRequest.class);
        when(request2.getMethod()).thenReturn("POST");
        when(request2.getRequestURI()).thenReturn("/api/v1/auth/web-login");
        when(request2.getRemoteAddr()).thenReturn("10.0.0.2");
        when(request2.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 9.9.9.9");

        HttpServletResponse response = mockResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request1, response, chain);
        filter.doFilter(request1, response, chain);
        filter.doFilter(request2, response, chain);

        verify(chain, times(2)).doFilter(request1, response);
        verify(response).setStatus(429);
    }

    private HttpServletRequest mockRequest(String method, String path, String remoteAddr) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(path);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(null);
        return request;
    }
}

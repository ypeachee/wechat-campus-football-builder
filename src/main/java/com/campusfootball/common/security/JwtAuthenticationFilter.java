package com.campusfootball.common.security;

import com.campusfootball.common.api.AppException;
import com.campusfootball.user.AppUser;
import com.campusfootball.user.AppUserMapper;
import com.campusfootball.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AppUserMapper appUserMapper;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserMapper appUserMapper) {
        this.jwtService = jwtService;
        this.appUserMapper = appUserMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                long userId = jwtService.parseUserId(authorization.substring(7));
                appUserMapper.findById(userId)
                        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                        .ifPresent(this::authenticate);
            } catch (AppException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(AppUser user) {
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getRoleCode());
        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRoleCode().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}

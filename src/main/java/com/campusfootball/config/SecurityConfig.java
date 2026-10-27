package com.campusfootball.config;

import com.campusfootball.common.security.JwtAuthenticationFilter;
import com.campusfootball.common.security.RestAccessDeniedHandler;
import com.campusfootball.common.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/wechat-login").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/fixtures/*/players/*/rating/me",
                                "/api/v1/fixtures/*/referee-rating/me").authenticated()
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/fixtures/*/players/*/rating",
                                "/api/v1/fixtures/*/referee-rating").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/ratings/*/report").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/seasons", "/api/v1/teams/**", "/api/v1/players/**",
                                "/api/v1/fixtures/**", "/api/v1/standings", "/api/v1/stats/**",
                                "/api/v1/competitions", "/api/v1/stages", "/api/v1/groups/**", "/api/v1/brackets").permitAll()
                        .requestMatchers("/api/v1/me").authenticated()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/manage/**").hasAnyRole("ADMIN", "TEAM_MANAGER")
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

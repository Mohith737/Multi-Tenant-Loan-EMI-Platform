package com.loanplatform.loan_platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/admin/login", "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/stripe").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tenants/public").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/h2-console/**", "/api/v2/**").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"errorCode\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"status\":401}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"errorCode\":\"ACCESS_DENIED\",\"message\":\"Access is denied\",\"status\":403}");
                        })
                )
                .httpBasic(httpBasic -> {})
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${app.security.admin.username}") String username,
            @Value("${app.security.admin.password}") String password,
            @Value("${app.security.tenant-admin.username:tenant_admin}") String tenantAdminUsername,
            @Value("${app.security.tenant-admin.password:tenant_admin_password}") String tenantAdminPassword,
            PasswordEncoder passwordEncoder
    ) {
        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(passwordEncoder.encode(password))
                        .roles("PLATFORM_ADMIN")
                        .build(),
                User.withUsername(tenantAdminUsername)
                        .password(passwordEncoder.encode(tenantAdminPassword))
                        .roles("TENANT_ADMIN")
                        .build()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec secretKey = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> new JwtAuthenticationToken(jwt, extractRoleAuthorities(jwt));
    }

    private Collection<? extends GrantedAuthority> extractRoleAuthorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Object rolesClaim = jwt.getClaims().get("roles");
        Object roleClaim = jwt.getClaims().get("role");
        Object authoritiesClaim = jwt.getClaims().get("authorities");

        if (rolesClaim instanceof Collection<?> roles) {
            for (Object role : roles) {
                if (role != null) {
                    authorities.add(() -> "ROLE_" + normalizeRole(role.toString()));
                }
            }
            return authorities;
        }

        if (rolesClaim instanceof String rolesString) {
            for (String role : rolesString.split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isBlank()) {
                    authorities.add(() -> "ROLE_" + normalizeRole(trimmed));
                }
            }
            return authorities;
        }

        if (roleClaim instanceof String singleRole && !singleRole.isBlank()) {
            authorities.add(() -> "ROLE_" + normalizeRole(singleRole));
            return authorities;
        }

        if (authoritiesClaim instanceof Collection<?> claimsAuthorities) {
            for (Object authority : claimsAuthorities) {
                if (authority == null) {
                    continue;
                }
                String rawAuthority = authority.toString().trim().toUpperCase(Locale.ROOT);
                if (rawAuthority.isBlank()) {
                    continue;
                }
                if (rawAuthority.startsWith("ROLE_")) {
                    authorities.add(() -> rawAuthority);
                } else {
                    authorities.add(() -> "ROLE_" + normalizeRole(rawAuthority));
                }
            }
        }

        return authorities;
    }

    private String normalizeRole(String role) {
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            return normalized.substring("ROLE_".length());
        }
        return normalized;
    }
}

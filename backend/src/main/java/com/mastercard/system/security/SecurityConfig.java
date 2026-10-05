package com.mastercard.system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] SWAGGER = {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    JwtDecoder jwtDecoder(JwtService jwt) {
        return jwt.decoder();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(
                jwt -> List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
        return c;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origins}") String origins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cfg.setAllowCredentials(true); // necesario para la cookie del refresh token
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", cfg);
        return src;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, Environment env, JwtAuthenticationConverter converter,
                                    ObjectMapper mapper) throws Exception {
        AuthenticationEntryPoint entryPoint = (req, res, e) ->
                json(mapper, res, HttpStatus.UNAUTHORIZED, "No autenticado: inicie sesión para continuar");
        AccessDeniedHandler denied = (req, res, e) ->
                json(mapper, res, HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta acción");
        boolean dev = env.acceptsProfiles(Profiles.of("dev"));

        http
            .csrf(AbstractHttpConfigurer::disable) // API stateless con Bearer; el refresh usa cookie SameSite=Strict
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .headers(h -> {
                h.frameOptions(f -> f.deny());
                h.referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER));
                // CSP estricta para la API; el Swagger UI (solo dev) necesita scripts/estilos propios.
                h.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                        new NegatedRequestMatcher(new OrRequestMatcher(
                                Arrays.stream(SWAGGER).map(AntPathRequestMatcher::new).toArray(AntPathRequestMatcher[]::new))),
                        new ContentSecurityPolicyHeaderWriter("default-src 'none'; frame-ancestors 'none'")));
            })
            .authorizeHttpRequests(a -> {
                a.requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll();
                a.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll();
                if (dev) a.requestMatchers(SWAGGER).permitAll();
                a.anyRequest().authenticated();
            })
            .oauth2ResourceServer(o -> o
                .jwt(j -> j.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(denied))
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied));
        return http.build();
    }

    private static void json(ObjectMapper mapper, HttpServletResponse res, HttpStatus status, String message)
            throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), Map.of("message", message));
    }
}

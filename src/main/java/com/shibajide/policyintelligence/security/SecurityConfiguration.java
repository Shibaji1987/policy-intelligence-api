package com.shibajide.policyintelligence.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(ApiSecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiSecurityProperties properties) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!properties.enabled()) {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }

        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ACTUATOR_ADMIN")
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasRole("API_DOCS")
                        .requestMatchers(HttpMethod.POST, "/api/v1/documents/**").hasRole("DOCUMENT_ADMIN")
                        .requestMatchers("/api/v1/documents/**").hasAnyRole("DOCUMENT_ADMIN", "ADVISOR_USER")
                        .requestMatchers("/api/v1/advisor/**").hasRole("ADVISOR_USER")
                        .requestMatchers("/api/v1/retrieval/**").hasRole("ADVISOR_USER")
                        .requestMatchers("/api/v1/retrieval-traces/**").hasRole("ADVISOR_USER")
                        .requestMatchers("/api/v1/evaluations/**").hasRole("EVALUATION_USER")
                        .requestMatchers("/api/v1/cache/**", "/api/v1/embeddings/**").hasRole("DOCUMENT_ADMIN")
                        .requestMatchers("/api/v1/ml/health").hasAnyRole("ACTUATOR_ADMIN", "ADVISOR_USER")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}

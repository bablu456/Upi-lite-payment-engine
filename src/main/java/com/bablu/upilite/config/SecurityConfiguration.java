package com.bablu.upilite.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

        private final JwtAuthenticationFilter jwtAuthFilter;
        private final AuthenticationProvider authenticationProvider;
        private final CorsConfigurationSource corsConfigurationSource;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                // Enable CORS with our configuration
                                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                                // CSRF disable (Stateless API ke liye)
                                .csrf(csrf -> csrf.disable())

                                // Authorization Rules
                                .authorizeHttpRequests(auth -> auth
                                                // Public Endpoints (No Token Required)
                                                .requestMatchers(
                                                                "/api/users/register",
                                                                "/api/users/login",
                                                                "/api/users/login/otp/request",
                                                                "/api/users/login/otp/verify",
                                                                "/api/users/password/forgot/request",
                                                                "/api/users/password/forgot/reset",
                                                                "/api/notifications/stream",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/v3/api-docs/**",
                                                                "/swagger-resources/**",
                                                                "/webjars/**")
                                                .permitAll()
                                                // Baaki sab Secured hai
                                                .anyRequest().authenticated())

                                // Stateless Session (No server-side session)
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                // Authentication Provider set karo
                                .authenticationProvider(authenticationProvider)

                                // JWT Filter add karo (UsernamePasswordAuthenticationFilter se pehle)
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}

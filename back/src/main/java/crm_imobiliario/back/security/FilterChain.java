package crm_imobiliario.back.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import crm_imobiliario.back.model.service.UsuarioService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

//Um Filter fica no caminho entre o cliente e o Controller.
@Configuration
@EnableWebSecurity
public class FilterChain {

    @Bean
    public org.springframework.security.web.SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityFilter filter, RateLimitFilter rateLimitFilter) throws Exception {
        return http
                .cors(cors -> {})
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> {
                    headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable);
                    headers.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000));
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"));
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            String body = "{\"success\":false,\"message\":\"Não autenticado. Faça login novamente.\",\"code\":\"AUTH_REQUIRED\"}";
                            try { // tenta usar ApiErrorResponse se ObjectMapper disponível
                                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                                crm_imobiliario.back.util.ApiErrorResponse err = crm_imobiliario.back.util.ApiErrorResponse.builder()
                                        .success(false).message("Não autenticado. Faça login novamente.").code("AUTH_REQUIRED")
                                        .path(request.getRequestURI()).timestamp(java.time.Instant.now()).build();
                                body = mapper.writeValueAsString(err);
                            } catch (Exception ignored) {}
                            response.getWriter().write(body);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            String body = "{\"success\":false,\"message\":\"Você não possui permissão para realizar esta ação.\",\"code\":\"FORBIDDEN\"}";
                            try {
                                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                                crm_imobiliario.back.util.ApiErrorResponse err = crm_imobiliario.back.util.ApiErrorResponse.builder()
                                        .success(false).message("Você não possui permissão para realizar esta ação.").code("FORBIDDEN")
                                        .path(request.getRequestURI()).timestamp(java.time.Instant.now()).build();
                                body = mapper.writeValueAsString(err);
                            } catch (Exception ignored) {}
                            response.getWriter().write(body);
                        })
                )
                .authorizeHttpRequests(authorization -> {
                    authorization.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll();
                    authorization.requestMatchers(HttpMethod.POST,"/login").permitAll();
                    authorization.requestMatchers(HttpMethod.POST,"/auth/refresh").permitAll();
                    authorization.requestMatchers("/h2-console/**").permitAll();
                    authorization.anyRequest().authenticated();
                })
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UsuarioService usuarioService) {
        return new CustomUserDetails(usuarioService);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(List.of("http://localhost:3000", "http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization","Content-Type","X-Requested-With","Accept"));
        configuration.setExposedHeaders(List.of("Set-Cookie"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}

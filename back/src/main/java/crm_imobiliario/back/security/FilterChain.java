package crm_imobiliario.back.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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

    // nomes dos papéis gravados na tabela "papel" (CustomUserDetails os expõe como ROLE_<papel>)
    static final String ADMIN = "admin";
    static final String GESTOR = "gestor";

    /** Origens do frontend autorizadas (CORS); separadas por vírgula. */
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    public org.springframework.security.web.SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityFilter filter, RateLimitFilter rateLimitFilter) throws Exception {
        return http
                .cors(cors -> {})
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> {
                    // frameOptions fica no padrão (DENY): o console H2 não é mais exposto
                    headers.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000));
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"));
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                JsonErrorWriter.escrever(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "Sua sessão expirou ou você não está conectado. Faça login para continuar.", "AUTH_REQUIRED"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                JsonErrorWriter.escrever(request, response, HttpServletResponse.SC_FORBIDDEN,
                                        "Você não tem permissão para acessar esta funcionalidade com o seu perfil de acesso.", "FORBIDDEN"))
                )
                .authorizeHttpRequests(authorization -> {
                    authorization.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll();
                    authorization.requestMatchers(HttpMethod.POST, "/login").permitAll();
                    authorization.requestMatchers(HttpMethod.POST, "/auth/refresh").permitAll();
                    // logout só revoga os tokens que o próprio cliente apresenta; precisa funcionar
                    // mesmo com o access token já expirado
                    authorization.requestMatchers(HttpMethod.POST, "/auth/logout").permitAll();

                    // ---- Autorização por papel (antes só existia "autenticado"; o front escondia as
                    // telas, mas qualquer usuário logado conseguia chamar estes endpoints direto) ----

                    // usuários: próprio perfil/senha para todos; leitura para admin/gestor (telas de
                    // redistribuição e equipes listam corretores); escrita somente admin
                    authorization.requestMatchers("/usuarios/me", "/usuarios/minha-senha").authenticated();
                    authorization.requestMatchers(HttpMethod.GET, "/usuarios", "/usuarios/*").hasAnyRole(ADMIN, GESTOR);
                    authorization.requestMatchers("/usuarios/**").hasRole(ADMIN);

                    // papéis: leitura para autenticados; criar/excluir somente admin
                    authorization.requestMatchers(HttpMethod.GET, "/papel").authenticated();
                    authorization.requestMatchers("/papel/**").hasRole(ADMIN);

                    // equipes: leitura para autenticados; alterações somente admin
                    authorization.requestMatchers(HttpMethod.GET, "/equipes").authenticated();
                    authorization.requestMatchers("/equipes/**").hasRole(ADMIN);

                    // dashboard do gestor e redistribuição de clientes: admin/gestor
                    authorization.requestMatchers("/dashboard/gestor/**", "/dashboard/gestor").hasAnyRole(ADMIN, GESTOR);
                    authorization.requestMatchers("/leads/aguardando-redistribuicao", "/leads/redistribuir", "/leads/redistribuir-em-massa",
                            "/leads/*/redistribuir/*").hasAnyRole(ADMIN, GESTOR);

                    // sincronização manual do catálogo de empreendimentos: somente admin
                    authorization.requestMatchers(HttpMethod.POST, "/api/v1/empreendimentos/sync").hasRole(ADMIN);

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

        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization","Content-Type","X-Requested-With","Accept"));
        configuration.setExposedHeaders(List.of("Set-Cookie"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}

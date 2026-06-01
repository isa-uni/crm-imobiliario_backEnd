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


@Configuration
@EnableWebSecurity
public class FilterChain {

    @Bean
    public org.springframework.security.web.SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityFilter filter) throws Exception {
        return http
                .cors(cors -> {})
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorization -> {
                    authorization.requestMatchers(HttpMethod.POST,"/login").permitAll();
                    authorization.requestMatchers(HttpMethod.POST,"/usuarios/cadastrar").permitAll();
                    authorization.requestMatchers(HttpMethod.POST,"/leads/cadastrar").permitAll();
                    authorization.requestMatchers(HttpMethod.POST,"/imovel/cadastrar").permitAll();
                    authorization.requestMatchers(HttpMethod.GET,"/leads").permitAll();
                    authorization.requestMatchers(HttpMethod.GET,"/imovel").permitAll();
                    authorization.requestMatchers(HttpMethod.GET,"/imovel/disponivel").permitAll();
                    authorization.requestMatchers(HttpMethod.GET,"/leads/metrics").permitAll();
                    // authorization.requestMatchers(HttpMethod.POST,"/usuarios/cadastrar").hasRole("admin");
                    authorization.requestMatchers(HttpMethod.POST,"/papel/novo").permitAll();
                    // authorization.requestMatchers(HttpMethod.POST,"/papel/novo").hasRole("admin");
                    // authorization.requestMatchers(HttpMethod.DELETE,"/usuarios/deletar/**").hasRole("admin");
                    authorization.requestMatchers(HttpMethod.DELETE,"/usuarios/deletar/**").permitAll();
                    authorization.requestMatchers(HttpMethod.DELETE,"/leads/deletar/**").permitAll();
                    authorization.requestMatchers(HttpMethod.PUT,"/imovel/inativar/**").permitAll();
                    authorization.requestMatchers(HttpMethod.PUT,"/leads/inativar/**").permitAll();
                    authorization.requestMatchers(HttpMethod.PUT,"/imovel/ativar/**").permitAll();
                    authorization.requestMatchers(HttpMethod.PUT,"/leads/ativar/**").permitAll();
                    authorization.requestMatchers(HttpMethod.PUT,"/imovel/atualizar/**").permitAll();
                    authorization.requestMatchers(HttpMethod.PUT,"/leads/atualizar/**").permitAll();
                    authorization.requestMatchers("/h2-console/**").permitAll();
                    // authorization.requestMatchers("/turmas/**").hasAnyRole("admin", "professor");
                    // authorization.requestMatchers("/usuario-turma/**").hasAnyRole("admin", "professor");
                    authorization.anyRequest().authenticated();
                })
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

        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}

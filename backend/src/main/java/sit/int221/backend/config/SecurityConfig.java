package sit.int221.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import sit.int221.backend.security.CustomAuthenticationEntryPoint;
import sit.int221.backend.security.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint customAuthEntryPoint;
    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(CustomAuthenticationEntryPoint entryPoint,
                          JwtAuthenticationFilter jwtAuthFilter) {
        this.customAuthEntryPoint = entryPoint;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/v2/auth/login",
                                "/v2/users/register",
                                "/v2/auth/verify-email",
                                "/v2/auth/resend-verification",
                                "/v2/auth/refresh"
                        ).permitAll()
                        // ปิดการเข้าฟรีทุก brand/sale-item
                        .requestMatchers(HttpMethod.GET,
                                "/v1/sale-items/**","/v2/sale-items/**", "/v2/brands/**", "/media/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e.authenticationEntryPoint(customAuthEntryPoint))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

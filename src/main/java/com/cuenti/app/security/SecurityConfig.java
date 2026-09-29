package com.cuenti.app.security;

import com.cuenti.app.views.LoginView;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.firewall.StrictHttpFirewall;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final LoginRateLimitFilter loginRateLimitFilter;
    private final IdleSessionFilter idleSessionFilter;
    private final SessionValidityFilter sessionValidityFilter;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public HttpFirewall allowDoubleSlashFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedDoubleSlash(true);
        firewall.setAllowSemicolon(true);
        return firewall;
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer(HttpFirewall allowDoubleSlashFirewall) {
        return web -> web.httpFirewall(allowDoubleSlashFirewall);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                // Bearer tokens only: HTTP Basic would let any account use the API with its
                // password on every request, bypassing the per-user API access switch
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain vaadinSecurityFilterChain(HttpSecurity http) throws Exception {
        http.headers(h -> h
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                // Vaadin needs inline and eval'd scripts, so no script-src; these directives
                // still block clickjacking, plugin content, <base> hijacking and form exfiltration
                .contentSecurityPolicy(c -> c.policyDirectives(
                        "frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'")));
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/images/**").permitAll()
                .requestMatchers("/actuator/health").permitAll());
        http.addFilterBefore(idleSessionFilter, SecurityContextHolderFilter.class);
        http.addFilterBefore(sessionValidityFilter, SecurityContextHolderFilter.class);
        http.addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.with(VaadinSecurityConfigurer.vaadin(), c -> c.loginView(LoginView.class));
        return http.build();
    }
}

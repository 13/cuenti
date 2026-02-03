package com.cuenti.homebanking.security;

import com.cuenti.homebanking.views.LoginView;
import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Security configuration for the application.
 * Configures Spring Security with Vaadin integration and API support.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends VaadinWebSecurity {

    /**
     * Custom HttpFirewall to allow URLs containing double slashes "//".
     */
    @Bean
    public HttpFirewall allowDoubleSlashFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedDoubleSlash(true); // Allow "//" in URLs
        return firewall;
    }

    /**
     * Apply the custom firewall to Spring Security.
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer(HttpFirewall allowDoubleSlashFirewall) {
        return web -> web.httpFirewall(allowDoubleSlashFirewall);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Allow public access to the images directory
        http.authorizeHttpRequests(auth ->
                auth.requestMatchers(new AntPathRequestMatcher("/images/**")).permitAll()
        );

        // API Security: Enable basic auth for /api/** endpoints and disable CSRF for API
        http.authorizeHttpRequests(auth ->
                        auth.requestMatchers(new AntPathRequestMatcher("/api/**")).authenticated()
                ).httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers(new AntPathRequestMatcher("/api/**")));

        // Configure Vaadin-specific security
        super.configure(http);
        setLoginView(http, LoginView.class);
    }
}

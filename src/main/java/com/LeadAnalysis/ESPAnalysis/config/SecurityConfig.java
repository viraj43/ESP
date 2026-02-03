package com.LeadAnalysis.ESPAnalysis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/login").permitAll()
                        .anyRequest().authenticated() // this ensure that can land into homepage only if authenticated
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .sessionManagement(session -> session
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                )
                .csrf(csrf -> csrf.disable()); // Disable CSRF for simplicity

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // Create users with email as username
        UserDetails admin = User.builder()
                .username("natasha@frugaltesting.com")
                .password(passwordEncoder().encode("natasha@2025"))
                .roles("ADMIN", "USER")
                .build();

        UserDetails analyst1 = User.builder()
                .username("venkata@frugaltestingid.com")
                .password(passwordEncoder().encode("Esp@2025"))
                .roles("USER")
                .build();

        UserDetails analyst2 = User.builder()
                .username("bhojaraj@frugaltestingin.com")
                .password(passwordEncoder().encode("Esp@2025"))
                .roles("USER")
                .build();

        UserDetails manager = User.builder()
                .username("bsairam@frugaltestingin.com")
                .password(passwordEncoder().encode("ESP@2025"))
                .roles("MANAGER", "USER")
                .build();
        UserDetails user2 = User.builder()
                .username("viraj@frugaltesting.com")
                .password(passwordEncoder().encode("ESP@2025"))
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(admin, analyst1, analyst2, manager,user2);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
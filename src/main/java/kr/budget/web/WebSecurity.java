package kr.budget.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class WebSecurity {
    @Bean
    SecurityFilterChain demoSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/api/**", "/webjars/**", "/assets/**", "/error").permitAll()
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.FORWARD).permitAll()
                .anyRequest().denyAll())
                .logout(logout -> logout.logoutUrl("/api/demo/end").logoutSuccessUrl("/"))
                .build();
    }
}

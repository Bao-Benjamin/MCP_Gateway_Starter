package com.thesis.todo.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Quy tắc phân quyền của ứng dụng. MCP Gateway đọc chính các quy tắc này để lọc Tool, nên không
 * phải khai báo quyền lần thứ hai.
 */
@Configuration
@EnableConfigurationProperties(ApiKeyProperties.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyProperties apiKeys) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.addFilterBefore(new ApiKeyAuthenticationFilter(apiKeys), AnonymousAuthenticationFilter.class)
			.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.authorizeHttpRequests(auth -> auth
				// Endpoint MCP mở cho mọi người; quyền được kiểm tra theo từng Tool
				.requestMatchers("/mcp", "/v3/api-docs/**", "/error").permitAll()
				.requestMatchers("/api/public/**").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.requestMatchers("/api/tasks/**").hasRole("USER")
				.anyRequest().denyAll());
		return http.build();
	}

}

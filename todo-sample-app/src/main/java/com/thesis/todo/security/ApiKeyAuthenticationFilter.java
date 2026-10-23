package com.thesis.todo.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Xác thực bằng header {@code X-API-Key}.
 * <ul>
 * <li>Không có header: tiếp tục như khách (anonymous).</li>
 * <li>Key hợp lệ: gắn người dùng và role vào SecurityContext.</li>
 * <li>Key sai: trả 401 ngay.</li>
 * </ul>
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-API-Key";

	private final ApiKeyProperties properties;

	public ApiKeyAuthenticationFilter(ApiKeyProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String apiKey = request.getHeader(HEADER);
		if (apiKey != null) {
			ApiKeyProperties.User user = this.properties.keys().get(apiKey);
			if (user == null) {
				response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid API key");
				return;
			}
			var authorities = AuthorityUtils
				.createAuthorityList(user.roles().stream().map(r -> "ROLE_" + r).toArray(String[]::new));
			SecurityContextHolder.getContext()
				.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.username(), null, authorities));
		}
		chain.doFilter(request, response);
	}

}

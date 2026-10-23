package com.thesis.mcpgateway.security;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tách riêng để chỉ nạp class của Spring Security khi ứng dụng có Spring Security.
 */
public final class SecurityContextAccessor {

	private SecurityContextAccessor() {
	}

	public static Object currentAuthentication() {
		return SecurityContextHolder.getContext().getAuthentication();
	}

}

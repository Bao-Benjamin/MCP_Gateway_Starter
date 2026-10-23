package com.thesis.todo.security;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Danh sách API key dùng cho demo, khai báo trong application.yml.
 *
 * @param keys ánh xạ API key -> người dùng
 */
@ConfigurationProperties(prefix = "demo.security")
public record ApiKeyProperties(Map<String, User> keys) {

	/**
	 * @param username tên người dùng
	 * @param roles vai trò, ví dụ USER, ADMIN
	 */
	public record User(String username, List<String> roles) {
	}

}

package com.thesis.todo.user;

import java.util.List;

/**
 * Tài khoản người dùng (thông tin công khai, không chứa API key).
 */
public record UserAccount(String username, List<String> roles, boolean locked) {
}

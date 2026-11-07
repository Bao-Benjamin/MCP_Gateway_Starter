package com.thesis.todo.user;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.thesis.todo.security.ApiKeyProperties;

/**
 * Danh sách người dùng lấy từ cấu hình API key; trạng thái khóa lưu trong bộ nhớ.
 */
@Service
public class UserAccountService {

	private final Map<String, UserAccount> accounts = new ConcurrentHashMap<>();

	public UserAccountService(ApiKeyProperties properties) {
		properties.keys()
			.values()
			.forEach(u -> this.accounts.put(u.username(), new UserAccount(u.username(), u.roles(), false)));
	}

	public List<UserAccount> findAll() {
		return this.accounts.values().stream().sorted(Comparator.comparing(UserAccount::username)).toList();
	}

	public Optional<UserAccount> find(String username) {
		return Optional.ofNullable(this.accounts.get(username));
	}

	public boolean isLocked(String username) {
		return find(username).map(UserAccount::locked).orElse(false);
	}

	public Optional<UserAccount> setLocked(String username, boolean locked) {
		return find(username).map(a -> {
			UserAccount updated = new UserAccount(a.username(), a.roles(), locked);
			this.accounts.put(username, updated);
			return updated;
		});
	}

}

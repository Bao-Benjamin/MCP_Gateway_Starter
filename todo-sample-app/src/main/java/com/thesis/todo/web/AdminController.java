package com.thesis.todo.web;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.thesis.todo.task.Task;
import com.thesis.todo.task.TaskService;
import com.thesis.todo.task.TaskStatistics;
import com.thesis.todo.user.UserAccount;
import com.thesis.todo.user.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

/**
 * Chức năng quản trị, chỉ ADMIN dùng được (quy tắc nằm trong SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final TaskService taskService;

	private final UserAccountService userAccountService;

	public AdminController(TaskService taskService, UserAccountService userAccountService) {
		this.taskService = taskService;
		this.userAccountService = userAccountService;
	}

	@Operation(operationId = "listUsers", summary = "Liệt kê tất cả người dùng (chỉ ADMIN)")
	@GetMapping("/users")
	public List<UserAccount> users() {
		return this.userAccountService.findAll();
	}

	@Operation(operationId = "lockUser", summary = "Khóa tài khoản một người dùng (chỉ ADMIN)")
	@PatchMapping("/users/{username}/lock")
	public UserAccount lock(@Parameter(description = "Tên người dùng") @PathVariable String username,
			Principal principal) {
		if (username.equals(principal.getName())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot lock your own account");
		}
		return setLocked(username, true);
	}

	@Operation(operationId = "unlockUser", summary = "Mở khóa tài khoản một người dùng (chỉ ADMIN)")
	@PatchMapping("/users/{username}/unlock")
	public UserAccount unlock(@Parameter(description = "Tên người dùng") @PathVariable String username) {
		return setLocked(username, false);
	}

	@Operation(operationId = "listAllTasks", summary = "Liệt kê task của mọi người dùng (chỉ ADMIN)")
	@GetMapping("/tasks")
	public List<Task> tasks(
			@Parameter(description = "Chỉ lấy task của người dùng này") @RequestParam(required = false) String owner) {
		return this.taskService.findAll(owner);
	}

	@Operation(operationId = "getTaskStatistics", summary = "Thống kê task của từng người dùng (chỉ ADMIN)")
	@GetMapping("/stats")
	public Map<String, TaskStatistics> stats() {
		return this.taskService.statisticsByOwner();
	}

	private UserAccount setLocked(String username, boolean locked) {
		return this.userAccountService.setLocked(username, locked)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + username + " not found"));
	}

}

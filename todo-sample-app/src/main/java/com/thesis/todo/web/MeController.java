package com.thesis.todo.web;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.thesis.todo.task.TaskService;
import com.thesis.todo.task.TaskStatistics;
import com.thesis.todo.user.UserAccount;
import com.thesis.todo.user.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;

/**
 * Thông tin của chính người dùng đang đăng nhập.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

	private final UserAccountService userAccountService;

	private final TaskService taskService;

	public MeController(UserAccountService userAccountService, TaskService taskService) {
		this.userAccountService = userAccountService;
		this.taskService = taskService;
	}

	@Operation(operationId = "getMyProfile", summary = "Xem thông tin tài khoản của người dùng hiện tại")
	@GetMapping
	public UserAccount profile(Principal principal) {
		return this.userAccountService.find(principal.getName())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	@Operation(operationId = "getMyStatistics", summary = "Thống kê task của người dùng hiện tại: tổng, đã xong, chưa xong, quá hạn")
	@GetMapping("/stats")
	public TaskStatistics statistics(Principal principal) {
		return this.taskService.statisticsOf(principal.getName());
	}

	@Operation(operationId = "listMyTags", summary = "Liệt kê các nhãn người dùng hiện tại đã dùng")
	@GetMapping("/tags")
	public List<String> tags(Principal principal) {
		return this.taskService.tagsOf(principal.getName());
	}

}

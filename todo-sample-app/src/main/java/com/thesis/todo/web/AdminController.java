package com.thesis.todo.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thesis.todo.task.TaskService;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final TaskService taskService;

	public AdminController(TaskService taskService) {
		this.taskService = taskService;
	}

	@Operation(operationId = "getTaskStatistics", summary = "Thống kê số task của từng người dùng (chỉ ADMIN)")
	@GetMapping("/stats")
	public Map<String, Long> stats() {
		return this.taskService.countByOwner();
	}

}

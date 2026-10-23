package com.thesis.todo.web;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.thesis.todo.task.CreateTaskRequest;
import com.thesis.todo.task.Task;
import com.thesis.todo.task.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	private final TaskService taskService;

	public TaskController(TaskService taskService) {
		this.taskService = taskService;
	}

	@Operation(operationId = "listMyTasks", summary = "Liệt kê task của người dùng hiện tại")
	@GetMapping
	public List<Task> list(Principal principal) {
		return this.taskService.findByOwner(principal.getName());
	}

	@Operation(operationId = "createTask", summary = "Tạo task mới cho người dùng hiện tại")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Task create(@Valid @RequestBody CreateTaskRequest request, Principal principal) {
		return this.taskService.create(principal.getName(), request.title());
	}

}

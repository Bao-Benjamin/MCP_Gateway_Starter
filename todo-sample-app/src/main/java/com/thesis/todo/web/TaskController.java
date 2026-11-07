package com.thesis.todo.web;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.thesis.todo.task.Task;
import com.thesis.todo.task.TaskRequest;
import com.thesis.todo.task.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;

/**
 * Task của người dùng hiện tại. Task của người khác trả về 404, như thể không tồn tại.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	private final TaskService taskService;

	public TaskController(TaskService taskService) {
		this.taskService = taskService;
	}

	@Operation(operationId = "listMyTasks", summary = "Liệt kê task của người dùng hiện tại, có thể lọc theo trạng thái và nhãn")
	@GetMapping
	public List<Task> list(
			@Parameter(description = "true: chỉ task đã xong, false: chỉ task chưa xong") @RequestParam(required = false) Boolean done,
			@Parameter(description = "Chỉ lấy task có nhãn này") @RequestParam(required = false) String tag,
			Principal principal) {
		return this.taskService.findByOwner(principal.getName(), done, tag);
	}

	@Operation(operationId = "createTask", summary = "Tạo task mới cho người dùng hiện tại")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Task create(@Valid @RequestBody TaskRequest request, Principal principal) {
		return this.taskService.create(principal.getName(), request);
	}

	@Operation(operationId = "getTask", summary = "Xem chi tiết một task theo id")
	@GetMapping("/{id}")
	public Task get(@Parameter(description = "Id của task") @PathVariable long id, Principal principal) {
		return this.taskService.findByIdAndOwner(id, principal.getName()).orElseThrow(() -> notFound(id));
	}

	@Operation(operationId = "updateTask", summary = "Sửa tiêu đề, hạn và nhãn của một task")
	@PutMapping("/{id}")
	public Task update(@Parameter(description = "Id của task") @PathVariable long id,
			@Valid @RequestBody TaskRequest request, Principal principal) {
		return this.taskService.update(id, principal.getName(), request).orElseThrow(() -> notFound(id));
	}

	@Operation(operationId = "completeTask", summary = "Đánh dấu một task là đã hoàn thành")
	@PatchMapping("/{id}/complete")
	public Task complete(@Parameter(description = "Id của task") @PathVariable long id, Principal principal) {
		return this.taskService.complete(id, principal.getName()).orElseThrow(() -> notFound(id));
	}

	@Operation(operationId = "deleteTask", summary = "Xóa một task")
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@Parameter(description = "Id của task") @PathVariable long id, Principal principal) {
		if (!this.taskService.delete(id, principal.getName())) {
			throw notFound(id);
		}
	}

	private static ResponseStatusException notFound(long id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Task " + id + " not found");
	}

}

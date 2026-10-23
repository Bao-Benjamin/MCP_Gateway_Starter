package com.thesis.todo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu để tạo task mới.
 */
public record CreateTaskRequest(
		@Schema(description = "Tiêu đề công việc", example = "Viết chương 1 luận văn") @NotBlank @Size(max = 200) String title) {
}

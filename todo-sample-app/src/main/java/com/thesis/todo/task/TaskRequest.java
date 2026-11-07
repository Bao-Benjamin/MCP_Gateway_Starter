package com.thesis.todo.task;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu để tạo hoặc sửa task.
 */
public record TaskRequest(
		@Schema(description = "Tiêu đề công việc", example = "Viết chương 1 luận văn") @NotBlank @Size(max = 200) String title,
		@Schema(description = "Hạn hoàn thành (yyyy-MM-dd), có thể bỏ trống", example = "2026-11-30") LocalDate dueDate,
		@Schema(description = "Nhãn để phân loại, ví dụ: thesis, personal") @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags) {

	public List<String> tagsOrEmpty() {
		return (this.tags != null) ? List.copyOf(this.tags) : List.of();
	}

}

package com.thesis.todo.task;

import java.time.LocalDate;
import java.util.List;

/**
 * Một công việc trong danh sách to-do.
 */
public record Task(long id, String owner, String title, LocalDate dueDate, List<String> tags, boolean done) {

	public boolean isOverdue(LocalDate today) {
		return !this.done && this.dueDate != null && this.dueDate.isBefore(today);
	}

}

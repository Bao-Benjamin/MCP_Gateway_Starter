package com.thesis.todo.task;

/**
 * Một công việc trong danh sách to-do.
 */
public record Task(long id, String owner, String title, boolean done) {
}

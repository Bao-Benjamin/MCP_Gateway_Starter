package com.thesis.todo.task;

/**
 * Thống kê task của một người dùng.
 */
public record TaskStatistics(long total, long done, long open, long overdue) {
}

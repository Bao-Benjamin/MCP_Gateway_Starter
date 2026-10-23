package com.thesis.todo.task;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * Lưu task trong bộ nhớ (chưa cần database cho prototype).
 */
@Service
public class TaskService {

	private final Map<Long, Task> tasks = new ConcurrentHashMap<>();

	private final AtomicLong nextId = new AtomicLong(1);

	/** Phân quyền theo dữ liệu: mỗi người chỉ thấy task của mình. */
	public List<Task> findByOwner(String owner) {
		return this.tasks.values().stream().filter(t -> t.owner().equals(owner)).toList();
	}

	public Task create(String owner, String title) {
		Task task = new Task(this.nextId.getAndIncrement(), owner, title, false);
		this.tasks.put(task.id(), task);
		return task;
	}

	public Map<String, Long> countByOwner() {
		return this.tasks.values()
			.stream()
			.collect(Collectors.groupingBy(Task::owner, Collectors.counting()));
	}

}

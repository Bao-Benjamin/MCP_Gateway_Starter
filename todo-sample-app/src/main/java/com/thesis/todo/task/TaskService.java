package com.thesis.todo.task;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

/**
 * Lưu task trong bộ nhớ (chưa cần database).
 * <p>
 * Phân quyền theo dữ liệu nằm ở đây: mọi thao tác của người dùng thường đều lọc theo
 * {@code owner}, nên không ai đọc hay sửa được task của người khác.
 */
@Service
public class TaskService {

	private final Map<Long, Task> tasks = new ConcurrentHashMap<>();

	private final AtomicLong nextId = new AtomicLong(1);

	private final Clock clock;

	public TaskService() {
		this(Clock.systemDefaultZone());
	}

	TaskService(Clock clock) {
		this.clock = clock;
	}

	public List<Task> findByOwner(String owner, Boolean done, String tag) {
		return ownedBy(owner).filter(t -> done == null || t.done() == done)
			.filter(t -> tag == null || t.tags().contains(tag))
			.toList();
	}

	public Optional<Task> findByIdAndOwner(long id, String owner) {
		return Optional.ofNullable(this.tasks.get(id)).filter(t -> t.owner().equals(owner));
	}

	public Task create(String owner, TaskRequest request) {
		Task task = new Task(this.nextId.getAndIncrement(), owner, request.title(), request.dueDate(),
				request.tagsOrEmpty(), false);
		this.tasks.put(task.id(), task);
		return task;
	}

	public Optional<Task> update(long id, String owner, TaskRequest request) {
		return findByIdAndOwner(id, owner).map(old -> save(new Task(old.id(), owner, request.title(),
				request.dueDate(), request.tagsOrEmpty(), old.done())));
	}

	public Optional<Task> complete(long id, String owner) {
		return findByIdAndOwner(id, owner)
			.map(old -> save(new Task(old.id(), owner, old.title(), old.dueDate(), old.tags(), true)));
	}

	public boolean delete(long id, String owner) {
		return findByIdAndOwner(id, owner).map(t -> this.tasks.remove(t.id()) != null).orElse(false);
	}

	public List<String> tagsOf(String owner) {
		return List.copyOf(ownedBy(owner).flatMap(t -> t.tags().stream()).collect(Collectors.toCollection(TreeSet::new)));
	}

	public TaskStatistics statisticsOf(String owner) {
		return statistics(ownedBy(owner).toList());
	}

	/** Dành cho ADMIN: xem task của mọi người, có thể lọc theo chủ sở hữu. */
	public List<Task> findAll(String owner) {
		return this.tasks.values()
			.stream()
			.filter(t -> owner == null || t.owner().equals(owner))
			.sorted(Comparator.comparingLong(Task::id))
			.toList();
	}

	/** Dành cho ADMIN: thống kê theo từng người dùng. */
	public Map<String, TaskStatistics> statisticsByOwner() {
		return this.tasks.values()
			.stream()
			.collect(Collectors.groupingBy(Task::owner))
			.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, e -> statistics(e.getValue())));
	}

	private Stream<Task> ownedBy(String owner) {
		return this.tasks.values().stream().filter(t -> t.owner().equals(owner)).sorted(Comparator.comparingLong(Task::id));
	}

	private Task save(Task task) {
		this.tasks.put(task.id(), task);
		return task;
	}

	private TaskStatistics statistics(List<Task> tasks) {
		LocalDate today = LocalDate.now(this.clock);
		long done = tasks.stream().filter(Task::done).count();
		long overdue = tasks.stream().filter(t -> t.isOverdue(today)).count();
		return new TaskStatistics(tasks.size(), done, tasks.size() - done, overdue);
	}

}

package com.thesis.todo;

import static com.thesis.todo.McpTestClient.call;
import static com.thesis.todo.McpTestClient.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.json.JsonMapper;

/**
 * Các luồng nghiệp vụ chạy hoàn toàn qua MCP Tool.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskFlowTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@LocalServerPort
	int port;

	private McpSchema.CallToolResult callOk(String apiKey, String tool, Map<String, Object> args) {
		McpSchema.CallToolResult result = call(this.port, apiKey, tool, args);
		assertThat(result.isError()).as(text(result)).isFalse();
		return result;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> json(McpSchema.CallToolResult result) {
		return JSON.readValue(text(result), Map.class);
	}

	private long createTask(String apiKey, String title, List<String> tags) {
		Map<String, Object> task = json(
				callOk(apiKey, "createTask", Map.of("body", Map.of("title", title, "tags", tags))));
		return ((Number) task.get("id")).longValue();
	}

	@Test
	void createUpdateCompleteAndDeleteTask() {
		long id = createTask("alice-key", "Viết chương 2", List.of("thesis"));

		Map<String, Object> updated = json(callOk("alice-key", "updateTask",
				Map.of("id", id, "body", Map.of("title", "Viết chương 2 (bản sửa)", "dueDate", "2026-12-01",
						"tags", List.of("thesis", "urgent")))));
		assertThat(updated).containsEntry("title", "Viết chương 2 (bản sửa)").containsEntry("dueDate", "2026-12-01");

		assertThat(json(callOk("alice-key", "completeTask", Map.of("id", id)))).containsEntry("done", true);
		assertThat(text(callOk("alice-key", "listMyTasks", Map.of("done", true, "tag", "urgent"))))
			.contains("Viết chương 2 (bản sửa)");
		assertThat(text(callOk("alice-key", "listMyTags", Map.of()))).contains("urgent");

		callOk("alice-key", "deleteTask", Map.of("id", id));
		McpSchema.CallToolResult afterDelete = call(this.port, "alice-key", "getTask", Map.of("id", id));
		assertThat(afterDelete.isError()).isTrue();
		assertThat(text(afterDelete)).startsWith("HTTP 404").contains("Task " + id + " not found");
	}

	@Test
	void usersCannotReadOrChangeEachOthersTasks() {
		long id = createTask("bob-key", "Việc riêng của bob", List.of("personal"));

		// alice thấy được Tool (cùng vai trò USER) nhưng REST API trả 404 vì không phải task của alice
		for (String tool : List.of("getTask", "completeTask", "deleteTask")) {
			McpSchema.CallToolResult result = call(this.port, "alice-key", tool, Map.of("id", id));
			assertThat(text(result)).as(tool).startsWith("HTTP 404");
		}
		assertThat(text(callOk("alice-key", "listMyTasks", Map.of()))).doesNotContain("Việc riêng của bob");

		// ADMIN xem được task của mọi người qua Tool quản trị
		assertThat(text(callOk("admin-key", "listAllTasks", Map.of("owner", "bob"))))
			.contains("Việc riêng của bob");
	}

	@Test
	void lockedUserCannotUseMcp() {
		callOk("admin-key", "lockUser", Map.of("username", "bob"));
		try {
			// Tài khoản bị khóa: bị chặn ngay từ tầng xác thực, không kết nối MCP được
			assertThatThrownBy(() -> McpTestClient.toolNames(this.port, "bob-key")).isInstanceOf(Exception.class);
		}
		finally {
			callOk("admin-key", "unlockUser", Map.of("username", "bob"));
		}
		assertThat(McpTestClient.toolNames(this.port, "bob-key")).contains("listMyTasks");
	}

	@Test
	void adminCannotLockOwnAccount() {
		McpSchema.CallToolResult result = call(this.port, "admin-key", "lockUser", Map.of("username", "admin"));
		assertThat(result.isError()).isTrue();
		assertThat(text(result)).startsWith("HTTP 400").contains("cannot lock your own account");
	}

	@Test
	void invalidInputIsRejected() {
		// Sai kiểu dữ liệu: MCP server từ chối theo inputSchema, chưa gọi tới REST API
		McpSchema.CallToolResult wrongType = call(this.port, "alice-key", "getTask", Map.of("id", "abc"));
		assertThat(wrongType.isError()).isTrue();

		// Đúng kiểu nhưng sai quy tắc nghiệp vụ (@NotBlank): REST API trả 400
		McpSchema.CallToolResult blankTitle = call(this.port, "alice-key", "createTask",
				Map.of("body", Map.of("title", "")));
		assertThat(blankTitle.isError()).isTrue();
		assertThat(text(blankTitle)).startsWith("HTTP 400");
	}

}

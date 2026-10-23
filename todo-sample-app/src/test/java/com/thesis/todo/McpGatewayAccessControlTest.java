package com.thesis.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Kiểm thử end-to-end: một MCP client thật kết nối tới ứng dụng to-do qua HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpGatewayAccessControlTest {

	@LocalServerPort
	int port;

	private McpSyncClient client(String apiKey) {
		HttpRequest.Builder request = HttpRequest.newBuilder();
		if (apiKey != null) {
			request.header("X-API-Key", apiKey);
		}
		McpSyncClient client = McpClient
			.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + this.port)
				.endpoint("/mcp")
				.requestBuilder(request)
				.build())
			.build();
		client.initialize();
		return client;
	}

	private List<String> toolNames(String apiKey) {
		try (McpSyncClient client = client(apiKey)) {
			return client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
		}
	}

	private McpSchema.CallToolResult call(String apiKey, String tool, Map<String, Object> args) {
		try (McpSyncClient client = client(apiKey)) {
			return client.callTool(new McpSchema.CallToolRequest(tool, args));
		}
	}

	private static String text(McpSchema.CallToolResult result) {
		return ((McpSchema.TextContent) result.content().get(0)).text();
	}

	@Test
	void guestSeesOnlyPublicTools() {
		assertThat(toolNames(null)).containsExactly("getAppInfo");
	}

	@Test
	void userDoesNotSeeAdminTools() {
		assertThat(toolNames("alice-key")).containsExactlyInAnyOrder("getAppInfo", "listMyTasks", "createTask");
	}

	@Test
	void adminSeesAllTools() {
		assertThat(toolNames("admin-key")).containsExactlyInAnyOrder("getAppInfo", "listMyTasks", "createTask",
				"getTaskStatistics");
	}

	@Test
	void hiddenToolCannotBeCalledByName() {
		// Client tự gửi tên Tool bị ẩn: Gateway kiểm tra quyền lại và từ chối như Tool không tồn tại
		assertThatThrownBy(() -> call("alice-key", "getTaskStatistics", Map.of()))
			.hasMessageContaining("Unknown tool");
		assertThatThrownBy(() -> call(null, "listMyTasks", Map.of())).hasMessageContaining("Unknown tool");
	}

	@Test
	void toolCallRunsRestEndpointAsTheCaller() {
		McpSchema.CallToolResult created = call("bob-key", "createTask",
				Map.of("body", Map.of("title", "Viết chương 1")));
		assertThat(created.isError()).isFalse();
		assertThat(text(created)).contains("\"owner\":\"bob\"");

		// Phân quyền theo dữ liệu nằm trong REST API vẫn được giữ: người khác không thấy task của bob
		assertThat(text(call("bob-key", "listMyTasks", Map.of()))).contains("Viết chương 1");
		assertThat(text(call("alice-key", "listMyTasks", Map.of()))).doesNotContain("Viết chương 1");
	}

	@Test
	void validationErrorIsReturnedAsToolError() {
		McpSchema.CallToolResult result = call("alice-key", "createTask", Map.of("body", Map.of("title", "")));
		assertThat(result.isError()).isTrue();
		assertThat(text(result)).startsWith("HTTP 400");
	}

}

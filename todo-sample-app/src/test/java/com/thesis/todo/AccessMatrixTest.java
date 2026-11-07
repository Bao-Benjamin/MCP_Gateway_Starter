package com.thesis.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kiểm tra toàn bộ ma trận quyền trong access-matrix.csv: với mỗi vai trò và mỗi Tool, Tool có
 * hiện trong tools/list không, và gọi thẳng bằng tên có bị chặn không.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccessMatrixTest {

	/** Vai trò -> API key dùng để đóng vai đó (null là khách, không gửi key). */
	private static final Map<String, String> ROLE_KEYS = new LinkedHashMap<>();

	static {
		ROLE_KEYS.put("GUEST", null);
		ROLE_KEYS.put("USER", "alice-key");
		ROLE_KEYS.put("ADMIN", "admin-key");
	}

	private static final List<String> ROLES = List.copyOf(ROLE_KEYS.keySet());

	@LocalServerPort
	int port;

	record Row(String tool, Map<String, Boolean> allowed, Map<String, Object> arguments) {

		@Override
		public String toString() {
			return this.tool;
		}

	}

	@SuppressWarnings("unchecked")
	static List<Row> matrix() throws IOException {
		JsonMapper json = JsonMapper.builder().build();
		List<Row> rows = new ArrayList<>();
		try (InputStream in = AccessMatrixTest.class.getResourceAsStream("/access-matrix.csv")) {
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				String[] cells = line.split(";", 5);
				Map<String, Boolean> allowed = new LinkedHashMap<>();
				for (int i = 0; i < ROLES.size(); i++) {
					allowed.put(ROLES.get(i), "Y".equals(cells[i + 1].trim()));
				}
				rows.add(new Row(cells[0].trim(), allowed, json.readValue(cells[4], Map.class)));
			}
		}
		return rows;
	}

	static Stream<String> roles() {
		return ROLES.stream();
	}

	static Stream<Arguments> roleAndTool() throws IOException {
		return matrix().stream().flatMap(row -> ROLES.stream().map(role -> Arguments.of(role, row)));
	}

	@ParameterizedTest(name = "{0} thấy đúng danh sách Tool")
	@MethodSource("roles")
	void toolListMatchesMatrix(String role) throws IOException {
		List<String> expected = matrix().stream().filter(r -> r.allowed().get(role)).map(Row::tool).toList();

		assertThat(McpTestClient.toolNames(this.port, ROLE_KEYS.get(role)))
			.containsExactlyInAnyOrderElementsOf(expected);
	}

	@ParameterizedTest(name = "{0} gọi {1}")
	@MethodSource("roleAndTool")
	void toolCallMatchesMatrix(String role, Row row) {
		String apiKey = ROLE_KEYS.get(role);
		if (row.allowed().get(role)) {
			// Được phép: Gateway cho qua. Kết quả có thể là lỗi nghiệp vụ (404 vì id không tồn tại),
			// nhưng không bao giờ là 401/403.
			McpSchema.CallToolResult result = McpTestClient.call(this.port, apiKey, row.tool(), row.arguments());
			assertThat(McpTestClient.text(result)).doesNotStartWith("HTTP 401").doesNotStartWith("HTTP 403");
		}
		else {
			assertThatThrownBy(() -> McpTestClient.call(this.port, apiKey, row.tool(), row.arguments()))
				.hasMessageContaining("Unknown tool");
		}
	}

}

package com.thesis.todo;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP client thật dùng trong test: kết nối tới ứng dụng qua HTTP, có thể kèm API key.
 */
final class McpTestClient {

	private McpTestClient() {
	}

	static McpSyncClient connect(int port, String apiKey) {
		HttpRequest.Builder request = HttpRequest.newBuilder();
		if (apiKey != null) {
			request.header("X-API-Key", apiKey);
		}
		McpSyncClient client = McpClient
			.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
				.endpoint("/mcp")
				.requestBuilder(request)
				.build())
			.build();
		client.initialize();
		return client;
	}

	static List<String> toolNames(int port, String apiKey) {
		try (McpSyncClient client = connect(port, apiKey)) {
			return client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
		}
	}

	static McpSchema.CallToolResult call(int port, String apiKey, String tool, Map<String, Object> args) {
		try (McpSyncClient client = connect(port, apiKey)) {
			return client.callTool(new McpSchema.CallToolRequest(tool, args));
		}
	}

	static String text(McpSchema.CallToolResult result) {
		return ((McpSchema.TextContent) result.content().get(0)).text();
	}

}

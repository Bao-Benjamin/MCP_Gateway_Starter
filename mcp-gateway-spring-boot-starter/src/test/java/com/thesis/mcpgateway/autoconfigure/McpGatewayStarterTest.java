package com.thesis.mcpgateway.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

import com.thesis.mcpgateway.GatewayTestApplication;
import com.thesis.mcpgateway.registry.GatewayTool;
import com.thesis.mcpgateway.registry.ToolRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Kiểm tra Starter với các cấu hình khác nhau, trên một ứng dụng không có Spring Security.
 */
class McpGatewayStarterTest {

	static McpSyncClient connect(int port) {
		McpSyncClient client = McpClient
			.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).endpoint("/mcp").build())
			.build();
		client.initialize();
		return client;
	}

	@Nested
	@SpringBootTest(classes = GatewayTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			properties = { "spring.ai.mcp.server.protocol=STATELESS", "mcp.gateway.read-timeout=500ms" })
	class DefaultConfiguration {

		@LocalServerPort
		int port;

		@Test
		void publishesOnlyGetAndPostByDefaultAndDoesNotFilterWithoutSpringSecurity() {
			try (McpSyncClient client = connect(this.port)) {
				List<String> names = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
				// DELETE /api/items/{id} không được công bố vì mặc định chỉ GET, POST
				assertThat(names).hasSize(2).noneMatch(n -> n.contains("delete"));
			}
		}

		@Test
		void slowEndpointTimesOutAsToolError() {
			try (McpSyncClient client = connect(this.port)) {
				String slowTool = client.listTools()
					.tools()
					.stream()
					.map(McpSchema.Tool::name)
					.filter(n -> n.contains("slow"))
					.findFirst()
					.orElseThrow();
				McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(slowTool, Map.of()));

				assertThat(result.isError()).isTrue();
				assertThat(((McpSchema.TextContent) result.content().get(0)).text())
					.startsWith("Could not call REST API");
			}
		}

	}

	@Nested
	@SpringBootTest(classes = GatewayTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			properties = { "spring.ai.mcp.server.protocol=STATELESS",
					"mcp.gateway.include-methods=GET,POST,DELETE" })
	class DeleteEnabled {

		@Autowired
		ToolRegistry registry;

		@Test
		void deleteEndpointIsPublishedAndMarkedDestructive() {
			GatewayTool delete = this.registry.all()
				.stream()
				.filter(t -> "DELETE".equals(t.endpoint().httpMethod()))
				.findFirst()
				.orElseThrow();
			assertThat(delete.tool().annotations().destructiveHint()).isTrue();
		}

	}

	@Nested
	@SpringBootTest(classes = GatewayTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			properties = { "spring.ai.mcp.server.protocol=STATELESS", "mcp.gateway.enabled=false" })
	class Disabled {

		@Autowired
		ApplicationContext context;

		@Test
		void noGatewayBeans() {
			assertThat(this.context.getBeansOfType(ToolRegistry.class)).isEmpty();
		}

	}

	@Nested
	@ExtendWith(OutputCaptureExtension.class)
	@SpringBootTest(classes = GatewayTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			properties = "spring.ai.mcp.server.protocol=STREAMABLE")
	class NotStateless {

		@Autowired
		ApplicationContext context;

		@Test
		void gatewayIsOffAndWarns(CapturedOutput output) {
			assertThat(this.context.getBeansOfType(ToolRegistry.class)).isEmpty();
			assertThat(output).contains(McpGatewayProtocolCheckAutoConfiguration.WARNING);
		}

	}

	@Nested
	@ExtendWith(OutputCaptureExtension.class)
	@SpringBootTest(classes = GatewayTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			properties = { "spring.ai.mcp.server.protocol=STATELESS", "mcp.gateway.openapi-path=/does-not-exist",
					"mcp.gateway.openapi-max-attempts=2", "mcp.gateway.openapi-retry-delay=10ms" })
	class OpenApiUnavailable {

		@Autowired
		ToolRegistry registry;

		@Test
		void applicationStillStartsWithoutTools(CapturedOutput output) {
			assertThat(this.registry.all()).isEmpty();
			assertThat(output).contains("attempt 2/2").contains("Giving up reading OpenAPI document");
		}

	}

}

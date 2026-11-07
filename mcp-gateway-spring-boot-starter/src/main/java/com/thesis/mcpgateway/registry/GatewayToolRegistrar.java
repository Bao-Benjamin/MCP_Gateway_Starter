package com.thesis.mcpgateway.registry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.thesis.mcpgateway.config.McpGatewayProperties;
import com.thesis.mcpgateway.invocation.ToolInvoker;
import com.thesis.mcpgateway.openapi.ApiEndpointMetadata;
import com.thesis.mcpgateway.openapi.OpenApiEndpointReader;
import com.thesis.mcpgateway.openapi.ToolGenerator;
import com.thesis.mcpgateway.transport.CallerContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;

/**
 * Khi ứng dụng đã khởi động xong (server đang lắng nghe), đọc OpenAPI, sinh Tool và đăng ký vào
 * MCP server.
 * <p>
 * Phải đợi {@link ApplicationReadyEvent} vì springdoc chỉ dựng tài liệu OpenAPI khi ứng dụng đã
 * chạy.
 */
public class GatewayToolRegistrar implements ApplicationListener<ApplicationReadyEvent> {

	private static final Logger logger = LoggerFactory.getLogger(GatewayToolRegistrar.class);

	private final McpGatewayProperties properties;

	private final RestClient restClient;

	private final Supplier<String> baseUrl;

	private final OpenApiEndpointReader reader;

	private final ToolGenerator generator;

	private final ToolRegistry registry;

	private final ToolInvoker invoker;

	private final McpStatelessSyncServer mcpServer;

	public GatewayToolRegistrar(McpGatewayProperties properties, RestClient restClient, Supplier<String> baseUrl,
			OpenApiEndpointReader reader, ToolGenerator generator, ToolRegistry registry, ToolInvoker invoker,
			McpStatelessSyncServer mcpServer) {
		this.properties = properties;
		this.restClient = restClient;
		this.baseUrl = baseUrl;
		this.reader = reader;
		this.generator = generator;
		this.registry = registry;
		this.invoker = invoker;
		this.mcpServer = mcpServer;
	}

	/**
	 * Lỗi ở đây (không đọc được OpenAPI, tài liệu sai định dạng) chỉ được ghi log: ứng dụng vẫn
	 * chạy bình thường, chỉ là không có Tool nào được công bố.
	 */
	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		Map<String, Object> openApi = loadOpenApi();
		if (openApi == null) {
			return;
		}
		try {
			registerTools(openApi);
		}
		catch (RuntimeException ex) {
			logger.error("Could not generate MCP tools from OpenAPI document; no tools were published", ex);
		}
	}

	private Map<String, Object> loadOpenApi() {
		String url = this.baseUrl.get() + this.properties.getOpenapiPath();
		int maxAttempts = Math.max(1, this.properties.getOpenapiMaxAttempts());
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				Map<String, Object> openApi = this.restClient.get()
					.uri(url)
					.retrieve()
					.body(new ParameterizedTypeReference<Map<String, Object>>() {
					});
				if (openApi != null) {
					return openApi;
				}
				logger.warn("OpenAPI document at {} is empty (attempt {}/{})", url, attempt, maxAttempts);
			}
			catch (RestClientException ex) {
				logger.warn("Could not read OpenAPI document at {} (attempt {}/{}): {}", url, attempt, maxAttempts,
						ex.getMessage());
			}
			if (attempt < maxAttempts && !sleep(this.properties.getOpenapiRetryDelay())) {
				break;
			}
		}
		logger.error("Giving up reading OpenAPI document at {}; no MCP tools were published. "
				+ "Check that springdoc is on the classpath and that {} is not blocked by Spring Security.", url,
				this.properties.getOpenapiPath());
		return null;
	}

	private void registerTools(Map<String, Object> openApi) {
		List<ApiEndpointMetadata> endpoints = this.reader.read(openApi);
		List<GatewayTool> tools = this.generator.generate(endpoints);
		for (GatewayTool tool : tools) {
			this.registry.register(tool);
			this.mcpServer.addTool(McpStatelessServerFeatures.SyncToolSpecification.builder()
				.tool(tool.tool())
				.callHandler((ctx, request) -> this.invoker.invoke(tool, request.arguments(), CallerContext.from(ctx)))
				.build());
			logger.info("Registered MCP tool '{}' -> {} {}", tool.name(), tool.endpoint().httpMethod(),
					tool.endpoint().pathTemplate());
			if ("DELETE".equals(tool.endpoint().httpMethod())) {
				logger.warn("MCP tool '{}' can delete data ({} {})", tool.name(), tool.endpoint().httpMethod(),
						tool.endpoint().pathTemplate());
			}
		}
		logger.info("Published {} MCP tools from OpenAPI document", tools.size());
	}

	private static boolean sleep(Duration delay) {
		try {
			Thread.sleep(delay.toMillis());
			return true;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

}

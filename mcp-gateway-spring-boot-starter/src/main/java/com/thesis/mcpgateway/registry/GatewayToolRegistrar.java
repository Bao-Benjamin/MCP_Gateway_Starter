package com.thesis.mcpgateway.registry;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

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

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		Map<String, Object> openApi = this.restClient.get()
			.uri(this.baseUrl.get() + this.properties.getOpenapiPath())
			.retrieve()
			.body(new ParameterizedTypeReference<Map<String, Object>>() {
			});
		if (openApi == null) {
			logger.warn("Could not read OpenAPI document at {}", this.properties.getOpenapiPath());
			return;
		}

		List<ApiEndpointMetadata> endpoints = this.reader.read(openApi);
		for (GatewayTool tool : this.generator.generate(endpoints)) {
			this.registry.register(tool);
			this.mcpServer.addTool(McpStatelessServerFeatures.SyncToolSpecification.builder()
				.tool(tool.tool())
				.callHandler((ctx, request) -> this.invoker.invoke(tool, request.arguments(), CallerContext.from(ctx)))
				.build());
			logger.info("Registered MCP tool '{}' -> {} {}", tool.name(), tool.endpoint().httpMethod(),
					tool.endpoint().pathTemplate());
		}
	}

}

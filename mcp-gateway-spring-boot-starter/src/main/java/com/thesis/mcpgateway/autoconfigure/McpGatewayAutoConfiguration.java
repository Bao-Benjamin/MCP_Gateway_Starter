package com.thesis.mcpgateway.autoconfigure;

import java.util.function.Supplier;

import org.springframework.ai.mcp.server.webmvc.autoconfigure.McpServerStatelessWebMvcAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator;
import org.springframework.web.client.RestClient;

import com.thesis.mcpgateway.config.McpGatewayProperties;
import com.thesis.mcpgateway.invocation.HttpLoopbackToolInvoker;
import com.thesis.mcpgateway.invocation.ToolInvoker;
import com.thesis.mcpgateway.openapi.OpenApiEndpointReader;
import com.thesis.mcpgateway.openapi.ToolGenerator;
import com.thesis.mcpgateway.registry.GatewayToolRegistrar;
import com.thesis.mcpgateway.registry.ToolRegistry;
import com.thesis.mcpgateway.security.PermitAllToolAccessControl;
import com.thesis.mcpgateway.security.SpringSecurityToolAccessControl;
import com.thesis.mcpgateway.security.ToolAccessControl;
import com.thesis.mcpgateway.transport.AccessControlledServerTransport;
import com.thesis.mcpgateway.transport.CallerContextExtractor;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tự động cấu hình MCP Gateway.
 * <p>
 * Yêu cầu MCP server của Spring AI chạy ở chế độ STATELESS
 * ({@code spring.ai.mcp.server.protocol=STATELESS}): mỗi request MCP là một HTTP request độc lập,
 * mang theo thông tin xác thực của người gọi.
 */
@AutoConfiguration(before = McpServerStatelessWebMvcAutoConfiguration.class)
@ConditionalOnProperty(prefix = "mcp.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "protocol", havingValue = "STATELESS")
@EnableConfigurationProperties({ McpGatewayProperties.class, McpServerStreamableHttpProperties.class })
public class McpGatewayAutoConfiguration {

	/**
	 * Transport HTTP của Spring AI, thêm bước đọc danh tính người gọi vào context của mỗi request.
	 * Thay cho bean mặc định của Spring AI (bean đó có {@code @ConditionalOnMissingBean}).
	 */
	@Bean
	@ConditionalOnMissingBean
	public WebMvcStatelessServerTransport webMvcStatelessServerTransport(
			@Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper, McpServerStreamableHttpProperties serverProperties,
			McpGatewayProperties gatewayProperties) {
		return WebMvcStatelessServerTransport.builder()
			.jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
			.messageEndpoint(serverProperties.getMcpEndpoint())
			.contextExtractor(new CallerContextExtractor(gatewayProperties.getForwardHeaders()))
			.build();
	}

	/**
	 * Transport mà MCP server thực sự dùng: bọc transport ở trên để lọc tools/list và kiểm tra quyền
	 * khi tools/call.
	 */
	@Bean
	@Primary
	public McpStatelessServerTransport accessControlledServerTransport(WebMvcStatelessServerTransport transport,
			ToolRegistry toolRegistry, ToolAccessControl toolAccessControl) {
		return new AccessControlledServerTransport(transport, toolRegistry, toolAccessControl);
	}

	@Bean
	@ConditionalOnMissingBean
	public ToolRegistry mcpGatewayToolRegistry() {
		return new ToolRegistry();
	}

	@Bean
	@ConditionalOnMissingBean
	public OpenApiEndpointReader mcpGatewayOpenApiEndpointReader(McpGatewayProperties properties) {
		return new OpenApiEndpointReader(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public ToolGenerator mcpGatewayToolGenerator() {
		return new ToolGenerator();
	}

	@Bean
	@ConditionalOnMissingBean
	public ToolInvoker mcpGatewayToolInvoker(ObjectProvider<RestClient.Builder> restClientBuilder,
			Environment environment, McpGatewayProperties properties) {
		return new HttpLoopbackToolInvoker(restClient(restClientBuilder), baseUrl(environment, properties));
	}

	@Bean
	public GatewayToolRegistrar mcpGatewayToolRegistrar(McpGatewayProperties properties,
			ObjectProvider<RestClient.Builder> restClientBuilder, Environment environment, OpenApiEndpointReader reader,
			ToolGenerator generator, ToolRegistry registry, ToolInvoker invoker, McpStatelessSyncServer mcpServer) {
		return new GatewayToolRegistrar(properties, restClient(restClientBuilder), baseUrl(environment, properties),
				reader, generator, registry, invoker, mcpServer);
	}

	/** Dùng RestClient.Builder của ứng dụng nếu có (đã cấu hình sẵn converter), nếu không thì tự tạo. */
	static RestClient restClient(ObjectProvider<RestClient.Builder> builder) {
		return builder.getIfAvailable(RestClient::builder).build();
	}

	/** Địa chỉ gốc để gọi lại chính ứng dụng; port chỉ biết được sau khi server đã chạy. */
	static Supplier<String> baseUrl(Environment environment, McpGatewayProperties properties) {
		return () -> {
			if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
				return properties.getBaseUrl();
			}
			String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
			return "http://localhost:" + port + environment.getProperty("server.servlet.context-path", "");
		};
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(WebInvocationPrivilegeEvaluator.class)
	static class SpringSecurityAccessControlConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public ToolAccessControl springSecurityToolAccessControl(
				ObjectProvider<WebInvocationPrivilegeEvaluator> privilegeEvaluator,
				Environment environment) {
			return new SpringSecurityToolAccessControl(privilegeEvaluator,
					environment.getProperty("server.servlet.context-path", ""));
		}

	}

	@Bean
	@ConditionalOnMissingBean
	public ToolAccessControl permitAllToolAccessControl() {
		return new PermitAllToolAccessControl();
	}

}

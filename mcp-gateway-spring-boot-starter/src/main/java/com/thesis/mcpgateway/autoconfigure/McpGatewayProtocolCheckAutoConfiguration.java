package com.thesis.mcpgateway.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Starter được bật nhưng MCP server không chạy ở chế độ STATELESS: Gateway không hoạt động. Ghi
 * cảnh báo rõ ràng thay vì im lặng không công bố Tool nào.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "mcp.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
@Conditional(McpGatewayProtocolCheckAutoConfiguration.NotStatelessCondition.class)
public class McpGatewayProtocolCheckAutoConfiguration {

	static final String WARNING = "MCP Gateway is disabled because spring.ai.mcp.server.protocol is not STATELESS. "
			+ "Set spring.ai.mcp.server.protocol=STATELESS to publish REST endpoints as MCP tools.";

	private static final Logger logger = LoggerFactory.getLogger(McpGatewayProtocolCheckAutoConfiguration.class);

	@Bean
	public SmartInitializingSingleton mcpGatewayProtocolWarning() {
		return () -> logger.warn(WARNING);
	}

	static class NotStatelessCondition extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			String protocol = context.getEnvironment().getProperty("spring.ai.mcp.server.protocol");
			if ("STATELESS".equalsIgnoreCase(protocol)) {
				return ConditionOutcome.noMatch("MCP server protocol is STATELESS");
			}
			return ConditionOutcome.match("MCP server protocol is " + protocol);
		}

	}

}

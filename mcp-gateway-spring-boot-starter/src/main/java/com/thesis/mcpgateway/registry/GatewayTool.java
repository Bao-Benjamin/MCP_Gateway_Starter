package com.thesis.mcpgateway.registry;

import com.thesis.mcpgateway.openapi.ApiEndpointMetadata;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Một MCP Tool do Gateway sinh ra, kèm endpoint REST mà nó đại diện.
 */
public record GatewayTool(McpSchema.Tool tool, ApiEndpointMetadata endpoint) {

	public String name() {
		return this.tool.name();
	}

}

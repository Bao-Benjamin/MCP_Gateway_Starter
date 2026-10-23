package com.thesis.mcpgateway.invocation;

import java.util.Map;

import com.thesis.mcpgateway.registry.GatewayTool;
import com.thesis.mcpgateway.transport.CallerContext;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Thực thi một Tool, tức là gọi endpoint REST tương ứng.
 */
public interface ToolInvoker {

	McpSchema.CallToolResult invoke(GatewayTool tool, Map<String, Object> arguments, CallerContext caller);

}

package com.thesis.mcpgateway.security;

import com.thesis.mcpgateway.registry.GatewayTool;
import com.thesis.mcpgateway.transport.CallerContext;

/**
 * Quyết định người gọi có được thấy và gọi một Tool hay không.
 */
public interface ToolAccessControl {

	boolean canInvoke(GatewayTool tool, CallerContext caller);

}

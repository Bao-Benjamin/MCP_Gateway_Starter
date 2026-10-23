package com.thesis.mcpgateway.security;

import com.thesis.mcpgateway.registry.GatewayTool;
import com.thesis.mcpgateway.transport.CallerContext;

/**
 * Dùng khi ứng dụng không có Spring Security: không lọc Tool nào.
 */
public class PermitAllToolAccessControl implements ToolAccessControl {

	@Override
	public boolean canInvoke(GatewayTool tool, CallerContext caller) {
		return true;
	}

}

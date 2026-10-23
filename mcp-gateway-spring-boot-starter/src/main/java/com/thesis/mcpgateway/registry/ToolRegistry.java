package com.thesis.mcpgateway.registry;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Danh sách Tool do Gateway sinh ra, tra cứu theo tên Tool.
 * <p>
 * Tool không có trong registry (ví dụ Tool viết tay bằng @McpTool) không do Gateway quản lý
 * quyền.
 */
public class ToolRegistry {

	private final Map<String, GatewayTool> tools = new ConcurrentHashMap<>();

	public void register(GatewayTool tool) {
		this.tools.put(tool.name(), tool);
	}

	public Optional<GatewayTool> find(String toolName) {
		return Optional.ofNullable(this.tools.get(toolName));
	}

	public Collection<GatewayTool> all() {
		return this.tools.values();
	}

}

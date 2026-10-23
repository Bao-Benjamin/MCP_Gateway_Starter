package com.thesis.mcpgateway.transport;

import java.security.Principal;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;

/**
 * Danh tính của người gọi MCP, lấy từ HTTP request ở tầng transport.
 * <p>
 * Danh tính đến từ cơ chế xác thực của ứng dụng (Spring Security), không phải từ dữ liệu MCP
 * client tự khai báo trong tham số Tool.
 *
 * @param authentication đối tượng Authentication của Spring Security (null nếu không có)
 * @param forwardedHeaders các header sẽ được chuyển tiếp khi gọi lại REST API
 */
public record CallerContext(Object authentication, Map<String, String> forwardedHeaders) {

	public static final String KEY = "mcp.gateway.caller";

	public static final CallerContext EMPTY = new CallerContext(null, Map.of());

	/** Tên người gọi, dùng cho log. */
	public String name() {
		return (this.authentication instanceof Principal principal) ? principal.getName() : "anonymous";
	}

	public static CallerContext from(McpTransportContext transportContext) {
		if (transportContext == null) {
			return EMPTY;
		}
		Object value = transportContext.get(KEY);
		return (value instanceof CallerContext caller) ? caller : EMPTY;
	}

}

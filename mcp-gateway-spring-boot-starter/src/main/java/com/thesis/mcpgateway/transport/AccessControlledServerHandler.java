package com.thesis.mcpgateway.transport;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thesis.mcpgateway.registry.ToolRegistry;
import com.thesis.mcpgateway.security.ToolAccessControl;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

/**
 * Kiểm soát quyền ở hai chỗ:
 * <ol>
 * <li>{@code tools/list}: chỉ trả về Tool mà người gọi được phép dùng.</li>
 * <li>{@code tools/call}: kiểm tra quyền lại trước khi thực thi. Ẩn Tool không thay thế được
 * kiểm tra quyền, vì client có thể tự gửi tên Tool.</li>
 * </ol>
 * Tool bị cấm trả về cùng lỗi với Tool không tồn tại, để không lộ thông tin Tool nào có trên
 * server.
 */
public class AccessControlledServerHandler implements McpStatelessServerHandler {

	private static final Logger logger = LoggerFactory.getLogger(AccessControlledServerHandler.class);

	private final McpStatelessServerHandler delegate;

	private final ToolRegistry registry;

	private final ToolAccessControl accessControl;

	public AccessControlledServerHandler(McpStatelessServerHandler delegate, ToolRegistry registry,
			ToolAccessControl accessControl) {
		this.delegate = delegate;
		this.registry = registry;
		this.accessControl = accessControl;
	}

	@Override
	public Mono<McpSchema.JSONRPCResponse> handleRequest(McpTransportContext transportContext,
			McpSchema.JSONRPCRequest request) {
		CallerContext caller = CallerContext.from(transportContext);

		if (McpSchema.METHOD_TOOLS_LIST.equals(request.method())) {
			return this.delegate.handleRequest(transportContext, request).map(response -> filterToolList(response, caller));
		}

		if (McpSchema.METHOD_TOOLS_CALL.equals(request.method())) {
			String toolName = toolName(request.params());
			if (toolName != null && !isAllowed(toolName, caller)) {
				logger.info("Denied call to tool '{}' for caller '{}'", toolName, caller.name());
				return Mono.just(McpSchema.JSONRPCResponse.error(request.id(),
						new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.INVALID_PARAMS,
								"Unknown tool: invalid_tool_name", "Tool not found: " + toolName)));
			}
		}

		return this.delegate.handleRequest(transportContext, request);
	}

	@Override
	public Mono<Void> handleNotification(McpTransportContext transportContext,
			McpSchema.JSONRPCNotification notification) {
		return this.delegate.handleNotification(transportContext, notification);
	}

	private McpSchema.JSONRPCResponse filterToolList(McpSchema.JSONRPCResponse response, CallerContext caller) {
		if (!(response.result() instanceof McpSchema.ListToolsResult result)) {
			return response;
		}
		List<McpSchema.Tool> visible = result.tools().stream().filter(t -> isAllowed(t.name(), caller)).toList();
		return McpSchema.JSONRPCResponse.result(response.id(),
				new McpSchema.ListToolsResult(visible, result.nextCursor(), result.meta()));
	}

	/** Tool không do Gateway sinh ra (ví dụ viết tay bằng @McpTool) thì giữ nguyên. */
	private boolean isAllowed(String toolName, CallerContext caller) {
		return this.registry.find(toolName).map(tool -> this.accessControl.canInvoke(tool, caller)).orElse(true);
	}

	private static String toolName(Object params) {
		if (params instanceof Map<?, ?> map && map.get("name") instanceof String name) {
			return name;
		}
		return null;
	}

}

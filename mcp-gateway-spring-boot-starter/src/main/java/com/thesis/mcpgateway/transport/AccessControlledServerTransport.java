package com.thesis.mcpgateway.transport;

import java.util.List;

import com.thesis.mcpgateway.registry.ToolRegistry;
import com.thesis.mcpgateway.security.ToolAccessControl;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import reactor.core.publisher.Mono;

/**
 * Bọc transport MCP thật để chen {@link AccessControlledServerHandler} vào giữa transport và MCP
 * server.
 * <p>
 * Lý do: MCP Java SDK trả về toàn bộ Tool cho mọi client trong {@code tools/list} và không có điểm
 * mở rộng để lọc theo người gọi. Transport là chỗ duy nhất nhận mọi request trước khi tới server.
 */
public class AccessControlledServerTransport implements McpStatelessServerTransport {

	private final McpStatelessServerTransport delegate;

	private final ToolRegistry registry;

	private final ToolAccessControl accessControl;

	public AccessControlledServerTransport(McpStatelessServerTransport delegate, ToolRegistry registry,
			ToolAccessControl accessControl) {
		this.delegate = delegate;
		this.registry = registry;
		this.accessControl = accessControl;
	}

	@Override
	public void setMcpHandler(McpStatelessServerHandler mcpHandler) {
		this.delegate.setMcpHandler(new AccessControlledServerHandler(mcpHandler, this.registry, this.accessControl));
	}

	@Override
	public Mono<Void> closeGracefully() {
		return this.delegate.closeGracefully();
	}

	@Override
	public List<String> protocolVersions() {
		return this.delegate.protocolVersions();
	}

}

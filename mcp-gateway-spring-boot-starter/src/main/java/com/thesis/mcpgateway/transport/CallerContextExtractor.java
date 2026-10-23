package com.thesis.mcpgateway.transport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.function.ServerRequest;

import com.thesis.mcpgateway.security.SecurityContextAccessor;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;

/**
 * Chạy trên thread xử lý HTTP request /mcp (đã đi qua filter chain của Spring Security), nên đọc
 * được SecurityContext. Kết quả được gắn vào {@link McpTransportContext} để dùng ở các bước sau,
 * kể cả khi các bước đó chạy trên thread khác.
 */
public class CallerContextExtractor implements McpTransportContextExtractor<ServerRequest> {

	private static final boolean SECURITY_PRESENT = ClassUtils.isPresent(
			"org.springframework.security.core.context.SecurityContextHolder",
			CallerContextExtractor.class.getClassLoader());

	private final List<String> forwardHeaders;

	public CallerContextExtractor(List<String> forwardHeaders) {
		this.forwardHeaders = forwardHeaders;
	}

	@Override
	public McpTransportContext extract(ServerRequest request) {
		Map<String, String> headers = new LinkedHashMap<>();
		for (String name : this.forwardHeaders) {
			String value = request.headers().firstHeader(name);
			if (value != null) {
				headers.put(name, value);
			}
		}
		Object authentication = SECURITY_PRESENT ? SecurityContextAccessor.currentAuthentication() : null;
		return McpTransportContext.create(Map.of(CallerContext.KEY, new CallerContext(authentication, headers)));
	}

}

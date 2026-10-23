package com.thesis.mcpgateway.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator;

import com.thesis.mcpgateway.registry.GatewayTool;
import com.thesis.mcpgateway.transport.CallerContext;

/**
 * Hỏi chính cấu hình phân quyền URL của ứng dụng ({@code SecurityFilterChain}) xem người gọi có
 * được truy cập endpoint của Tool hay không, mà không cần gọi endpoint thật.
 * <p>
 * Giới hạn: chỉ thấy quy tắc ở mức URL. Quy tắc ở mức method (ví dụ {@code @PreAuthorize}) vẫn
 * được áp dụng khi REST API thực sự chạy, nhưng không dùng để lọc danh sách Tool.
 */
public class SpringSecurityToolAccessControl implements ToolAccessControl {

	/** Giá trị thay cho biến path khi kiểm tra quyền, ví dụ /api/tasks/{id} thành /api/tasks/1. */
	private static final String PATH_VARIABLE_PLACEHOLDER = "1";

	private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken("mcp-gateway", "anonymousUser",
			AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

	private final ObjectProvider<WebInvocationPrivilegeEvaluator> privilegeEvaluator;

	private final String contextPath;

	/**
	 * Nhận ObjectProvider vì bean WebInvocationPrivilegeEvaluator do cấu hình Spring Security của
	 * ứng dụng tạo ra, có thể sau bean này.
	 */
	public SpringSecurityToolAccessControl(ObjectProvider<WebInvocationPrivilegeEvaluator> privilegeEvaluator,
			String contextPath) {
		this.privilegeEvaluator = privilegeEvaluator;
		this.contextPath = contextPath;
	}

	@Override
	public boolean canInvoke(GatewayTool tool, CallerContext caller) {
		Authentication authentication = (caller.authentication() instanceof Authentication auth) ? auth : ANONYMOUS;
		String uri = tool.endpoint().pathTemplate().replaceAll("\\{[^/}]+}", PATH_VARIABLE_PLACEHOLDER);
		WebInvocationPrivilegeEvaluator evaluator = this.privilegeEvaluator.getIfAvailable();
		if (evaluator == null) {
			// Ứng dụng có thư viện Spring Security nhưng không bật web security: không có quy tắc để lọc
			return true;
		}
		return evaluator.isAllowed(this.contextPath, uri, tool.endpoint().httpMethod(), authentication);
	}

}

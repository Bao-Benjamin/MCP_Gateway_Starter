package com.thesis.mcpgateway.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình của MCP Gateway, khai báo trong application.yml với tiền tố {@code mcp.gateway}.
 */
@ConfigurationProperties(prefix = "mcp.gateway")
public class McpGatewayProperties {

	/** Bật/tắt Starter. */
	private boolean enabled = true;

	/** Đường dẫn tài liệu OpenAPI dạng JSON (springdoc mặc định là /v3/api-docs). */
	private String openapiPath = "/v3/api-docs";

	/**
	 * Địa chỉ gốc để gọi lại REST API. Để trống thì dùng http://localhost:{port của ứng dụng}.
	 */
	private String baseUrl;

	/** Chỉ công bố endpoint có path khớp một trong các mẫu này (cú pháp Ant, ví dụ /api/**). */
	private List<String> includePaths = new ArrayList<>(List.of("/api/**"));

	/** Loại bỏ endpoint có path khớp các mẫu này, kể cả khi đã khớp includePaths. */
	private List<String> excludePaths = new ArrayList<>();

	/**
	 * Chỉ công bố các HTTP method này. Mặc định chỉ GET và POST; PUT, PATCH, DELETE phải bật rõ
	 * ràng vì chúng thay đổi hoặc xóa dữ liệu.
	 */
	private List<String> includeMethods = new ArrayList<>(List.of("GET", "POST"));

	/**
	 * Các header của request MCP được chuyển tiếp khi gọi lại REST API, để REST API nhận đúng
	 * danh tính người gọi.
	 */
	private List<String> forwardHeaders = new ArrayList<>(List.of("Authorization", "X-API-Key"));

	/** Thời gian chờ kết nối khi gọi lại REST API. */
	private Duration connectTimeout = Duration.ofSeconds(5);

	/** Thời gian chờ phản hồi khi gọi lại REST API. Quá thời gian này Tool trả lỗi. */
	private Duration readTimeout = Duration.ofSeconds(30);

	/** Số lần thử đọc tài liệu OpenAPI lúc khởi động. */
	private int openapiMaxAttempts = 3;

	/** Thời gian chờ giữa hai lần thử đọc OpenAPI. */
	private Duration openapiRetryDelay = Duration.ofSeconds(2);

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getOpenapiPath() {
		return this.openapiPath;
	}

	public void setOpenapiPath(String openapiPath) {
		this.openapiPath = openapiPath;
	}

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public List<String> getIncludePaths() {
		return this.includePaths;
	}

	public void setIncludePaths(List<String> includePaths) {
		this.includePaths = includePaths;
	}

	public List<String> getExcludePaths() {
		return this.excludePaths;
	}

	public void setExcludePaths(List<String> excludePaths) {
		this.excludePaths = excludePaths;
	}

	public List<String> getIncludeMethods() {
		return this.includeMethods;
	}

	public void setIncludeMethods(List<String> includeMethods) {
		this.includeMethods = includeMethods;
	}

	public Duration getConnectTimeout() {
		return this.connectTimeout;
	}

	public void setConnectTimeout(Duration connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public Duration getReadTimeout() {
		return this.readTimeout;
	}

	public void setReadTimeout(Duration readTimeout) {
		this.readTimeout = readTimeout;
	}

	public int getOpenapiMaxAttempts() {
		return this.openapiMaxAttempts;
	}

	public void setOpenapiMaxAttempts(int openapiMaxAttempts) {
		this.openapiMaxAttempts = openapiMaxAttempts;
	}

	public Duration getOpenapiRetryDelay() {
		return this.openapiRetryDelay;
	}

	public void setOpenapiRetryDelay(Duration openapiRetryDelay) {
		this.openapiRetryDelay = openapiRetryDelay;
	}

	public List<String> getForwardHeaders() {
		return this.forwardHeaders;
	}

	public void setForwardHeaders(List<String> forwardHeaders) {
		this.forwardHeaders = forwardHeaders;
	}

}

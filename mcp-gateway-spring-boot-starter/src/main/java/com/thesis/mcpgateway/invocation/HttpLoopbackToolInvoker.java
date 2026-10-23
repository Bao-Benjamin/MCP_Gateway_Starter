package com.thesis.mcpgateway.invocation;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.thesis.mcpgateway.openapi.ApiEndpointMetadata;
import com.thesis.mcpgateway.openapi.ToolGenerator;
import com.thesis.mcpgateway.registry.GatewayTool;
import com.thesis.mcpgateway.transport.CallerContext;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Gọi lại REST API của chính ứng dụng qua HTTP (loopback), kèm header xác thực của người gọi MCP.
 * <p>
 * Cách này chậm hơn gọi trực tiếp method của Controller, nhưng request đi qua đầy đủ filter chain
 * của Spring Security, binding tham số, validation và exception handler, nên không bỏ qua bước
 * bảo mật nào.
 */
public class HttpLoopbackToolInvoker implements ToolInvoker {

	private static final Logger logger = LoggerFactory.getLogger(HttpLoopbackToolInvoker.class);

	private final RestClient restClient;

	private final Supplier<String> baseUrl;

	public HttpLoopbackToolInvoker(RestClient restClient, Supplier<String> baseUrl) {
		this.restClient = restClient;
		this.baseUrl = baseUrl;
	}

	@Override
	public McpSchema.CallToolResult invoke(GatewayTool tool, Map<String, Object> arguments, CallerContext caller) {
		ApiEndpointMetadata endpoint = tool.endpoint();
		Map<String, Object> args = (arguments != null) ? arguments : Map.of();

		UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(this.baseUrl.get() + endpoint.pathTemplate());
		Map<String, Object> pathVariables = new HashMap<>();
		for (ApiEndpointMetadata.Parameter p : endpoint.parameters()) {
			Object value = args.get(p.name());
			if (value == null) {
				continue;
			}
			if ("path".equals(p.in())) {
				pathVariables.put(p.name(), value);
			}
			else {
				uri.queryParam(p.name(), value);
			}
		}
		URI target = uri.buildAndExpand(pathVariables).encode().toUri();

		RestClient.RequestBodySpec request = this.restClient.method(HttpMethod.valueOf(endpoint.httpMethod()))
			.uri(target)
			.accept(MediaType.APPLICATION_JSON)
			.headers(h -> caller.forwardedHeaders().forEach(h::set));
		Object body = args.get(ToolGenerator.BODY_PROPERTY);
		if (endpoint.requestBodySchema() != null && body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).body(body);
		}

		logger.debug("Calling {} {} for tool '{}'", endpoint.httpMethod(), target, tool.name());
		return request.exchange((req, res) -> {
			int status = res.getStatusCode().value();
			String text = StreamUtils.copyToString(res.getBody(), StandardCharsets.UTF_8);
			if (status >= 400) {
				// Lỗi HTTP trả về như kết quả Tool có isError=true để LLM đọc được lý do
				return McpSchema.CallToolResult.builder()
					.addTextContent("HTTP " + status + (text.isBlank() ? "" : ": " + text))
					.isError(true)
					.build();
			}
			return McpSchema.CallToolResult.builder().addTextContent(text.isBlank() ? "HTTP " + status : text).build();
		});
	}

}

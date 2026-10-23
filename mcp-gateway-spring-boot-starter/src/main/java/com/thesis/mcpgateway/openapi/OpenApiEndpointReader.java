package com.thesis.mcpgateway.openapi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.AntPathMatcher;

import com.thesis.mcpgateway.config.McpGatewayProperties;

/**
 * Đọc tài liệu OpenAPI (đã parse thành Map) và trả về danh sách endpoint thuộc phạm vi hỗ trợ.
 * <p>
 * Phạm vi MVP: tham số path/query, body application/json. Endpoint ngoài phạm vi bị bỏ qua và
 * ghi log lý do, không sinh schema sai.
 */
public class OpenApiEndpointReader {

	private static final Logger logger = LoggerFactory.getLogger(OpenApiEndpointReader.class);

	private static final List<String> HTTP_METHODS = List.of("get", "post", "put", "patch", "delete");

	/** Giới hạn độ sâu khi resolve $ref, tránh lặp vô hạn với schema đệ quy. */
	private static final int MAX_REF_DEPTH = 10;

	private final McpGatewayProperties properties;

	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	public OpenApiEndpointReader(McpGatewayProperties properties) {
		this.properties = properties;
	}

	@SuppressWarnings("unchecked")
	public List<ApiEndpointMetadata> read(Map<String, Object> openApi) {
		List<ApiEndpointMetadata> result = new ArrayList<>();
		Map<String, Object> paths = (Map<String, Object>) openApi.getOrDefault("paths", Map.of());

		for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
			String path = pathEntry.getKey();
			if (!isPathIncluded(path)) {
				continue;
			}
			Map<String, Object> pathItem = (Map<String, Object>) pathEntry.getValue();
			// Tham số khai báo chung cho cả path item
			List<Object> commonParams = (List<Object>) pathItem.getOrDefault("parameters", List.of());

			for (String method : HTTP_METHODS) {
				Object op = pathItem.get(method);
				if (op == null || !isMethodIncluded(method)) {
					continue;
				}
				try {
					result.add(toMetadata(openApi, method, path, (Map<String, Object>) op, commonParams));
				}
				catch (UnsupportedEndpointException ex) {
					logger.info("Skipping {} {}: {}", method.toUpperCase(Locale.ROOT), path, ex.getMessage());
				}
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private ApiEndpointMetadata toMetadata(Map<String, Object> openApi, String method, String path,
			Map<String, Object> operation, List<Object> commonParams) {

		List<ApiEndpointMetadata.Parameter> parameters = new ArrayList<>();
		List<Object> allParams = new ArrayList<>(commonParams);
		allParams.addAll((List<Object>) operation.getOrDefault("parameters", List.of()));

		for (Object rawParam : allParams) {
			Map<String, Object> param = (Map<String, Object>) resolve(openApi, rawParam, 0);
			String in = (String) param.get("in");
			String name = (String) param.get("name");
			if (!"path".equals(in) && !"query".equals(in)) {
				// header/cookie: không cho LLM điền, bỏ qua tham số này
				if (Boolean.TRUE.equals(param.get("required"))) {
					throw new UnsupportedEndpointException("required parameter '" + name + "' is in " + in);
				}
				continue;
			}
			Map<String, Object> schema = (Map<String, Object>) resolve(openApi,
					param.getOrDefault("schema", Map.of("type", "string")), 0);
			boolean required = "path".equals(in) || Boolean.TRUE.equals(param.get("required"));
			parameters.add(new ApiEndpointMetadata.Parameter(name, in, required, (String) param.get("description"),
					schema));
		}

		Map<String, Object> bodySchema = null;
		boolean bodyRequired = false;
		Object rawBody = operation.get("requestBody");
		if (rawBody != null) {
			Map<String, Object> body = (Map<String, Object>) resolve(openApi, rawBody, 0);
			Map<String, Object> content = (Map<String, Object>) body.getOrDefault("content", Map.of());
			Map<String, Object> json = (Map<String, Object>) content.get("application/json");
			if (json == null) {
				throw new UnsupportedEndpointException("request body is not application/json " + content.keySet());
			}
			bodySchema = (Map<String, Object>) resolve(openApi, json.getOrDefault("schema", Map.of()), 0);
			bodyRequired = Boolean.TRUE.equals(body.get("required"));
		}

		return new ApiEndpointMetadata(method.toUpperCase(Locale.ROOT), path, (String) operation.get("operationId"),
				(String) operation.get("summary"), (String) operation.get("description"), parameters, bodySchema,
				bodyRequired);
	}

	/**
	 * Thay mọi {@code {"$ref": "#/components/..."}} bằng nội dung thật (inline), vì MCP client
	 * nhận inputSchema độc lập, không có phần components.
	 */
	@SuppressWarnings("unchecked")
	Object resolve(Map<String, Object> openApi, Object node, int depth) {
		if (node instanceof Map<?, ?> map) {
			Object ref = map.get("$ref");
			if (ref instanceof String refString) {
				if (depth >= MAX_REF_DEPTH) {
					// Schema đệ quy quá sâu: dừng lại ở object tổng quát
					return new LinkedHashMap<>(Map.of("type", "object"));
				}
				return resolve(openApi, lookup(openApi, refString), depth + 1);
			}
			Map<String, Object> copy = new LinkedHashMap<>();
			for (Map.Entry<?, ?> e : map.entrySet()) {
				copy.put((String) e.getKey(), resolve(openApi, e.getValue(), depth));
			}
			return copy;
		}
		if (node instanceof List<?> list) {
			List<Object> copy = new ArrayList<>();
			for (Object item : list) {
				copy.add(resolve(openApi, item, depth));
			}
			return copy;
		}
		return node;
	}

	@SuppressWarnings("unchecked")
	private Object lookup(Map<String, Object> openApi, String ref) {
		if (!ref.startsWith("#/")) {
			throw new UnsupportedEndpointException("external $ref is not supported: " + ref);
		}
		Object current = openApi;
		for (String part : ref.substring(2).split("/")) {
			String key = part.replace("~1", "/").replace("~0", "~");
			if (!(current instanceof Map<?, ?> map) || !map.containsKey(key)) {
				throw new UnsupportedEndpointException("$ref not found: " + ref);
			}
			current = ((Map<String, Object>) map).get(key);
		}
		return current;
	}

	private boolean isPathIncluded(String path) {
		boolean included = this.properties.getIncludePaths().stream().anyMatch(p -> this.pathMatcher.match(p, path));
		boolean excluded = this.properties.getExcludePaths().stream().anyMatch(p -> this.pathMatcher.match(p, path));
		return included && !excluded;
	}

	private boolean isMethodIncluded(String method) {
		return this.properties.getIncludeMethods().stream().anyMatch(m -> m.equalsIgnoreCase(method));
	}

	/** Endpoint nằm ngoài phạm vi hỗ trợ của Gateway. */
	static class UnsupportedEndpointException extends RuntimeException {

		UnsupportedEndpointException(String message) {
			super(message);
		}

	}

}

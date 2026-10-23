package com.thesis.mcpgateway.openapi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.thesis.mcpgateway.registry.GatewayTool;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Chuyển {@link ApiEndpointMetadata} thành MCP Tool: tên, mô tả, inputSchema và annotations.
 * <p>
 * inputSchema là một object gộp tất cả tham số path/query; body JSON nằm trong thuộc tính
 * {@value #BODY_PROPERTY}.
 */
public class ToolGenerator {

	public static final String BODY_PROPERTY = "body";

	/** Nhiều MCP client giới hạn tên Tool 64 ký tự, chỉ gồm chữ, số, '_' và '-'. */
	private static final int MAX_NAME_LENGTH = 64;

	public List<GatewayTool> generate(List<ApiEndpointMetadata> endpoints) {
		Set<String> usedNames = new HashSet<>();
		List<GatewayTool> tools = new ArrayList<>();
		for (ApiEndpointMetadata endpoint : endpoints) {
			String name = uniqueName(toolName(endpoint), usedNames);
			McpSchema.Tool tool = McpSchema.Tool.builder(name, inputSchema(endpoint))
				.title(endpoint.summary())
				.description(description(endpoint))
				.annotations(annotations(endpoint))
				.build();
			tools.add(new GatewayTool(tool, endpoint));
		}
		return tools;
	}

	String toolName(ApiEndpointMetadata endpoint) {
		String raw = (endpoint.operationId() != null && !endpoint.operationId().isBlank()) ? endpoint.operationId()
				: endpoint.httpMethod().toLowerCase(Locale.ROOT) + "_" + endpoint.pathTemplate();
		String name = raw.replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
		return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
	}

	private String uniqueName(String base, Set<String> usedNames) {
		String name = base;
		int i = 2;
		while (!usedNames.add(name)) {
			String suffix = "_" + i++;
			name = base.substring(0, Math.min(base.length(), MAX_NAME_LENGTH - suffix.length())) + suffix;
		}
		return name;
	}

	private String description(ApiEndpointMetadata endpoint) {
		StringBuilder sb = new StringBuilder();
		if (endpoint.summary() != null) {
			sb.append(endpoint.summary());
		}
		if (endpoint.description() != null && !endpoint.description().equals(endpoint.summary())) {
			sb.append(sb.isEmpty() ? "" : "\n").append(endpoint.description());
		}
		// Ghi rõ endpoint gốc để người đọc (và LLM) biết Tool làm gì
		sb.append(sb.isEmpty() ? "" : "\n").append("(REST: ")
			.append(endpoint.httpMethod())
			.append(' ')
			.append(endpoint.pathTemplate())
			.append(')');
		return sb.toString();
	}

	Map<String, Object> inputSchema(ApiEndpointMetadata endpoint) {
		Map<String, Object> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();

		for (ApiEndpointMetadata.Parameter p : endpoint.parameters()) {
			Map<String, Object> schema = new LinkedHashMap<>(p.schema());
			if (p.description() != null) {
				schema.putIfAbsent("description", p.description());
			}
			properties.put(p.name(), schema);
			if (p.required()) {
				required.add(p.name());
			}
		}
		if (endpoint.requestBodySchema() != null) {
			properties.put(BODY_PROPERTY, endpoint.requestBodySchema());
			if (endpoint.requestBodyRequired()) {
				required.add(BODY_PROPERTY);
			}
		}

		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", properties);
		if (!required.isEmpty()) {
			schema.put("required", required);
		}
		return schema;
	}

	private McpSchema.ToolAnnotations annotations(ApiEndpointMetadata endpoint) {
		boolean readOnly = "GET".equals(endpoint.httpMethod());
		boolean destructive = "DELETE".equals(endpoint.httpMethod());
		boolean idempotent = readOnly || "PUT".equals(endpoint.httpMethod()) || destructive;
		return new McpSchema.ToolAnnotations(endpoint.summary(), readOnly, destructive, idempotent, false, null);
	}

}

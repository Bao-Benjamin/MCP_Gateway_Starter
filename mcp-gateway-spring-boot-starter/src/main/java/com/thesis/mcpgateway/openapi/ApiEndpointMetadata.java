package com.thesis.mcpgateway.openapi;

import java.util.List;
import java.util.Map;

/**
 * Thông tin của một endpoint REST, đọc từ OpenAPI.
 *
 * @param httpMethod GET, POST, ...
 * @param pathTemplate path có biến, ví dụ /api/tasks/{id}
 * @param operationId operationId trong OpenAPI (có thể null)
 * @param summary mô tả ngắn
 * @param description mô tả chi tiết
 * @param parameters tham số path/query
 * @param requestBodySchema JSON Schema của body (đã resolve $ref), null nếu không có body
 * @param requestBodyRequired body có bắt buộc không
 */
public record ApiEndpointMetadata(String httpMethod, String pathTemplate, String operationId, String summary,
		String description, List<Parameter> parameters, Map<String, Object> requestBodySchema,
		boolean requestBodyRequired) {

	/**
	 * Một tham số path hoặc query.
	 *
	 * @param name tên tham số
	 * @param in "path" hoặc "query"
	 * @param required bắt buộc hay không
	 * @param description mô tả
	 * @param schema JSON Schema của tham số
	 */
	public record Parameter(String name, String in, boolean required, String description,
			Map<String, Object> schema) {
	}

}

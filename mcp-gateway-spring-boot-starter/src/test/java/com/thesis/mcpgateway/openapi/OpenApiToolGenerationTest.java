package com.thesis.mcpgateway.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.thesis.mcpgateway.config.McpGatewayProperties;
import com.thesis.mcpgateway.registry.GatewayTool;
import tools.jackson.databind.json.JsonMapper;

class OpenApiToolGenerationTest {

	private static final String OPENAPI = """
			{
			  "openapi": "3.1.0",
			  "paths": {
			    "/api/tasks/{id}": {
			      "get": {
			        "operationId": "getTask",
			        "summary": "Lấy một task",
			        "parameters": [
			          {"name": "id", "in": "path", "required": true, "schema": {"type": "integer"}},
			          {"name": "verbose", "in": "query", "schema": {"type": "boolean"}}
			        ]
			      },
			      "delete": { "operationId": "deleteTask", "parameters": [] }
			    },
			    "/api/tasks": {
			      "post": {
			        "requestBody": {
			          "required": true,
			          "content": {"application/json": {"schema": {"$ref": "#/components/schemas/CreateTask"}}}
			        }
			      }
			    },
			    "/api/files": {
			      "post": {
			        "operationId": "upload",
			        "requestBody": {"content": {"multipart/form-data": {"schema": {"type": "object"}}}}
			      }
			    },
			    "/internal/health": { "get": { "operationId": "health" } }
			  },
			  "components": {
			    "schemas": {
			      "CreateTask": {
			        "type": "object",
			        "properties": {"title": {"type": "string"}, "tag": {"$ref": "#/components/schemas/Tag"}},
			        "required": ["title"]
			      },
			      "Tag": {"type": "object", "properties": {"name": {"type": "string"}}}
			    }
			  }
			}
			""";

	@SuppressWarnings("unchecked")
	private List<GatewayTool> generate() {
		Map<String, Object> openApi = JsonMapper.builder().build().readValue(OPENAPI, Map.class);
		List<ApiEndpointMetadata> endpoints = new OpenApiEndpointReader(new McpGatewayProperties()).read(openApi);
		return new ToolGenerator().generate(endpoints);
	}

	@Test
	void onlyIncludedPathsAndMethodsBecomeTools() {
		// /internal/** không khớp /api/**, DELETE không thuộc GET/POST, multipart bị bỏ qua
		assertThat(generate()).extracting(GatewayTool::name).containsExactlyInAnyOrder("getTask", "post_api_tasks");
	}

	@Test
	@SuppressWarnings("unchecked")
	void pathAndQueryParametersBecomeSchemaProperties() {
		GatewayTool tool = generate().stream().filter(t -> t.name().equals("getTask")).findFirst().orElseThrow();
		Map<String, Object> schema = tool.tool().inputSchema();
		Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

		assertThat(properties).containsKeys("id", "verbose");
		assertThat(schema.get("required")).isEqualTo(List.of("id"));
		assertThat(tool.tool().annotations().readOnlyHint()).isTrue();
	}

	@Test
	@SuppressWarnings("unchecked")
	void requestBodyRefsAreResolvedInline() {
		GatewayTool tool = generate().stream().filter(t -> t.name().equals("post_api_tasks")).findFirst().orElseThrow();
		Map<String, Object> properties = (Map<String, Object>) tool.tool().inputSchema().get("properties");
		Map<String, Object> body = (Map<String, Object>) properties.get("body");
		Map<String, Object> bodyProps = (Map<String, Object>) body.get("properties");
		Map<String, Object> tag = (Map<String, Object>) bodyProps.get("tag");

		assertThat(body).doesNotContainKey("$ref");
		assertThat(tag.get("properties")).isEqualTo(Map.of("name", Map.of("type", "string")));
		assertThat(tool.tool().inputSchema().get("required")).isEqualTo(List.of("body"));
	}

}

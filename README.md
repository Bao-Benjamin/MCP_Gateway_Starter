# MCP Gateway Spring Boot Starter (prototype tuần 1)

Starter tự động công bố REST API của ứng dụng Spring Boot thành **MCP Tools**, có kiểm soát quyền:

- `tools/list` chỉ trả về Tool mà người gọi được phép dùng.
- `tools/call` kiểm tra quyền lại trước khi chạy (client tự gửi tên Tool bị ẩn cũng bị chặn).
- Tool được thực thi bằng cách gọi lại REST API qua HTTP, kèm header xác thực của người gọi, nên mọi quy tắc bảo mật và validation của ứng dụng vẫn được áp dụng.

## Cấu trúc

```
mcp-gateway/
├── mcp-gateway-spring-boot-starter/   # Starter (phần chính của luận văn)
│   └── com.thesis.mcpgateway
│       ├── autoconfigure/   McpGatewayAutoConfiguration: tự cấu hình khi thêm dependency
│       ├── config/          McpGatewayProperties: cấu hình mcp.gateway.*
│       ├── openapi/         Đọc OpenAPI -> ApiEndpointMetadata -> MCP Tool
│       ├── registry/        ToolRegistry, GatewayToolRegistrar (đăng ký Tool khi app chạy xong)
│       ├── transport/       Lọc tools/list và chặn tools/call theo quyền
│       ├── security/        ToolAccessControl: hỏi Spring Security xem người gọi có quyền không
│       └── invocation/      HttpLoopbackToolInvoker: gọi lại REST API
└── todo-sample-app/                   # Ứng dụng to-do mẫu dùng Starter
```

## Yêu cầu

- JDK 21 (kiểm tra bằng `java -version`)
- Không cần cài Maven: dùng `mvnw` có sẵn trong thư mục.
- Để thử bằng giao diện: Node.js (để chạy MCP Inspector).

## Build và chạy test

Trên Windows (PowerShell hoặc CMD), trong thư mục `mcp-gateway`:

```
.\mvnw.cmd clean install
```

Lần đầu sẽ tải Maven và thư viện nên hơi lâu. Kết quả mong đợi: `BUILD SUCCESS`, 9 test chạy qua
(3 test sinh Tool từ OpenAPI, 6 test end-to-end với MCP client thật).

## Chạy ứng dụng mẫu

```
.\mvnw.cmd -pl todo-sample-app spring-boot:run
```

Log khởi động sẽ có các dòng (log viết bằng tiếng Anh để không lỗi font trên console Windows):

```
Registered MCP tool 'listMyTasks' -> GET /api/tasks
Registered MCP tool 'createTask' -> POST /api/tasks
Registered MCP tool 'getAppInfo' -> GET /api/public/info
Registered MCP tool 'getTaskStatistics' -> GET /api/admin/stats
```

MCP endpoint: `http://localhost:8080/mcp`

### API key dùng thử (khai báo trong `application.yml`)

| Header `X-API-Key` | Người dùng | Vai trò | Tool thấy được |
|---|---|---|---|
| (không gửi) | khách | – | getAppInfo |
| `alice-key` | alice | USER | getAppInfo, listMyTasks, createTask |
| `bob-key` | bob | USER | getAppInfo, listMyTasks, createTask |
| `admin-key` | admin | USER, ADMIN | cả 4 Tool |

## Thử bằng MCP Inspector

```
npx @modelcontextprotocol/inspector
```

Trong giao diện mở ra:

1. Transport Type: **Streamable HTTP**, URL: `http://localhost:8080/mcp`
2. Ở mục Authentication (hoặc Custom Headers, tùy phiên bản Inspector): thêm header `X-API-Key` với giá trị `alice-key`
3. Bấm **Connect**, sang tab **Tools**, bấm **List Tools**: thấy 3 Tool.
4. Đổi key thành `admin-key` rồi kết nối lại: thấy 4 Tool. Bỏ header: chỉ thấy 1 Tool.
5. Chọn `createTask`, nhập `{"title": "Viết chương 1"}` vào ô `body`, bấm **Run Tool**.

## Thử bằng curl

Trên Windows dùng `curl.exe` (không dùng `curl` của PowerShell). Liệt kê Tool của alice:

```
curl.exe -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" -H "X-API-Key: alice-key" -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"
```

Alice thử gọi Tool của ADMIN (bị chặn):

```
curl.exe -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" -H "X-API-Key: alice-key" -d "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"getTaskStatistics\",\"arguments\":{}}}"
```

## Dùng Starter trong ứng dụng khác

1. Thêm dependency `com.thesis:mcp-gateway-spring-boot-starter` và springdoc (`springdoc-openapi-starter-webmvc-api`).
2. Trong `application.yml`:

```yaml
spring.ai.mcp.server.protocol: STATELESS   # bắt buộc

mcp:
  gateway:
    include-paths: [/api/**]          # endpoint nào được công bố
    exclude-paths: []
    include-methods: [GET, POST]
    forward-headers: [Authorization, X-API-Key]   # header xác thực chuyển tiếp khi gọi lại API
    # base-url: http://localhost:8080  # mặc định tự lấy port của ứng dụng
```

3. Trong cấu hình Spring Security: cho phép truy cập `/mcp` và `/v3/api-docs/**`. Quyền của từng Tool lấy từ chính quy tắc của endpoint REST tương ứng.

## Giới hạn hiện tại (ghi vào báo cáo)

- Chỉ hỗ trợ tham số path/query và body `application/json`. Endpoint multipart, tham số header bắt buộc bị bỏ qua (có log lý do).
- Lọc Tool dựa trên quy tắc phân quyền **theo URL** trong `SecurityFilterChain`. Quy tắc ở mức method (`@PreAuthorize`) vẫn được áp dụng khi gọi, nhưng không dùng để ẩn Tool.
- Chỉ chạy ở chế độ MCP STATELESS trên Spring MVC.
- Danh sách Tool được sinh một lần khi ứng dụng khởi động.

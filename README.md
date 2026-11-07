# MCP Gateway Spring Boot Starter (giai đoạn 2)

Starter tự động công bố REST API của ứng dụng Spring Boot thành **MCP Tools**, có kiểm soát quyền:

- `tools/list` chỉ trả về Tool mà người gọi được phép dùng.
- `tools/call` kiểm tra quyền lại trước khi chạy (client tự gửi tên Tool bị ẩn cũng bị chặn).
- Tool được thực thi bằng cách gọi lại REST API qua HTTP, kèm header xác thực của người gọi, nên mọi quy tắc bảo mật và validation của ứng dụng vẫn được áp dụng.

## Cấu trúc

```
mcp-gateway/
├── mcp-gateway-spring-boot-starter/   # Starter (phần chính của luận văn)
│   └── com.thesis.mcpgateway
│       ├── autoconfigure/   Tự cấu hình khi thêm dependency; cảnh báo nếu thiếu STATELESS
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
- Không cần cài Maven: dùng `mvnw` có sẵn trong thư mục. Lần đầu, cần đặt file `maven-wrapper.properties` vào đúng chỗ
  (chỉ làm một lần, trong PowerShell tại thư mục `mcp-gateway`):

  ```
  mkdir .mvn\wrapper; move maven-wrapper.properties .mvn\wrapper\
  ```

  Nếu chạy bằng IntelliJ (Maven tích hợp sẵn) thì có thể bỏ qua bước này.
- Để thử bằng giao diện: Node.js (để chạy MCP Inspector).

## Build và chạy test

Trên Windows (PowerShell hoặc CMD), trong thư mục `mcp-gateway`:

```
.\mvnw.cmd clean install
```

Lần đầu sẽ tải Maven và thư viện nên hơi lâu. Kết quả mong đợi: `BUILD SUCCESS`, 64 test chạy qua:

| Test | Số test | Kiểm tra gì |
|---|---|---|
| `OpenApiToolGenerationTest` | 5 | Sinh Tool từ OpenAPI: lọc path/method, schema, `$ref`, tên trùng |
| `McpGatewayStarterTest` | 6 | Cấu hình Starter: mặc định, bật DELETE, tắt Starter, thiếu STATELESS, không đọc được OpenAPI, timeout |
| `AccessMatrixTest` | 48 | Ma trận 3 vai trò × 15 Tool trong `access-matrix.csv` (thấy và gọi) |
| `TaskFlowTest` | 5 | Luồng nghiệp vụ qua MCP: CRUD task, phân quyền theo dữ liệu, khóa tài khoản, dữ liệu sai |

## Chạy ứng dụng mẫu

```
.\mvnw.cmd -pl todo-sample-app spring-boot:run
```

Log khởi động sẽ có các dòng (log viết bằng tiếng Anh để không lỗi font trên console Windows):

```
Registered MCP tool 'listMyTasks' -> GET /api/tasks
Registered MCP tool 'createTask' -> POST /api/tasks
...
MCP tool 'deleteTask' can delete data (DELETE /api/tasks/{id})
Published 15 MCP tools from OpenAPI document
```

Dữ liệu được lưu trong bộ nhớ (RAM), tắt app là mất. App mẫu chỉ dùng để thử Gateway nên chưa cần database.

MCP endpoint: `http://localhost:8080/mcp`

### API key dùng thử (khai báo trong `application.yml`)

| Header `X-API-Key` | Người dùng | Vai trò | Số Tool thấy được |
|---|---|---|---|
| (không gửi) | khách | – | 1 |
| `alice-key` | alice | USER | 10 |
| `bob-key` | bob | USER | 10 |
| `admin-key` | admin | USER, ADMIN | 15 |

### 15 Tool của app to-do

| Nhóm | Tool | REST | Ai dùng được |
|---|---|---|---|
| Công khai | `getAppInfo` | GET /api/public/info | mọi người |
| Task | `listMyTasks` (lọc `done`, `tag`) | GET /api/tasks | USER |
| | `createTask` | POST /api/tasks | USER |
| | `getTask` | GET /api/tasks/{id} | USER |
| | `updateTask` | PUT /api/tasks/{id} | USER |
| | `completeTask` | PATCH /api/tasks/{id}/complete | USER |
| | `deleteTask` | DELETE /api/tasks/{id} | USER |
| Cá nhân | `getMyProfile` | GET /api/me | USER |
| | `getMyStatistics` | GET /api/me/stats | USER |
| | `listMyTags` | GET /api/me/tags | USER |
| Quản trị | `listUsers` | GET /api/admin/users | ADMIN |
| | `lockUser` | PATCH /api/admin/users/{username}/lock | ADMIN |
| | `unlockUser` | PATCH /api/admin/users/{username}/unlock | ADMIN |
| | `listAllTasks` (lọc `owner`) | GET /api/admin/tasks | ADMIN |
| | `getTaskStatistics` | GET /api/admin/stats | ADMIN |

Ngoài quyền theo vai trò, REST API còn có phân quyền theo dữ liệu: mỗi người chỉ xem và sửa được task của mình
(task của người khác trả 404). Người dùng bị khóa bị chặn ngay từ tầng xác thực (403), kể cả khi gọi `/mcp`.

## Thử bằng MCP Inspector

```
npx @modelcontextprotocol/inspector
```

Trong giao diện mở ra:

1. Transport Type: **Streamable HTTP**, URL: `http://localhost:8080/mcp`
2. Ở mục Authentication (hoặc Custom Headers, tùy phiên bản Inspector): thêm header `X-API-Key` với giá trị `alice-key`
3. Bấm **Connect**, sang tab **Tools**, bấm **List Tools**: thấy 10 Tool.
4. Đổi key thành `admin-key` rồi kết nối lại: thấy 15 Tool. Bỏ header: chỉ thấy 1 Tool.
5. Chọn `createTask`, nhập `{"title": "Viết chương 1", "tags": ["thesis"]}` vào ô `body`, bấm **Run Tool**.
   Bên dưới chữ "Success" là JSON của task vừa tạo. Gọi `listMyTasks` để thấy task đó.

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
    include-methods: [GET, POST]      # mặc định; thêm PUT, PATCH, DELETE nếu muốn công bố
    forward-headers: [Authorization, X-API-Key]   # header xác thực chuyển tiếp khi gọi lại API
    # base-url: http://localhost:8080  # mặc định tự lấy port của ứng dụng
    connect-timeout: 5s               # thời gian chờ kết nối khi gọi lại API
    read-timeout: 30s                 # quá thời gian này Tool trả lỗi
    openapi-max-attempts: 3           # số lần thử đọc OpenAPI lúc khởi động
    openapi-retry-delay: 2s
    # enabled: false                  # tắt Starter
```

Nếu thiếu `protocol: STATELESS`, Starter không hoạt động và log cảnh báo
`MCP Gateway is disabled because spring.ai.mcp.server.protocol is not STATELESS`.

3. Trong cấu hình Spring Security: cho phép truy cập `/mcp` và `/v3/api-docs/**`. Quyền của từng Tool lấy từ chính quy tắc của endpoint REST tương ứng.

## Giới hạn hiện tại (ghi vào báo cáo)

- Chỉ hỗ trợ tham số path/query và body `application/json`. Endpoint multipart, tham số header bắt buộc bị bỏ qua (có log lý do).
- Lọc Tool dựa trên quy tắc phân quyền **theo URL** trong `SecurityFilterChain`. Quy tắc ở mức method (`@PreAuthorize`) vẫn được áp dụng khi gọi, nhưng không dùng để ẩn Tool.
- Chỉ chạy ở chế độ MCP STATELESS trên Spring MVC.
- Danh sách Tool được sinh một lần khi ứng dụng khởi động. Nếu không đọc được OpenAPI, ứng dụng vẫn chạy nhưng không có Tool nào (log ghi rõ lý do).
- Lọc Tool thay biến path bằng giá trị mẫu (`/api/tasks/{id}` thành `/api/tasks/1`) khi hỏi Spring Security. Quy tắc phụ thuộc vào giá trị cụ thể của biến path sẽ không được phản ánh đúng khi lọc (vẫn được kiểm tra khi gọi).
- Tool chỉ trả về nội dung dạng text (JSON của REST API), chưa dùng `structuredContent`/`outputSchema`.

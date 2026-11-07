# Cấu trúc thư mục (package) của Starter

> Thư mục gốc: `mcp-gateway-spring-boot-starter/src/main/java/com/thesis/mcpgateway/`.
> Chỉ giải thích, không đổi code.

## 0. Vì sao chia nhiều thư mục?

Mỗi thư mục là **một việc** trong luồng của Gateway. Chia như vậy để:
- đọc code biết ngay class nằm ở bước nào;
- thay một phần mà không đụng phần khác (ví dụ đổi cách gọi REST thì chỉ sửa `invocation/`);
- viết luận văn dễ: mỗi thư mục ứng với một khối trong sơ đồ kiến trúc.

Gateway có 2 luồng chính:

```
LUỒNG A – lúc app khởi động (chạy 1 lần):
  autoconfigure ─► registry/GatewayToolRegistrar
                     ├─► openapi/OpenApiEndpointReader   (đọc /v3/api-docs)
                     ├─► openapi/ToolGenerator            (endpoint → Tool)
                     ├─► registry/ToolRegistry            (ghi sổ)
                     └─► MCP server của Spring AI         (addTool)

LUỒNG B – mỗi request /mcp:
  transport/CallerContextExtractor        (ai đang gọi?)
    └─► transport/AccessControlledServerHandler
          ├─► registry/ToolRegistry       (Tool này ứng với endpoint nào?)
          ├─► security/ToolAccessControl  (người này được phép không?)
          └─► MCP server ─► invocation/HttpLoopbackToolInvoker ─► REST API thật
```

## 1. Bảng tóm tắt

| Thư mục | Trả lời câu hỏi | Class |
|---|---|---|
| `autoconfigure/` | Lắp ráp mọi thứ thế nào? | `McpGatewayAutoConfiguration`, `McpGatewayProtocolCheckAutoConfiguration` |
| `config/` | Người dùng cấu hình được gì? | `McpGatewayProperties` |
| `openapi/` | Có những API nào, biến thành Tool ra sao? | `ApiEndpointMetadata`, `OpenApiEndpointReader`, `ToolGenerator` |
| `registry/` | Tool nào ứng với endpoint nào, đăng ký lúc nào? | `GatewayTool`, `ToolRegistry`, `GatewayToolRegistrar` |
| `transport/` | Ai đang gọi, chặn ở đâu? | `CallerContext`, `CallerContextExtractor`, `AccessControlledServerTransport`, `AccessControlledServerHandler` |
| `security/` | Người này có được dùng Tool này không? | `ToolAccessControl`, `SpringSecurityToolAccessControl`, `PermitAllToolAccessControl`, `SecurityContextAccessor` |
| `invocation/` | Gọi REST API thật thế nào? | `ToolInvoker`, `HttpLoopbackToolInvoker` |

Ngoài code Java còn có `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. File này là "danh bạ" để Spring Boot biết có 2 class auto-config ở trên.

## 2. Chi tiết từng thư mục

### `autoconfigure/`: lắp ráp

- **`McpGatewayAutoConfiguration`**: tạo tất cả bean của Gateway và nối chúng với nhau. Xem chi tiết trong `GIAI_THICH_AUTOCONFIG.md`.
- **`McpGatewayProtocolCheckAutoConfiguration`**: chỉ in **cảnh báo** khi Starter được bật nhưng `protocol` không phải `STATELESS`. Lúc đó Gateway tắt, và không có cảnh báo thì người dùng sẽ không hiểu vì sao không thấy Tool nào.

Đây là thư mục duy nhất "biết" tất cả các thư mục khác. Các thư mục còn lại không tự tạo nhau mà được lắp ở đây.

### `config/`: cấu hình

- **`McpGatewayProperties`** (`@ConfigurationProperties("mcp.gateway")`): đọc phần `mcp.gateway.*` trong `application.yml`.

| Thuộc tính | Mặc định | Dùng ở |
|---|---|---|
| `enabled` | `true` | autoconfigure (bật/tắt) |
| `openapi-path` | `/v3/api-docs` | registry (đọc OpenAPI ở đâu) |
| `base-url` | rỗng (tự tính) | invocation (gọi REST ở đâu) |
| `include-paths` / `exclude-paths` | `[/api/**]` / rỗng | openapi (lọc endpoint) |
| `include-methods` | `[GET, POST]` | openapi (lọc method) |
| `forward-headers` | `[Authorization, X-API-Key]` | transport + invocation |
| `connect-timeout` / `read-timeout` | 5s / 30s | invocation |
| `openapi-max-attempts` / `openapi-retry-delay` | 3 / 2s | registry |

### `openapi/`: từ tài liệu API đến Tool

- **`ApiEndpointMetadata`** (record): dữ liệu "sạch" của một endpoint: method, path (vd `/api/tasks/{id}`), `operationId`, mô tả, danh sách tham số (record con `Parameter`: tên, nằm ở `path` hay `query`, bắt buộc không, kiểu), và schema của body. Đây là cầu nối giữa OpenAPI (rất rườm rà) và phần còn lại của Gateway.
- **`OpenApiEndpointReader`**: nhận JSON OpenAPI (dạng `Map`), duyệt từng path và method, lọc theo `include-*`/`exclude-*`, **giải `$ref`** (OpenAPI hay viết `"$ref": "#/components/schemas/TaskRequest"`, nên phải thay bằng nội dung thật để LLM đọc được), rồi trả ra `List<ApiEndpointMetadata>`. Endpoint không hỗ trợ được (vd upload file) thì bỏ qua và ghi log.
- **`ToolGenerator`**: biến mỗi `ApiEndpointMetadata` thành một Tool MCP:
  - tên = `operationId` đã làm sạch (chỉ `A-Z a-z 0-9 _ -`, tối đa 64 ký tự; trùng tên thì thêm `_2`);
  - `inputSchema` = gộp tham số path, query và một thuộc tính `body`;
  - gợi ý: GET → `readOnlyHint`, DELETE → `destructiveHint`.

Thư mục này **chỉ xử lý dữ liệu**: không gọi mạng, không biết người dùng là ai. Vì vậy nó dễ test riêng (`OpenApiToolGenerationTest`).

### `registry/`: sổ đăng ký Tool

- **`GatewayTool`** (record): một cặp `(Tool MCP, ApiEndpointMetadata)`. Nhờ cặp này, chỉ cần tên Tool là biết nó gọi endpoint nào.
- **`ToolRegistry`**: "sổ" lưu các `GatewayTool` theo tên (`register`, `find`, `all`). `transport/` tra sổ để kiểm quyền, `invocation/` dùng để biết gọi URL nào.
- **`GatewayToolRegistrar`**: **người điều phối luồng A**. Lắng nghe `ApplicationReadyEvent` (app đã chạy xong, springdoc đã có `/v3/api-docs`), rồi:
  1. gọi HTTP lấy `/v3/api-docs` (lỗi thì thử lại theo `openapi-max-attempts`);
  2. `OpenApiEndpointReader` → danh sách endpoint;
  3. `ToolGenerator` → danh sách `GatewayTool`;
  4. ghi vào `ToolRegistry`;
  5. `mcpServer.addTool(...)`: đăng ký vào MCP server, kèm hàm xử lý gọi `ToolInvoker`;
  6. log `Published N MCP tools` (và cảnh báo riêng cho mỗi Tool DELETE).

### `transport/`: cửa chặn ở đầu `/mcp`

Đây là phần **đóng góp chính** của đề tài, vì MCP SDK không cho lọc `tools/list` theo người gọi.

- **`CallerContext`** (record): "thẻ người gọi", gồm `authentication` (ai, có role gì) và `forwardedHeaders` (vd `X-API-Key: alice-key`). `CallerContext.from(ctx)` lấy thẻ này ra từ context MCP.
- **`CallerContextExtractor`**: chạy khi request `/mcp` vừa tới, đọc `SecurityContext` và các header rồi tạo `CallerContext`.
- **`AccessControlledServerTransport`**: lớp bọc transport của Spring AI. Việc duy nhất là chen `AccessControlledServerHandler` vào giữa transport và MCP server.
- **`AccessControlledServerHandler`**: nơi thực sự lọc:
  - `tools/list` → bỏ Tool không được phép khỏi danh sách;
  - `tools/call` → Tool không được phép thì trả lỗi "Unknown tool";
  - Tool không phải do Gateway sinh (vd viết tay bằng `@McpTool`) thì giữ nguyên.

Thư mục này **không tự quyết** ai được gì. Nó hỏi `security/`.

### `security/`: quyết định quyền

- **`ToolAccessControl`** (interface): một câu hỏi duy nhất, `canInvoke(tool, caller)`. Tách thành interface để app có thể tự viết luật riêng (khai báo bean của mình, Starter sẽ nhường).
- **`SpringSecurityToolAccessControl`**: cài đặt mặc định khi app có Spring Security. Nó hỏi luật URL của app (`SecurityConfig`) qua `WebInvocationPrivilegeEvaluator`: "người này có được `POST /api/tasks` không?". Biến path `{id}` được thay bằng `1`.
- **`PermitAllToolAccessControl`**: dự phòng khi app không có Spring Security, cho phép hết.
- **`SecurityContextAccessor`**: hàm nhỏ đọc `SecurityContextHolder`. Tách riêng để class của Spring Security chỉ được nạp khi app có thư viện đó, tránh lỗi `ClassNotFoundException` ở app không dùng Spring Security.

### `invocation/`: gọi REST API thật

- **`ToolInvoker`** (interface): `invoke(tool, arguments, caller)` → `CallToolResult`. Là interface để sau này có thể đổi cách gọi (vd gọi thẳng method Java thay vì HTTP) mà không sửa chỗ khác.
- **`HttpLoopbackToolInvoker`**: cách hiện tại, gửi HTTP tới **chính app**:
  - ghép URL: thay `{id}` bằng giá trị thật, thêm query string;
  - gắn lại header của người gọi (`X-API-Key`), nên REST API kiểm tra quyền lần nữa;
  - gửi `body` dạng JSON;
  - kết quả: JSON trả về được đặt vào `content[0].text`. HTTP ≥ 400 thì `isError=true` kèm `"HTTP 403: ..."`. Lỗi mạng hoặc timeout thì `"Could not call REST API: ..."`.

## 3. Các thư mục phụ thuộc nhau thế nào

```
                 autoconfigure  (lắp ráp, biết tất cả)
                       │
     ┌────────┬────────┼──────────┬───────────┐
     ▼        ▼        ▼          ▼           ▼
  config   openapi   registry   transport   invocation
              ▲       │  ▲  ▲      │   │        │
              └───────┘  │  └──────┘   ▼        │
                         │          security    │
                         └──────────────────────┘
```

- `openapi/` không phụ thuộc thư mục nào khác của Starter (chỉ dùng `config/`), nên đứng độc lập.
- `registry/` dùng `openapi/` (sinh Tool) và `invocation/` (gắn hàm xử lý khi Tool được gọi).
- `transport/` dùng `registry/` (tra Tool) và `security/` (hỏi quyền).
- `security/` và `invocation/` chỉ cần `GatewayTool` (từ `registry/`) và `CallerContext` (từ `transport/`).

Quy tắc: thư mục "dưới" không biết thư mục "trên". Ví dụ `openapi/` không biết có MCP server hay người dùng nào. Nhờ vậy khi viết luận văn có thể trình bày từng khối riêng, rồi `autoconfigure/` ghép chúng lại.

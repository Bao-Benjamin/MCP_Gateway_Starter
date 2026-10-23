# Giải thích code prototype tuần 1

## 1. Ý tưởng chính

Ứng dụng chỉ cần thêm dependency Starter. Khi khởi động xong, Starter:

1. Đọc tài liệu OpenAPI của chính ứng dụng (`/v3/api-docs`, do springdoc sinh).
2. Biến mỗi endpoint thành một MCP Tool (tên, mô tả, JSON Schema của tham số).
3. Đăng ký các Tool vào MCP server của Spring AI tại `/mcp`.

Khi MCP client gửi request, Gateway kiểm tra quyền ở 3 lớp:

| Lớp | Ở đâu | Làm gì |
|---|---|---|
| 1. Lọc danh sách | `tools/list` | Chỉ trả về Tool mà người gọi được phép dùng |
| 2. Kiểm tra khi gọi | `tools/call` | Client tự gửi tên Tool bị ẩn vẫn bị chặn, trả lỗi như Tool không tồn tại |
| 3. REST API thật | Spring Security của ứng dụng | Gateway gọi lại API kèm API key của người gọi, nên mọi quy tắc của ứng dụng vẫn chạy |

## 2. Luồng một request `tools/call`

```
MCP client (header X-API-Key: alice-key)
   │  POST /mcp  {"method":"tools/call","params":{"name":"createTask",...}}
   ▼
Spring Security filter chain của ứng dụng   → xác định alice, role USER
   ▼
WebMvcStatelessServerTransport (Spring AI)
   │  CallerContextExtractor: lưu Authentication + header X-API-Key vào McpTransportContext
   ▼
AccessControlledServerHandler                → lớp 1 và 2: alice có quyền POST /api/tasks không?
   ▼
MCP server của Spring AI                     → tìm Tool, kiểm tra input theo JSON Schema
   ▼
HttpLoopbackToolInvoker                      → POST http://localhost:8080/api/tasks (kèm X-API-Key)
   ▼
TaskController (đi qua Spring Security lần nữa: lớp 3)
```

## 3. Vì sao lại làm như vậy (nội dung tốt cho báo cáo)

**Vì sao phải chen vào transport?** Trong MCP Java SDK 2.0, `tools/list` trả về toàn bộ Tool cho mọi client, và hàm xử lý là `private`, không có chỗ để lọc theo người gọi (xem `McpStatelessAsyncServer.toolsListRequestHandler`). Điểm duy nhất nhận mọi request trước server là transport. Vì vậy `AccessControlledServerTransport` bọc transport thật và chèn `AccessControlledServerHandler` vào giữa. Đây là kỹ thuật decorator, không sửa thư viện.

**Vì sao chế độ STATELESS?** Mỗi request MCP là một HTTP request riêng và luôn mang theo API key. Danh tính được kiểm tra lại ở mọi request, không phụ thuộc phiên (session) cũ.

**Vì sao biết quyền mà không cần gọi thử endpoint?** `SpringSecurityToolAccessControl` dùng `WebInvocationPrivilegeEvaluator` của Spring Security. Nó hỏi chính các quy tắc trong `SecurityConfig` ("alice có được GET /api/admin/stats không?"). Nhờ vậy quyền chỉ khai báo một lần, ở ứng dụng.

**Vì sao gọi lại qua HTTP thay vì gọi thẳng method Controller?** Request đi qua đủ filter bảo mật, binding, validation (`@Valid`), exception handler. Đổi lại sẽ chậm hơn một chút; phần benchmark sẽ đo mức chậm này.

## 4. Vai trò từng class trong Starter

| Class | Vai trò |
|---|---|
| `McpGatewayAutoConfiguration` | Tự tạo các bean khi có dependency và `protocol: STATELESS` |
| `McpGatewayProperties` | Cấu hình `mcp.gateway.*` (path, method nào được công bố, header chuyển tiếp) |
| `OpenApiEndpointReader` | Đọc OpenAPI, lọc theo path/method, resolve `$ref`, bỏ qua endpoint không hỗ trợ |
| `ToolGenerator` | Sinh tên Tool (từ `operationId`), mô tả, `inputSchema`, annotations (`readOnlyHint` cho GET) |
| `GatewayToolRegistrar` | Chờ ứng dụng chạy xong rồi đọc OpenAPI và đăng ký Tool |
| `ToolRegistry` | Lưu Tool do Gateway sinh, tra theo tên |
| `CallerContextExtractor`, `CallerContext` | Lấy danh tính người gọi từ HTTP request |
| `AccessControlledServerTransport`, `AccessControlledServerHandler` | Lọc `tools/list`, chặn `tools/call` |
| `SpringSecurityToolAccessControl` | Hỏi Spring Security xem người gọi có quyền vào endpoint không |
| `HttpLoopbackToolInvoker` | Gọi lại REST API, lỗi HTTP trả về dạng `isError=true` |

## 5. Test đã có

- `OpenApiToolGenerationTest` (3 test): lọc path/method, tham số path/query thành schema, resolve `$ref` lồng nhau, bỏ qua multipart.
- `McpGatewayAccessControlTest` (6 test, dùng MCP client thật):
  - khách chỉ thấy 1 Tool, alice thấy 3, admin thấy 4;
  - alice gọi thẳng Tool của admin bị chặn, khách gọi Tool của USER bị chặn;
  - bob tạo task, alice không thấy task của bob (phân quyền theo dữ liệu vẫn giữ);
  - dữ liệu sai (title rỗng) trả về lỗi HTTP 400 dạng kết quả Tool.

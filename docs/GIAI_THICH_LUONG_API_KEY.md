# Luồng đi của một request có API key (giải thích chi tiết)

> Tài liệu này bổ sung cho `GIAI_THICH_API_KEY.md`. Chỉ giải thích, không đổi code.

## 1. Ai tạo ra API key? Có quy luật gì không?

- **Ai tạo:** chính mình (Claude) tự viết tay 3 key cho bản demo. Không có chương trình sinh key nào cả.
- **Có quy luật không:** **không**. `alice-key`, `bob-key`, `admin-key` chỉ là chuỗi tùy ý. Đặt tên giống tên người cho dễ đọc thôi; server **không hề phân tích** chuỗi đó. Đổi `alice-key` thành `xyz123` thì vẫn chạy y hệt.
- **Làm sao biết key là của ai:** **tra bảng**. Server có một bảng `key → người dùng`, nhận key thì tìm trong bảng:

```yaml
# todo-sample-app/src/main/resources/application.yml
demo:
  security:
    keys:
      alice-key:            # <- chuỗi key (tùy ý)
        username: alice     # <- key này thuộc về ai
        roles: [USER]       # <- người đó có quyền gì
      bob-key:
        username: bob
        roles: [USER]
      admin-key:
        username: admin
        roles: [USER, ADMIN]
```

```java
// ApiKeyAuthenticationFilter.java (dòng 45)
ApiKeyProperties.User user = this.properties.keys().get(apiKey);   // tra bảng, giống Map.get()
```

Giống như thẻ gửi xe: số trên thẻ không chứa thông tin gì về chủ xe, nhân viên chỉ tra sổ xem số đó ứng với xe nào.

## 2. Có phải đang hardcode không?

**Đúng.** Key nằm dạng chữ thường (plaintext) trong `application.yml`. Khi ứng dụng khởi động, Spring đọc phần `demo.security.keys` vào record `ApiKeyProperties` (một `Map<String, User>`), rồi `UserAccountService` tạo danh sách user từ đó. Muốn thêm key thì phải sửa file yml rồi khởi động lại.

Cách này **chỉ dùng cho demo** (trong yml đã ghi chú "Không dùng cách này cho hệ thống thật"). Lý do chọn: đề tài là **Starter**, không phải hệ thống quản lý tài khoản. Starter không quan tâm key đến từ đâu, nó chỉ đọc `Authentication` mà Spring Security đã tạo sẵn.

### Hệ thống thật thường làm thế nào

| Bước | Demo hiện tại | Hệ thống thật |
|---|---|---|
| Tạo key | Viết tay trong yml | Admin bấm "tạo key" → server sinh chuỗi ngẫu nhiên (vd 32 byte random, kiểu `sk_live_9fA2...`) |
| Đưa key cho người dùng | Đọc trong file | Hiện **một lần duy nhất** lúc tạo |
| Lưu key | Plaintext trong yml | Lưu **hash** (SHA-256) trong database, không lưu key gốc |
| Kiểm tra key | `map.get(key)` | Hash key nhận được rồi tìm hash đó trong DB |
| Thu hồi / hết hạn | Không có | Có cột `revoked`, `expires_at` |

Hoặc bỏ hẳn API key, dùng JWT/Keycloak (đã so sánh trong `GIAI_THICH_API_KEY.md`). Với JWT thì token **có** chứa thông tin người dùng bên trong (có chữ ký), khác với API key.

## 3. Luồng chi tiết: alice gọi tool `createTask` qua MCP

Ví dụ: MCP Inspector (hoặc Claude Desktop) gọi tool `createTask` với key của alice.

```
Client MCP ──(1) POST /mcp + X-API-Key: alice-key──► Todo app (cổng 8080)
                                                    │
     (2) ApiKeyAuthenticationFilter: tra bảng → alice, ROLE_USER
     (3) SecurityConfig: /mcp là permitAll → cho qua
     (4) CallerContextExtractor: chép "alice" + header X-API-Key vào context MCP
     (5) AccessControlledServerHandler: alice có được POST /api/tasks không? → CÓ
     (6) MCP server kiểm tra arguments theo inputSchema
     (7) HttpLoopbackToolInvoker ──POST http://localhost:8080/api/tasks + X-API-Key: alice-key──┐
                                                                                             │
     (8) ApiKeyAuthenticationFilter LẦN 2: tra bảng → alice          ◄────────────────────────┘
     (9) SecurityConfig: /api/tasks/** cần ROLE_USER → alice có → cho qua
    (10) TaskController.createTask(Principal = alice) → TaskService lưu vào bộ nhớ
    (11) Trả JSON task mới → HttpLoopbackToolInvoker gói thành CallToolResult
    (12) Trả JSON-RPC response về client
```

### Từng bước cụ thể

**(1) Client gửi request.** Inspector gửi:

```http
POST /mcp HTTP/1.1
X-API-Key: alice-key
Content-Type: application/json

{"jsonrpc":"2.0","id":7,"method":"tools/call",
 "params":{"name":"createTask","arguments":{"body":{"title":"Viết chương 3"}}}}
```

Key do **người dùng tự nhập** vào Inspector (ô Headers). Server không cấp key qua mạng; bạn phải biết key từ trước.

**(2) Lọc API key (lần 1).** Mọi request vào app đều đi qua chuỗi filter của Spring Security. `ApiKeyAuthenticationFilter` đọc header `X-API-Key`:
- không có header → đi tiếp với tư cách khách (anonymous)
- có nhưng không tìm thấy trong bảng → trả 401 "Invalid API key", dừng luôn
- tìm thấy nhưng tài khoản bị khóa → 403 "Account is locked"
- tìm thấy → tạo `Authentication(alice, ROLE_USER)` và đặt vào `SecurityContextHolder`

**(3) Luật URL.** `SecurityConfig` cho phép `/mcp` với mọi người (permitAll), vì việc phân quyền từng tool sẽ làm ở bước 5.

**(4) Ghi nhớ người gọi.** Request đến `WebMvcStatelessServerTransport` (endpoint MCP). `CallerContextExtractor` (của Starter) chép 2 thứ vào `McpTransportContext`: đối tượng `Authentication` (alice) và các header cần chuyển tiếp (`X-API-Key: alice-key`). Lý do: các bước sau chạy trong code MCP SDK, không còn thấy request HTTP gốc.

**(5) Kiểm tra quyền gọi tool.** `AccessControlledServerHandler` biết tool `createTask` ứng với `POST /api/tasks`. Nó hỏi Spring Security: "với luật trong `SecurityConfig`, alice có được `POST /api/tasks` không?" (qua `WebInvocationPrivilegeEvaluator`). Có → đi tiếp. Nếu là khách thì trả lỗi `Unknown tool`, giống như tool không tồn tại.

**(6) Kiểm tra tham số.** MCP server so `arguments` với `inputSchema` của tool (sinh từ OpenAPI). Thiếu `title` chẳng hạn thì báo lỗi ngay.

**(7) Gọi REST API thật.** `HttpLoopbackToolInvoker` gửi một request HTTP **mới** tới chính app:

```http
POST http://localhost:8080/api/tasks
X-API-Key: alice-key          <- chép nguyên từ bước 4
Content-Type: application/json

{"title":"Viết chương 3"}
```

**(8)–(9) Lọc API key lần 2.** Vì đây là request HTTP mới, nó đi lại từ đầu chuỗi filter: tra bảng lần nữa ra alice, `SecurityConfig` thấy `/api/tasks/**` yêu cầu `ROLE_USER`, alice có nên cho qua. Đây là lớp bảo vệ thứ ba: kể cả gateway có lỗi thì REST API vẫn tự kiểm tra.

**(10) Controller xử lý.** `TaskController.createTask` nhận `Principal` = alice, gọi `TaskService` tạo task với `owner = alice` và lưu vào `ConcurrentHashMap` (trong bộ nhớ).

**(11)–(12) Trả kết quả.** REST trả `201` + JSON task. Invoker đặt JSON đó vào `CallToolResult.content[0].text` (`isError=false`; nếu HTTP ≥ 400 thì `isError=true`). MCP server bọc thành JSON-RPC response gửi về Inspector.

### Với `tools/list` thì sao?

Bước 1–4 giống hệt. Ở bước 5, thay vì kiểm tra 1 tool, handler lấy danh sách tất cả 15 tool và **bỏ đi** các tool mà alice không có quyền (hỏi Spring Security cho từng tool). Kết quả: khách thấy 1 tool, alice/bob thấy 10, admin thấy 15. Không có bước gọi REST API.

### Tóm tắt 3 lớp kiểm tra

| Lớp | Ở đâu | Làm gì |
|---|---|---|
| 1 | `tools/list` (Starter) | Ẩn tool không có quyền |
| 2 | `tools/call` (Starter) | Chặn gọi tool không có quyền, kể cả khi đoán được tên |
| 3 | REST API (app) | Filter API key + `SecurityConfig` chạy lại trên request loopback |

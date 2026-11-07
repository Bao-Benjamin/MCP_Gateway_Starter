# Giải thích code giai đoạn 2

*Viết ngày 24/10/2026. File này chỉ nói những gì thay đổi so với giai đoạn 1; kiến trúc chung xem [giai đoạn 1](GIAI_THICH_GIAI_DOAN_1.md).*

## Starter

| Thay đổi | Ở đâu | Vì sao |
|---|---|---|
| Hỗ trợ PUT, PATCH, DELETE | `McpGatewayProperties.includeMethods` | Đã đọc được mọi method từ giai đoạn 1; giai đoạn này kiểm chứng bằng test. Mặc định vẫn chỉ GET, POST vì các method kia sửa hoặc xóa dữ liệu, phải bật rõ ràng |
| Cảnh báo Tool xóa dữ liệu | `GatewayToolRegistrar` | Log WARN cho mỗi Tool DELETE để người cấu hình biết mình đang cho LLM quyền xóa |
| Thử lại khi đọc OpenAPI | `GatewayToolRegistrar.loadOpenApi` | Đọc lỗi thì thử lại (mặc định 3 lần). Hết lượt thì log lỗi kèm gợi ý cách sửa, ứng dụng vẫn chạy, chỉ là không có Tool |
| Timeout khi gọi lại API | `McpGatewayAutoConfiguration.restClient` | Một endpoint chậm không được giữ request MCP mãi. Mặc định chờ kết nối 5s, chờ phản hồi 30s |
| Lỗi mạng thành lỗi Tool | `HttpLoopbackToolInvoker.invoke` | Timeout, mất kết nối trả về `isError=true` với nội dung "Could not call REST API: ..." thay vì làm hỏng cả request MCP |
| Tên Tool trùng | `ToolGenerator.uniqueName` | Hai endpoint cùng `operationId` thì Tool sau thành `ten_2`, có log cảnh báo |
| Cảnh báo thiếu STATELESS | `McpGatewayProtocolCheckAutoConfiguration` | Trước đây Starter im lặng không làm gì; giờ log rõ lý do và cách sửa |

## App to-do

- 15 endpoint chia 4 nhóm: công khai, task, cá nhân (`/api/me`), quản trị (`/api/admin`). Danh sách đầy đủ trong README.
- Task có thêm hạn (`dueDate`) và nhãn (`tags`); thống kê đếm cả task quá hạn.
- `UserAccountService`: ADMIN khóa/mở khóa người dùng. Người bị khóa bị `ApiKeyAuthenticationFilter` chặn với 403 ngay từ đầu, nên cũng không vào được `/mcp`.
- Task của người khác trả về **404** chứ không phải 403, để không lộ việc task đó có tồn tại.
- `spring.web.error.include-message: always`: lỗi trả về có lý do (ví dụ "Task 5 not found"), LLM đọc được để tự sửa.

## Test (64 test)

- `access-matrix.csv` là bảng quyền mong đợi (15 Tool × 3 vai trò). `AccessMatrixTest` đọc bảng này và kiểm tra hai điều cho từng ô: Tool có hiện trong `tools/list` không, và gọi thẳng bằng tên có bị chặn không. Muốn thêm endpoint thì chỉ cần thêm một dòng vào bảng. Bảng này sẽ dùng lại cho thí nghiệm E1 của benchmark.
- `McpGatewayStarterTest` chạy Starter trên một app nhỏ **không có Spring Security** với 5 cấu hình khác nhau (mặc định, bật DELETE, tắt Starter, thiếu STATELESS, sai đường dẫn OpenAPI) và kiểm tra timeout.
- `TaskFlowTest` chạy các luồng nghiệp vụ hoàn toàn qua MCP Tool.

## Ba lớp kiểm tra dữ liệu đầu vào

Một lời gọi Tool có thể bị từ chối ở ba chỗ, mỗi chỗ trả lỗi khác nhau:

1. Sai kiểu (ví dụ `id` là chữ): MCP server từ chối theo `inputSchema`, chưa gọi tới REST API.
2. Đúng kiểu nhưng sai quy tắc (ví dụ `title` rỗng): REST API trả `HTTP 400` nhờ `@Valid`.
3. Đúng dữ liệu nhưng không phải của mình: REST API trả `HTTP 404`.

## Class mới trong Starter

| Class | Vai trò |
|---|---|
| `McpGatewayProtocolCheckAutoConfiguration` | Log cảnh báo khi Starter bật nhưng MCP server không ở chế độ STATELESS |

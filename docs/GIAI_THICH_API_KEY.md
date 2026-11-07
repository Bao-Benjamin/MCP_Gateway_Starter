# API key hoạt động thế nào, và cách thay thế (JWT)

*Viết ngày 25/10/2026.*

## 1. Ý tưởng

API key giống một **mật khẩu dài** mà mỗi người dùng gửi kèm trong mọi request, qua header `X-API-Key`. Server tra key đó trong danh sách để biết "đây là ai, có vai trò gì".

Lưu ý quan trọng: **phần API key thuộc về app to-do mẫu, không thuộc Starter.** Starter không biết gì về API key. Nó chỉ đọc kết quả mà Spring Security để lại (đối tượng `Authentication`), và chuyển tiếp header khi gọi lại API. Vì vậy đổi cách xác thực (ví dụ sang JWT) không phải sửa Starter.

## 2. Ba nơi liên quan trong code

| File | Vai trò |
|---|---|
| `todo-sample-app/.../application.yml` (mục `demo.security.keys`) | Danh sách key: `alice-key` là alice (USER), `bob-key` là bob (USER), `admin-key` là admin (USER, ADMIN) |
| `security/ApiKeyAuthenticationFilter.java` | Đọc header `X-API-Key`, tra danh sách, cho biết người gọi là ai |
| `security/SecurityConfig.java` | Quy tắc: URL nào cần vai trò nào |

## 3. Chuyện gì xảy ra với một request

`ApiKeyAuthenticationFilter` chạy trước mọi controller và xử lý 4 trường hợp:

| Request | Filter làm gì | Kết quả |
|---|---|---|
| Không có header `X-API-Key` | Không làm gì | Spring Security coi là **khách** (anonymous) |
| Key không có trong danh sách | Trả **401** ngay | Dừng, không vào controller |
| Key đúng nhưng tài khoản bị khóa | Trả **403** ngay | Dừng |
| Key đúng | Tạo `Authentication` (tên người dùng + `ROLE_USER`, `ROLE_ADMIN`...) và lưu vào `SecurityContext` | Đi tiếp |

Sau filter, các quy tắc trong `SecurityConfig` quyết định có cho vào URL hay không:

```
/api/public/**             ai cũng vào được
/api/tasks/**, /api/me/**  cần ROLE_USER
/api/admin/**              cần ROLE_ADMIN
/mcp, /v3/api-docs/**      ai cũng vào được (quyền kiểm tra theo từng Tool)
```

## 4. Đi qua MCP thì sao

Một lời gọi Tool đi qua filter **hai lần**:

```
MCP client --(X-API-Key: alice-key)--> /mcp
   1. ApiKeyAuthenticationFilter: alice, ROLE_USER
   2. Starter đọc Authentication này để lọc Tool và kiểm tra quyền gọi
   3. Starter gọi lại REST API, CHUYỂN TIẾP header X-API-Key: alice-key
        ApiKeyAuthenticationFilter chạy lần nữa: vẫn là alice
        SecurityConfig + controller kiểm tra như request bình thường
```

Nhờ chuyển tiếp header, REST API nhận đúng danh tính người gọi, không phải danh tính của Gateway. Header nào được chuyển tiếp là do cấu hình `mcp.gateway.forward-headers` (mặc định `Authorization` và `X-API-Key`).

## 5. Cách khác đã bàn từ đầu: OAuth2 / JWT

Trong bản góp ý kế hoạch ban đầu có hai lựa chọn: **API key** (đang dùng) và **JWT** do một máy chủ xác thực như **Keycloak** cấp. Khi đó ta đã chọn API key cho MVP và để JWT làm hướng mở rộng.

Với JWT, người dùng đăng nhập ở Keycloak và nhận một **token có hạn dùng** (ví dụ 5 phút), bên trong ghi sẵn tên và vai trò, có chữ ký số. Client gửi `Authorization: Bearer <token>`. Server chỉ cần kiểm tra chữ ký, không phải tra danh sách.

| | API key (đang dùng) | JWT với Keycloak |
|---|---|---|
| Ai cấp | Ứng dụng tự giữ danh sách key | Máy chủ xác thực riêng (Keycloak) |
| Hết hạn | Không, dùng mãi tới khi xóa | Có, thường vài phút |
| Lộ ra thì sao | Dùng được mãi tới khi đổi key | Chỉ dùng được tới khi token hết hạn |
| Vai trò nằm ở đâu | Trong cấu hình của ứng dụng | Trong token (claim) |
| Cài đặt | Rất đơn giản, không cần thêm gì | Phải chạy Keycloak (Docker), cấu hình realm, client |
| Chuẩn MCP | Không phải chuẩn | Đặc tả MCP dùng OAuth 2.1 cho xác thực HTTP |
| Hợp với | Demo, test, máy với máy | Hệ thống thật, nhiều người dùng |

## 6. Đổi sang JWT thì phải sửa gì

- **Starter: không sửa gì.** Nó đã chuyển tiếp header `Authorization` và chỉ dựa vào `Authentication` của Spring Security. Đây là một điểm tốt để nêu trong báo cáo: Starter không phụ thuộc cách xác thực.
- **App mẫu:** bỏ `ApiKeyAuthenticationFilter`, thêm `spring-boot-starter-oauth2-resource-server`, cấu hình địa chỉ Keycloak, và ánh xạ vai trò trong token thành `ROLE_...`.
- **Test:** lấy token từ Keycloak (hoặc tạo token giả trong test) thay cho API key.

Theo kế hoạch, việc này để làm sau khi xong phần chính, nếu còn thời gian.

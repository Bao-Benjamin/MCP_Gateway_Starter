# Giải thích `McpGatewayAutoConfiguration.java`

> File: `mcp-gateway-spring-boot-starter/src/main/java/com/thesis/mcpgateway/autoconfigure/McpGatewayAutoConfiguration.java`.
> Chỉ giải thích, không đổi code. Số dòng lấy theo bản hiện tại.

## 0. File này là gì?

Đây là **"cửa vào" của Starter**. Ứng dụng to-do không hề gọi class nào của Starter. Nó chỉ thêm dependency vào `pom.xml`, còn Spring Boot tự tìm file này qua
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` rồi tạo các bean bên trong. "Tự động cấu hình" (auto-configuration) nghĩa là vậy.

Nói gọn, file này làm 3 việc:
1. **Chen** lớp kiểm soát quyền vào giữa HTTP `/mcp` và MCP server của Spring AI.
2. **Tạo** các bean để đọc OpenAPI, sinh Tool, lưu Tool và gọi REST API.
3. **Chọn** cách kiểm tra quyền: dùng Spring Security nếu app có, nếu không thì cho phép tất cả.

## 1. Các annotation trên class (dòng 48–51)

| Dòng | Annotation | Ý nghĩa dễ hiểu |
|---|---|---|
| 48 | `@AutoConfiguration(before = McpServerStatelessWebMvcAutoConfiguration.class)` | Đây là auto-config, và phải chạy **trước** auto-config MCP của Spring AI. Lý do: mình cần đăng ký bean transport của mình trước, để bean mặc định của Spring AI (có `@ConditionalOnMissingBean`) thấy "đã có rồi" và tự bỏ qua. |
| 49 | `@ConditionalOnProperty(mcp.gateway.enabled = true, matchIfMissing = true)` | Công tắc bật/tắt. Không khai báo gì thì **mặc định bật**. Ghi `mcp.gateway.enabled: false` thì cả file này bị bỏ qua. |
| 50 | `@ConditionalOnProperty(spring.ai.mcp.server.protocol = STATELESS)` | Chỉ chạy khi MCP server ở chế độ STATELESS: mỗi request MCP là một HTTP request riêng, mang theo API key của người gọi. Chế độ khác (SSE, STREAMABLE) giữ phiên lâu dài, nên không biết từng request của ai. Không phải STATELESS thì file này tắt, và `McpGatewayProtocolCheckAutoConfiguration` in cảnh báo. |
| 51 | `@EnableConfigurationProperties({McpGatewayProperties, McpServerStreamableHttpProperties})` | Đọc cấu hình từ `application.yml` vào 2 object: `mcp.gateway.*` của mình, và cấu hình MCP của Spring AI (lấy đường dẫn `/mcp`). |

Hai `@ConditionalOnProperty` ghép kiểu **AND**: cả hai phải đúng thì file mới có tác dụng.

### `@ConditionalOnMissingBean` (gặp ở hầu hết các bean)

"Chỉ tạo bean này nếu app **chưa tự tạo** bean cùng kiểu." Đây là quy ước của mọi Starter: cho sẵn mặc định, nhưng app có quyền thay thế. Ví dụ app muốn phân quyền theo cách riêng thì chỉ cần khai báo một bean `ToolAccessControl` của mình, Starter sẽ nhường.

## 2. Hai bean transport, phần quan trọng nhất (dòng 53–79)

Nhắc lại: `/mcp` do **Spring AI** phục vụ. Spring AI dùng một "transport" (class nhận HTTP request rồi chuyển cho MCP server). Starter không viết lại `/mcp`, nó chỉ **thay** và **bọc** transport đó.

### 2a. `webMvcStatelessServerTransport` (dòng 58–68): thay transport

```java
WebMvcStatelessServerTransport.builder()
    .jsonMapper(...)                                  // cách đọc/ghi JSON, giống Spring AI
    .messageEndpoint(serverProperties.getMcpEndpoint()) // đường dẫn "/mcp"
    .contextExtractor(new CallerContextExtractor(...))  // <- ĐIỂM KHÁC DUY NHẤT
    .build();
```

Bean này gần như giống hệt bean mặc định của Spring AI. Chỉ khác là có thêm `contextExtractor`: với mỗi request `/mcp`, nó đọc **ai đang gọi** (`Authentication` từ Spring Security) và **header cần chuyển tiếp** (`X-API-Key`, `Authorization`), rồi gắn vào context của MCP. Không có bước này thì các bước sau không biết người gọi là ai.

Vì Spring AI khai báo bean của họ với `@ConditionalOnMissingBean`, và file này chạy trước (dòng 48), nên bean của mình được dùng, bean của Spring AI bị bỏ qua.

### 2b. `accessControlledServerTransport` (dòng 74–79): bọc transport

```java
@Bean
@Primary
public McpStatelessServerTransport accessControlledServerTransport(WebMvcStatelessServerTransport transport, ...) {
    return new AccessControlledServerTransport(transport, toolRegistry, toolAccessControl);
}
```

- Nhận transport ở 2a rồi **bọc** nó lại (Decorator pattern). Khi MCP server gắn handler vào, lớp bọc chen `AccessControlledServerHandler` vào giữa. Handler này lọc `tools/list` và chặn `tools/call`.
- `@Primary`: lúc này có **2 bean** cùng kiểu `McpStatelessServerTransport` (2a và 2b). MCP server của Spring AI cần đúng 1 cái, nên `@Primary` bảo Spring: "lấy cái đã bọc".

Hình dung:

```
HTTP /mcp ──► WebMvcStatelessServerTransport (2a: đọc người gọi)
                 └─► AccessControlledServerHandler (2b: lọc / chặn theo quyền)
                         └─► MCP server của Spring AI (xử lý tools/list, tools/call)
```

Vì sao phải làm vòng vèo như vậy: MCP Java SDK trả **cùng một danh sách Tool cho mọi người** và không có chỗ mở rộng nào để lọc theo người gọi (issue #746). Transport là chỗ duy nhất mọi request đều đi qua, nên mình chen vào đó. Đây chính là đóng góp cốt lõi của đề tài.

## 3. Các bean xử lý Tool (dòng 81–113)

| Bean | Dòng | Làm gì |
|---|---|---|
| `ToolRegistry` | 82–85 | "Sổ" lưu các Tool Gateway đã sinh ra: tên Tool ↔ endpoint (method + path). Handler ở 2b tra sổ này để biết Tool `lockUser` là `POST /api/admin/users/{id}/lock`. |
| `OpenApiEndpointReader` | 88–91 | Đọc JSON OpenAPI (`/v3/api-docs`), lọc endpoint theo `include-paths`, `exclude-paths`, `include-methods`, và giải `$ref`. |
| `ToolGenerator` | 94–97 | Biến mỗi endpoint thành một Tool MCP: tên (từ `operationId`), mô tả, `inputSchema`, gợi ý (readOnly/destructive). |
| `ToolInvoker` | 100–104 | Khi Tool được gọi thì gửi HTTP thật tới REST API (`HttpLoopbackToolInvoker`), kèm header của người gọi. |
| `GatewayToolRegistrar` | 106–113 | **Người điều phối**. Khi app sẵn sàng, nó dùng 3 bean trên để đọc OpenAPI, sinh Tool, ghi vào sổ, rồi `mcpServer.addTool(...)` để đăng ký vào MCP server. |

`GatewayToolRegistrar` không có `@ConditionalOnMissingBean`, vì nó là phần bắt buộc của Gateway, không phải mặc định để thay.

## 4. Hai hàm phụ `restClient` và `baseUrl` (dòng 114–134)

Đây là hàm `static` thường, **không phải bean**. Hai bean `ToolInvoker` và `GatewayToolRegistrar` cùng dùng chúng.

**`restClient(...)` (dòng 118–123): tạo HTTP client có timeout.**
- `connectTimeout` (mặc định 5s): thời gian chờ tối đa để kết nối.
- `readTimeout` (mặc định 30s): thời gian chờ tối đa để nhận phản hồi. Không đặt timeout thì một API bị treo sẽ giữ request MCP mãi mãi.
- `builder.getIfAvailable(RestClient::builder)`: nếu app đã có `RestClient.Builder` (đã cấu hình converter, interceptor) thì dùng lại, không có thì tự tạo mới. Spring Boot 4 không còn tạo sẵn bean này, nên phải dùng `ObjectProvider`.

**`baseUrl(...)` (dòng 126–134): địa chỉ để Gateway gọi lại chính app.**
- Có `mcp.gateway.base-url` trong yml thì dùng nó.
- Không có thì tự ghép `http://localhost:<port><context-path>`, ví dụ `http://localhost:8080`.
- Trả về `Supplier` (hàm, gọi lúc nào tính lúc đó) chứ không trả chuỗi. Lý do: khi tạo bean thì server **chưa chạy**, nên chưa biết port. Đặc biệt khi test dùng port ngẫu nhiên (`local.server.port`), port chỉ có sau khi Tomcat khởi động xong.

## 5. Chọn cách kiểm tra quyền (dòng 136–156)

Có 2 phương án, Spring chọn **một**:

**5a. `SpringSecurityAccessControlConfiguration` (dòng 136–148)**: class lồng bên trong.
- `@ConditionalOnClass(WebInvocationPrivilegeEvaluator.class)`: chỉ có hiệu lực khi app **có thư viện Spring Security**. Mình tách thành class lồng để nếu app không có Spring Security thì Java không phải nạp class đó (tránh lỗi `ClassNotFoundException`).
- Tạo `SpringSecurityToolAccessControl`, class hỏi luật URL của app (`SecurityConfig`) như đã giải thích trong `GIAI_THICH_LUONG_API_KEY.md`.
- Nhận `ObjectProvider<WebInvocationPrivilegeEvaluator>` (lấy "lười", lúc cần mới lấy) vì bean evaluator do cấu hình Spring Security của app tạo ra, có thể **sau** bean này. Trước đây mình dùng `@ConditionalOnBean` thì bị kiểm tra quá sớm, nên danh sách không được lọc. Lỗi đó đã sửa ở giai đoạn 1.
- `context-path` được truyền vào để kiểm tra URL đúng khi app chạy dưới đường dẫn con (vd `/todo`).

**5b. `permitAllToolAccessControl` (dòng 151–155)**: phương án dự phòng.
- App không có Spring Security thì không có luật nào để hỏi, nên cho phép mọi Tool.
- Có `@ConditionalOnMissingBean`. Spring xử lý class lồng (5a) **trước** các `@Bean` của class ngoài, nên khi có Spring Security thì 5a đã tạo bean `ToolAccessControl` rồi, và 5b tự bỏ qua.

## 6. Thứ tự chạy khi app khởi động

```
1. Spring Boot đọc AutoConfiguration.imports → thấy McpGatewayAutoConfiguration
2. Kiểm tra điều kiện: mcp.gateway.enabled? protocol=STATELESS? → đúng thì tiếp tục
3. Chạy TRƯỚC auto-config MCP của Spring AI:
   - tạo WebMvcStatelessServerTransport (có CallerContextExtractor)
   - tạo AccessControlledServerTransport (@Primary)
   - tạo ToolRegistry, OpenApiEndpointReader, ToolGenerator, ToolInvoker, ToolAccessControl
4. Auto-config Spring AI chạy:
   - thấy đã có transport → không tạo transport mặc định
   - tạo McpStatelessSyncServer, dùng transport @Primary (bản đã bọc)
   - server gắn handler → lớp bọc chen AccessControlledServerHandler vào
   - đăng ký route /mcp
5. Tạo GatewayToolRegistrar (cần McpStatelessSyncServer, nên tạo sau server)
6. Tomcat khởi động, có port → baseUrl giờ mới tính được
7. springdoc dựng xong /v3/api-docs
8. ApplicationReadyEvent → GatewayToolRegistrar.onApplicationEvent (GatewayToolRegistrar.java:71):
   đọc /v3/api-docs (thử lại nếu lỗi) → sinh Tool → ghi ToolRegistry → mcpServer.addTool
   → log "Published N MCP tools"
9. Sẵn sàng nhận request /mcp
```

Bước 8 phải đợi `ApplicationReadyEvent`, vì springdoc chỉ dựng tài liệu OpenAPI khi app đã chạy. Gateway đọc nó qua HTTP như một client bình thường.

## 7. Tóm tắt một dòng cho từng khối

| Khối | Một câu |
|---|---|
| Annotation class | Bật khi `enabled` và STATELESS, chạy trước Spring AI |
| Transport 2a | Transport của Spring AI + đọc người gọi |
| Transport 2b | Bọc 2a để lọc/chặn Tool theo quyền |
| Bean Tool | Đọc OpenAPI → sinh Tool → lưu sổ → gọi REST |
| `restClient`/`baseUrl` | HTTP client có timeout, địa chỉ gọi lại chính app |
| Access control | Có Spring Security thì hỏi luật URL, không có thì cho phép hết |

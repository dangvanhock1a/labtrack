---
paths:
  - "src/test/java/**/*.java"
---
# Test: unit, @WebMvcTest, @SpringBootTest
- `@DisplayName` mở đầu bằng `TC-BRxx-yy`, mã hai chữ số. Đúng: `"TC-BR05-03 Gửi form hủy: lý do được cắt khoảng trắng…"`. Sai: `"Hủy mẫu (TC-BR05-03)"`, `"TC-BR5-3 …"`.
- Unit thuần: dựng bằng `new`, mock mapper, `Clock.fixed(…, ZoneId.of("Asia/Ho_Chi_Minh"))`.
- Có ít nhất một case sau nửa đêm giờ VN khi UTC còn là hôm trước, ví dụ `2026-01-01T00:30+07:00` = `2025-12-31T17:30Z`.
- Controller: `@WebMvcTest` + `@MockitoBean` + `@Import(SecurityConfig.class)`; thiếu `@Import` thì test chạy trên cấu hình bảo mật mặc định của Boot, mọi vai trò đều qua.
- Boot 4: dùng `@MockitoBean`, không dùng `@MockBean`; `@WebMvcTest` nằm ở `org.springframework.boot.webmvc.test.autoconfigure`. Sai: `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`.
- Người đăng nhập: `@WithAppUser(username, role)`; vai trò là tham số thì `with(user(WithAppUserSecurityContextFactory.principal(…)))`. Không dùng `@WithMockUser` cho test render trang (navbar đọc `principal.fullName`).
- Mọi POST qua MockMvc thêm `.with(csrf())`, trừ case cố ý kiểm việc thiếu CSRF.
- Cần CSDL thật: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` + `@Transactional`.
- Test không có transaction bao ngoài (test đồng thời, `RANDOM_PORT`) để lại dữ liệu không xóa được (trigger V4): chỉ đếm trên dữ liệu do chính test tạo. Sai: `SELECT count(*) FROM result_audit`. Đúng: lọc theo id mẫu hoặc id kết quả của test.
- Trang lỗi `templates/error/*.html` chỉ hiện qua Tomcat thật: test bằng `@SpringBootTest(webEnvironment = RANDOM_PORT)`; `@WebMvcTest` không chuyển sang `/error`.
- Ràng buộc CSDL được test bằng SQL ghi thẳng như `TestResultConstraintTests`, kiểm đúng tên ràng buộc trong lỗi.
- `LabSampleServiceTests` là test đăng ký, `LabSampleServiceTest` là test đổi trạng thái. Thêm test vào đúng lớp.
- Tên `*Test`/`*Tests` chạy ở surefire; tên `*IT` chỉ chạy ở `verify` (failsafe). Không đặt `*IT` cho test không phải E2E.

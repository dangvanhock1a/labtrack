---
paths:
  - "src/test/java/**/e2e/**/*.java"
  - "docs/kich-ban-e2e.md"
---
# E2E Playwright
- Lớp E2E tên `*IT`, nằm trong gói `e2e`, kế thừa `E2ETestBase`; chạy ở `./mvnw verify`, một lớp bằng `-Dit.test=TenLopIT`.
- `@DisplayName` có dạng `TC-E2E-xx <mô tả> (BR-yy)`, xx theo `docs/kich-ban-e2e.md`, mã BR ở cuối. Ví dụ: `"TC-E2E-06 Kiểm nghiệm viên gõ thẳng /samples/new: … (BR-12)"`.
- Locator theo thứ tự `getByRole` → `getByLabel` → `getByTestId`. Sai: `page.locator("//button")`, `page.locator(".btn-primary")`.
- Tên dễ trùng thì khớp chính xác: `new Page.GetByRoleOptions().setName("Duyệt").setExact(true)`.
- Không chờ bằng sleep: cấm `Thread.sleep` và `page.waitForTimeout`; chờ bằng `assertThat(locator).isVisible()` hoặc `assertThat(page).hasURL(…)`.
- Regex truyền cho Playwright không dùng `Pattern.quote` (RegExp của JavaScript không hiểu `\Q…\E`); escape tay như `actorAt`.
- Đăng nhập và đăng xuất bằng `login(username)`/`logout()` của lớp cha; tài khoản trong `R__dev_seed.sql`, mật khẩu `Passw0rd!`.
- Tiền điều kiện dựng qua service (`seedSampleInTesting()`); luồng đầy đủ qua giao diện chỉ nằm ở TC-E2E-03.
- Trước mỗi test, `DbCleaner` TRUNCATE mọi bảng trừ `app_user`, `test_parameter`, `flyway_schema_history` và tắt tạm trigger USER trong cùng transaction; nó từ chối CSDL không mang tên `test`/`*_test`.
- Bảng tham chiếu mới → thêm tên vào `NOT IN (…)` của `DbCleaner`.
- Không đặt `@Transactional` trên lớp E2E: trình duyệt gọi Tomcat thật, nên dữ liệu được commit.
- Test đỏ: xem ảnh `target/e2e-screenshots/<Lớp>-<method>-end.png` và trace `target/playwright-traces/<Lớp>-<method>.zip` (mở bằng trace.playwright.dev).
- Locator lệch giao diện: đối chiếu `docs/kich-ban-e2e.md` và yêu cầu để quyết lỗi nằm ở test hay ở ứng dụng, rồi mới sửa.
- `data-testid` trong template chỉ phục vụ E2E; đổi hay xóa là phải sửa test.

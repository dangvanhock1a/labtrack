---
paths:
  - "src/main/resources/templates/**/*.html"
---
# Template Thymeleaf
- Form POST dùng `th:action`. Đúng: `<form method="post" th:action="@{/samples/{id}/start(id=${sample.id})}">`. Sai: `action="/samples/1/start"` → thiếu token CSRF, nhận 403.
- `sec:authorize` chỉ ẩn nút; nút hay form mới vẫn cần luật trong `SecurityConfig`, và điều kiện `sec:authorize` phải trùng với luật đó.
- Chuỗi trong `hasRole('…')`/`hasAnyRole('…')` là tên hằng `UserRole`; `status.name() == '…'` là tên hằng enum. Gõ sai thì không có lỗi nào báo, nút chỉ biến mất.
- Mọi `#authentication.principal.*` nằm trong `sec:authorize="isAuthenticated()"`: navbar dùng chung cho trang đăng nhập và trang lỗi, nơi principal là chuỗi `anonymousUser`.
- Không đặt `th:if` cùng thẻ với `th:replace` (`th:replace` chạy trước nên điều kiện bị mất). Đúng: `<th:block th:if="…"><span th:replace="~{…}"></span></th:block>`.
- Trang mới dùng các fragment `head(…)`, `navbar`, `flash`, `scripts` của `fragments/layout.html`; Bootstrap lấy từ webjar `@{/webjars/bootstrap/5.3.8/…}`, không lấy từ CDN.
- Mọi ô nhập có nhãn: `<label th:for>` hoặc `<label class="visually-hidden">` (NFR-08), vì E2E tìm ô bằng `getByLabel`.
- Ngày giờ hiển thị bằng `#temporals.format(x, 'dd/MM/yyyy HH:mm')`, ngày bằng `'dd/MM/yyyy'` (NFR-04).
- Đổi chữ trên nút, nhãn hay tiêu đề → sửa E2E (`setName("Duyệt")`) và `docs/kich-ban-e2e.md`; tìm trước bằng `grep -rn '"<chữ cũ>"' src/test`.
- `data-testid` chỉ phục vụ E2E; đổi hay xóa là phải sửa test trong gói `e2e`.
- Trang lỗi mới `templates/error/NNN.html`: test bằng `@SpringBootTest(webEnvironment = RANDOM_PORT)` như `ForbiddenPageIntegrationTests`, không bằng `@WebMvcTest`.

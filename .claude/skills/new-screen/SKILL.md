---
name: new-screen
description: >-
  Tạo hoặc sửa một màn hình đi đủ 3 tầng mapper → service → controller + Thymeleaf theo pattern có sẵn, kèm unit
  test, @WebMvcTest, luật SecurityConfig và đề xuất một kịch bản E2E. Đề xuất URL, quyền và danh sách file rồi chờ
  người dùng đồng ý mới code. Dùng khi người dùng nói "thêm màn hình", "thêm trang", "thêm chức năng trên giao diện",
  "thêm form", "tìm kiếm", "lọc", hoặc BR có phần giao diện (BR-13 tra cứu, BR-15 tổng quan, BR-16 xuất CSV…).
  Phần đổi schema làm theo skill flyway-migration. Không dùng để viết test E2E.
argument-hint: "<màn hình hoặc chức năng cần làm, kèm mã BR nếu có>"
allowed-tools: Bash(./mvnw *) Bash(git grep *) Bash(git status *)
---
# Màn hình mới đi đủ 3 tầng

Việc cần làm: $ARGUMENTS

## File mẫu phải đọc trước
Code mới chép đúng pattern của các file này, không tự nghĩ kiểu khác.

| Tầng | File | Pattern cần chép |
|---|---|---|
| Dữ liệu | `mapper/LabSampleMapper.xml` + `persistence/LabSampleMapper.java` | `<sql id="columns">`, chỉ `#{}`, `@Param` khi có nhiều tham số, Javadoc trên interface |
| Nghiệp vụ | `service/LabSampleService.java` | `@Transactional` cho method ghi, `BusinessException(code, field, message)`, giờ từ `Clock`, đọc ra qua `toClockZone`, ngưỡng và mặc định khai báo một lần (`DEFAULT_DUE_DAYS`) |
| Trình bày | `web/SampleController.java` | `@InitBinder trimStrings`, `@ModelAttribute` cho danh sách chọn, PRG + flash `message`, `principal.getName()`, `register` tự bắt `BusinessException` để hiện lại form |
| Trình bày | `templates/samples/form.html` | `th:object`/`th:field`, `th:errorclass="is-invalid"`, `th:errors`, mỗi ô có `<label for>` |
| Trình bày | `templates/fragments/layout.html` | fragment `head(…)`, `navbar`, `flash`, `scripts`; nhãn `*Badge(…)` so theo tên hằng |
| Test web | `web/SampleControllerTests.java` | `@WebMvcTest` + `@Import(SecurityConfig.class)` + `@MockitoBean`, `@WithAppUser`, `.with(csrf())`, `tagContaining`, fixture `sampleIn(…)` |
| Phân quyền | `config/SecurityConfig.java` + `web/AccessControlTests.java` | luật theo URL + method, `@ParameterizedTest @CsvSource` đủ 4 vai trò |

Đọc các file trên sẽ tự nạp `.claude/rules/web.md`, `ui.md`, `service.md`, `mybatis.md`, `testing.md`: làm theo chúng.

## Hiện trạng
Endpoint đang có:
!`git grep -n -E "@(Get|Post|Request)Mapping" -- "*Controller.java"`

Luật phân quyền (luật khớp đầu tiên quyết định):
!`git grep -n "requestMatchers" -- "*SecurityConfig.java"`

## Quy trình
1. **Liệt kê BR áp dụng.** Đọc mục 8 (BR), mục 9 (ma trận quyền), mục 10 (NFR) của `docs/yeu-cau-nghiep-vu.md`, và
   mục 1 `docs/cau-hoi-lam-ro.md` (tầng phải chặn, loại test). Mỗi BR một dòng: mã, quy tắc rút gọn, tầng chặn, test
   cần có. Trang danh sách thì kèm NFR-01 (≤ 3 câu SQL/trang); có ngày giờ thì kèm NFR-04; có ô nhập thì kèm NFR-08.
   Gặp đúng tình huống ở mục 2 `docs/cau-hoi-lam-ro.md` thì hỏi người dùng, không tự quyết.
2. **Đề xuất rồi DỪNG, chờ người dùng đồng ý.** Chưa tạo hay sửa file nào ở bước này. Trình bày:
   - URL + method cho từng endpoint, vai trò được phép theo mục 9, và luật sẽ chèn vào chỗ nào trong `SecurityConfig`.
   - File theo tầng (dữ liệu → nghiệp vụ → trình bày → test), ghi rõ tạo mới hay sửa, mỗi file một dòng lý do.
   - Schema có đổi không; có thì phần CSDL làm theo skill `flyway-migration`, số V lấy theo mục 13.
   - Câu chữ tiếng Việt mới (nhãn, nút, thông báo lỗi), vì test so khớp nguyên văn. Câu nào BR không quy định thì
     đánh dấu là tự đặt.
   Người dùng sửa đề xuất thì đề xuất lại. Chỉ sang bước 3 khi có câu đồng ý rõ ràng.
3. **Code từ dưới lên**; xong tầng dưới mới sang tầng trên.
   - **Dữ liệu:** SQL chỉ trong `mapper/*.xml`. Lọc nhiều điều kiện thì dùng `<where>` + `<if>`. Cột sắp xếp chọn bằng
     `<choose>` trên tên cột cố định, không dùng `${}`. Tìm theo từ khóa thì escape `\`, `%`, `_` ở service rồi mới
     truyền vào `#{}` (BR-13). Phân trang `LIMIT #{…} OFFSET #{…}`, cỡ trang lấy từ `labtrack.page-size`. Số liệu
     mỗi dòng (ví dụ tiến độ x/y) gộp bằng JOIN hoặc subquery, không gọi lại mapper cho từng dòng (N+1). SQL mới có
     test tích hợp ở `persistence/*MapperTests` (PostgreSQL thật, CSDL `labtrack_test`).
   - **Nghiệp vụ + unit test:** controller chỉ gọi service; package `service` không import Spring Security. Unit test
     dựng bằng `new`, mock mapper, `Clock.fixed(…, ZoneId.of("Asia/Ho_Chi_Minh"))`; có ngày giờ thì thêm một case
     sau nửa đêm giờ VN khi UTC còn là hôm trước. Đặt test vào `LabSampleServiceTests` (đăng ký),
     `LabSampleServiceTest` (đổi trạng thái), hoặc lớp `<Service>Tests` mới.
   - **Trình bày + @WebMvcTest:** có ô nhập thì tạo lớp form riêng (Bean Validation như `SampleForm`); lỗi
     `typeMismatch` đặt câu ở `messages.properties`. Template dùng các fragment của `layout.html`; ngày giờ hiển thị
     qua `@fmt`. Test ở `SampleControllerTests` hoặc `<Controller>Tests` mới, gồm: trang render đúng; lỗi form hiện
     nguyên văn và không gọi service; POST truyền đúng giá trị và đúng tên đăng nhập xuống service; giá trị lạ trong
     request bị chặn.
4. **SecurityConfig** khi có URL mới: thêm `requestMatchers(HttpMethod.X, "…")` đứng trước
   `GET /samples, /samples/*`. Thêm case 4 vai trò vào `AccessControlTests` (vai trò không được phép nhận 403), và một
   case POST thiếu CSRF nhận 403. `sec:authorize` trong template phải trùng với luật này.
5. **Chạy** `./mvnw -q test` (CSDL `labtrack_test`, không lên CSDL dev). Báo cáo file đã tạo/sửa, số test chạy/đỏ, kèm output lỗi thật.
6. **Đề xuất 1 kịch bản E2E**, chưa viết code. Dùng đúng cột của bảng trong `docs/kich-ban-e2e.md`: Mã TC (số
   `TC-E2E-xx` kế tiếp), Use case · BR, Vai trò, Bước, Kết quả mong đợi (chữ nguyên văn từ template). Ghi locator
   theo thứ tự `getByRole` → `getByLabel` → `getByTestId`, tiền điều kiện dựng qua service (`.claude/rules/e2e.md`).
   Hỏi người dùng có muốn thêm kịch bản vào `docs/kich-ban-e2e.md` và viết lớp `*IT` không.

## Bẫy hay gặp
- Template đọc thêm thuộc tính mới (`${s.x.label}`) thì fixture dựng tay phải gán thuộc tính đó, nếu không trang lỗi
  SpEL: `sampleIn` (`SampleControllerTests`), `receivedSample` (`AccessControlTests`).
- Service là `@MockitoBean`: method trả object mà chưa stub sẽ trả `null`. Ví dụ `newSampleDefaults()` trả `null`
  thì `GET /samples/new` lỗi.
- `@ModelAttribute` trên method chạy cho mọi handler của controller, kể cả POST.
- Endpoint mới chưa có luật bị `denyAll()` trả 403. Luật đặt sau `GET /samples/*` thì mọi vai trò đều mở được.
- Biến đường dẫn của mẫu phải tên `id` (`GlobalControllerAdvice` redirect về `/samples/{id}`).
- Trang có bộ lọc: giữ tham số lọc trong link phân trang và link xuất CSV, ví dụ `@{/samples(q=${q},page=${p})}`.
- Đổi chữ hay `data-testid` có sẵn thì phải sửa cả E2E và `docs/kich-ban-e2e.md`. Tìm trước bằng
  `git grep -n '"<chữ cũ>"' -- src/test`.

## Tự kiểm tra trước khi báo xong
- Controller không dùng mapper: `git grep -n "persistence\." -- "src/main/java/*/web/*"` ra 0 dòng.
- Service không dùng Spring Security: `git grep -n "springframework.security" -- "src/main/java/*/service/*"` ra 0 dòng.
- Không có `${` trong `src/main/resources/mapper/*.xml`.
- Mỗi URL mới có luật trong `SecurityConfig` và có case trong `AccessControlTests`.
- Mỗi ô nhập có nhãn; `@DisplayName` mở đầu bằng `TC-BRxx-yy`.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Quản lý mẫu kiểm nghiệm cho phòng QC dược. Ba tầng: Thymeleaf + Bootstrap → service → MyBatis → PostgreSQL 18.

## Tài liệu nghiệp vụ
- `docs/yeu-cau-nghiep-vu.md`: quy tắc `BR-xx` ở mục 8, yêu cầu phi chức năng `NFR-xx` ở mục 10, kế hoạch increment ở mục 13 (BR nào làm ở increment nào, dùng migration số mấy). Task nhắc mã BR nào thì đọc mục đó trước khi code.
- `docs/cau-hoi-lam-ro.md`: mục 1 ghi, cho từng BR, tầng phải chặn và loại test cần có. Mục 2 là 10 câu hỏi chưa có đáp án (ví dụ mã mẫu lấy năm nào khi nhập lùi ngày nhận). Gặp đúng tình huống đó thì hỏi người dùng, đừng tự quyết.

## Stack — không tự nâng hay hạ version
Java 21 · Spring Boot 4.1.1 · mybatis-spring-boot-starter 4.1.0 · Flyway · Thymeleaf · Bootstrap 5.3.8 (webjar) · Testcontainers

## Lệnh
- `./mvnw -q test` · một lớp: `-Dtest=TenLop` · một method: `-Dtest='TenLop#tenMethod'` · đầy đủ: `./mvnw verify`. Dự án không có linter hay formatter.
- `./mvnw spring-boot:run` → http://localhost:8080/samples (không có mapping cho `/`; sau khi đăng nhập, `/` trả 403). Đăng nhập bằng 6 tài khoản demo trong `db/devdata/R__dev_seed.sql` (5 của LT-BRD mục 4 và người duyệt thứ hai `duyet2`), mật khẩu `Passw0rd!`. CSDL dựng bằng `docker compose up -d`: PostgreSQL 18, cổng 5433, user/mật khẩu/CSDL đều là `labtrack`; đổi bằng `DB_URL`, `DB_USER`, `DB_PASSWORD`. Có Docker mà không muốn dựng compose: `./mvnw spring-boot:test-run` chạy `TestLabtrackApplication` với PostgreSQL của Testcontainers.
- Mọi lớp test `@SpringBootTest` cần CSDL thật, dựng PostgreSQL bằng Testcontainers qua `@Import(TestcontainersConfiguration.class)`. Máy không có Docker thì bật profile `localdb` để tắt `TestcontainersConfiguration`, và trỏ `DB_URL` sang CSDL riêng cho test, đừng chạy test lên CSDL dev:
  `$env:DB_URL='jdbc:postgresql://localhost:5433/labtrack_test'; ./mvnw -q test "-Dspring.profiles.active=localdb"`
  Windows PowerShell 5.1 tách đối số `-D` ở dấu chấm đầu tiên nếu không bọc nháy. Quên profile thì các lớp trên báo `Could not find a valid Docker environment`.

## Kiến trúc — bắt buộc
- `web` (controller, form) → `service` (quy tắc nghiệp vụ, transaction) → `persistence` (interface `@Mapper`). Controller không gọi mapper. SQL chỉ nằm trong `src/main/resources/mapper/*.xml`.
- Quy tắc quan trọng bị chặn ở nhiều tầng một cách có chủ ý: form (Bean Validation) + service + `CHECK`/`UNIQUE` của CSDL. Đừng bỏ lớp nào vì thấy lớp khác đã chặn.
- Vi phạm quy tắc ở service thì ném `BusinessException(code, field, message)`. `code` là mã ổn định để phân biệt loại lỗi (`CONCURRENT_UPDATE`, `REASON_REQUIRED`…); `field` là tên trường trên form, hoặc `null` nếu lỗi không thuộc trường nào. Overload hai tham số là `(field, message)`, không phải `(code, message)`.
- `GlobalControllerAdvice` biến mọi `BusinessException` chưa bắt thành flash `error` rồi redirect về `/samples/{id}`. Nó lấy biến đường dẫn tên đúng là `id`; endpoint đặt tên khác thì lỗi bị đưa về `/samples`. Đừng thêm try/catch vào method mới. Riêng `SampleController.register` tự bắt, vì nó cần gắn lỗi vào `BindingResult` để hiện lại form kèm dữ liệu đã nhập.
- `@InitBinder` của `SampleController` cắt khoảng trắng hai đầu và đổi ô để trống thành `null` trước khi validate, nên service nhận `null` chứ không nhận `""`.
- Thời gian chỉ lấy từ bean `Clock` (`ClockConfig`), múi giờ đọc từ `labtrack.time-zone` chứ không theo JVM. pgjdbc trả `TIMESTAMPTZ` theo UTC, nên đường đọc nào trả thời điểm cũng phải đổi về múi giờ ứng dụng — xem `LabSampleService.toClockZone`.
- Mã mẫu `LS-YYYY-NNNNN`: `SampleCodeGenerator` tăng bộ đếm theo năm (giờ VN) bằng `INSERT … ON CONFLICT … RETURNING`. Nó chạy với `Propagation.MANDATORY` để bộ đếm và mẫu cùng commit hoặc cùng rollback; gọi ngoài transaction là lỗi.
- Phân quyền (BR-12) chỉ được cưỡng chế ở `SecurityConfig`, theo URL + HTTP method. Luật cuối là `anyRequest().denyAll()`, nên endpoint mới trả 403 cho tới khi có luật riêng: thêm endpoint là thêm luật, đặt trước `GET /samples/*`. `sec:authorize` trong template chỉ ẩn nút cho dễ dùng.
- Người thực hiện: controller nhận `java.security.Principal` rồi truyền `principal.getName()` xuống service dưới dạng chuỗi. Package `service` không import Spring Security; chỉ `config` và `security` dùng nó.
- CSRF bật cho mọi POST. Form phải dùng `th:action` để Thymeleaf chèn token; `action` thường thì bị 403.
- Đổi trạng thái: bảng chuyển nằm ở `SampleStatus.canTransitionTo`. Service dựng một `StatusChange` rồi chạy `UPDATE … WHERE id = ? AND status = <trạng thái cũ>`. 0 dòng nghĩa là người khác vừa đổi trước → `CONCURRENT_UPDATE`, không phải lỗi hệ thống. UPDATE được 1 dòng thì chính `StatusChange` đó được ghi vào `sample_status_history` (BR-04), trong cùng transaction; đăng ký cũng ghi 1 dòng với `from_status` = null.
- Kết quả kiểm nghiệm (`TestResultService`, SPEC-004): nhập/duyệt/từ chối đều khóa dòng mẫu bằng `LabSampleMapper.lockById` (`FOR UPDATE`) trước mọi kiểm tra có đọc CSDL, nên hủy mẫu phải chờ, và thao tác sau thấy dữ liệu thao tác trước vừa commit. Thao tác mới đụng tới mẫu cũng phải khóa dòng mẫu trước tiên, để không deadlock. Mỗi thao tác thành công ghi đúng 1 dòng `result_audit` trong cùng transaction (số dòng khác 1 → `IllegalStateException`). Đánh giá Đạt/Không đạt do `Spec.evaluate` tính trên bản chụp tiêu chuẩn, form không có trường này. Lỗi form kết quả không hiện lại form: controller chọn 1 lỗi, đưa vào flash `error` + `errorParameterId` rồi redirect. Duyệt/từ chối chỉ REVIEWER (`SecurityConfig`).

## Bẫy
- Đổi schema là viết migration MỚI `V<n+1>__mo_ta.sql`. Không sửa migration đã commit.
- Tên hằng `SampleStatus` và `SampleType` được ghi nguyên văn vào cột có `CHECK` liệt kê giá trị. Đổi tên hằng là phải viết migration cho mọi `CHECK` đó, và sửa cả template so chuỗi tên hằng (`fragments/layout.html`, `samples/detail.html`).
- Tên hằng `ResultStatus`, `Verdict`, `ResultAction` cũng được ghi vào cột có `CHECK` (V4), xuất hiện dạng chuỗi trong trigger của V4 và trong template (`fragments/layout.html`, `samples/detail.html`). Đổi tên hằng là phải viết migration và sửa template.
- V4 có trigger chặn DELETE/TRUNCATE `test_result` và mọi UPDATE/DELETE/TRUNCATE `result_audit`; `test_result` chỉ được UPDATE một lần, từ PENDING sang APPROVED/REJECTED. Dữ liệu do test không có transaction bao ngoài để lại (`TestResultConcurrencyTests`, `ResultFlowIntegrationTests`, TC-BR10-03) không xóa được và tích lũy trong `labtrack_test` qua các lần chạy: test chỉ đếm trên mẫu do chính nó tạo, không đếm toàn bảng.
- Tên hằng `UserRole` được ghi vào `app_user.role` (`ck_app_user_role`) và xuất hiện dạng chuỗi trong `hasRole('…')`/`hasAnyRole('…')` của template. Đổi tên hằng là phải viết migration và sửa cả template.
- Navbar dùng chung cho trang đăng nhập và trang lỗi, nơi principal là chuỗi `anonymousUser`. Mọi chỗ đọc `#authentication.principal.*` phải nằm trong `sec:authorize="isAuthenticated()"`.
- `db/devdata/R__dev_seed.sql` là migration repeatable: sửa file là Flyway chạy lại nó ở lần khởi động sau, nên phải viết idempotent (`ON CONFLICT … DO UPDATE`). Hash mật khẩu dán tay; TC-BR12-05 bắt hash sai.
- Trang lỗi `templates/error/*.html` chỉ hiện qua Tomcat thật. Trong `@WebMvcTest`, lỗi không được chuyển sang `/error`, và Boot không chọn template `error/NNN` vì không có bean `DispatcherServlet`. Test trang lỗi bằng `@SpringBootTest(webEnvironment = RANDOM_PORT)`, như `ForbiddenPageIntegrationTests`.
- `ck_lab_sample_due_date` ở V1 ghi cứng `'Asia/Ho_Chi_Minh'`. Đổi `labtrack.time-zone` là phải đổi cả ràng buộc này bằng migration.
- `<select>` bọc `INSERT/UPDATE … RETURNING` phải có `flushCache="true" useCache="false"`, nếu không lần gọi thứ hai trong cùng transaction nhận lại giá trị cũ từ cache.
- Boot 4 đổi chỗ so với Boot 3: dùng `@MockitoBean` (không phải `@MockBean`), `@WebMvcTest` nằm ở `org.springframework.boot.webmvc.test.autoconfigure`.
- Không dùng H2. Test chạy trên PostgreSQL thật.
- Thông báo lỗi tiếng Việt bị test so khớp nguyên văn. Sửa câu chữ là phải sửa cả test. Thông báo khi form nhận sai kiểu dữ liệu (`typeMismatch`) nằm ở `messages.properties`.
- `.mvn/jvm.config` ép stdout/stderr của Maven sang UTF-8. Trên Windows dùng locale không phải UTF-8 (ví dụ locale Nhật, MS932), thiếu nó thì tiếng Việt in ra thành `?`. Output có `?` hay ký tự lạ thay chữ có dấu là lỗi hiển thị, không phải dữ liệu sai. Chạy `java` bằng tay thì thêm `-Dstdout.encoding=UTF-8`.

## Test
- `@DisplayName` mở đầu bằng `TC-BRxx-yy`.
- Ba kiểu test: unit thuần (dựng bằng `new`, mock mapper, `Clock.fixed` múi giờ VN, thử cả mốc sau nửa đêm giờ VN khi UTC còn là hôm trước); `@WebMvcTest` + `@MockitoBean` cho controller; `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` + `@Transactional` khi cần CSDL thật.
- `@WebMvcTest` phải `@Import(SecurityConfig.class)`; thiếu nó thì test chạy trên cấu hình bảo mật mặc định của Boot, mọi vai trò đều qua. Người đăng nhập trong test: dùng `@WithAppUser(username, role)`, hoặc `with(user(WithAppUserSecurityContextFactory.principal(…)))` khi vai trò là tham số. Không dùng `@WithMockUser` cho test render trang, vì navbar đọc `principal.fullName`. Mọi POST thêm `.with(csrf())`.
- Service có hai lớp test tên gần giống nhau: `LabSampleServiceTests` (đăng ký) và `LabSampleServiceTest` (đổi trạng thái).

## Quy ước
- Branch `feature/LT-xxx-mo-ta`. Conventional Commits, mô tả tiếng Việt, mã BR ở cuối dòng tiêu đề, ví dụ `feat(sample): nhận kiểm nghiệm và hủy mẫu có lý do (BR-03, BR-05)`.
- Javadoc tiếng Việt, nói lý do chọn cách làm, không mô tả lại cú pháp.

## Chưa có — đừng tưởng đã xong
- Chưa có quản lý tài khoản (tạo, khóa, đổi mật khẩu). Tài khoản chỉ đến từ `db/devdata/R__dev_seed.sql`, và file này chạy ở mọi môi trường có location `db/devdata` trong `spring.flyway.locations`. Khi có profile production, phải tách location này ra.

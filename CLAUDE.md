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
- `./mvnw spring-boot:run` → http://localhost:8080/samples (không có mapping cho `/`). CSDL dựng bằng `docker compose up -d`: PostgreSQL 18, cổng 5433, user/mật khẩu/CSDL đều là `labtrack`; đổi bằng `DB_URL`, `DB_USER`, `DB_PASSWORD`. Có Docker mà không muốn dựng compose: `./mvnw spring-boot:test-run` chạy `TestLabtrackApplication` với PostgreSQL của Testcontainers.
- Ba lớp test cần CSDL thật (`LabtrackApplicationTests`, `SampleCodeCounterMapperTests`, `StatusHistoryIntegrationTests`) dựng PostgreSQL bằng Testcontainers. Máy không có Docker thì bật profile `localdb` để tắt `TestcontainersConfiguration`, và trỏ `DB_URL` sang CSDL riêng cho test, đừng chạy test lên CSDL dev:
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
- Đổi trạng thái: bảng chuyển nằm ở `SampleStatus.canTransitionTo`. Service dựng một `StatusChange` rồi chạy `UPDATE … WHERE id = ? AND status = <trạng thái cũ>`. 0 dòng nghĩa là người khác vừa đổi trước → `CONCURRENT_UPDATE`, không phải lỗi hệ thống. UPDATE được 1 dòng thì chính `StatusChange` đó được ghi vào `sample_status_history` (BR-04), trong cùng transaction; đăng ký cũng ghi 1 dòng với `from_status` = null.

## Bẫy
- Đổi schema là viết migration MỚI `V<n+1>__mo_ta.sql`. Không sửa migration đã commit.
- Tên hằng `SampleStatus` và `SampleType` được ghi nguyên văn vào cột có `CHECK` liệt kê giá trị. Đổi tên hằng là phải viết migration cho mọi `CHECK` đó, và sửa cả template so chuỗi tên hằng (`fragments/layout.html`, `samples/detail.html`).
- `ck_lab_sample_due_date` ở V1 ghi cứng `'Asia/Ho_Chi_Minh'`. Đổi `labtrack.time-zone` là phải đổi cả ràng buộc này bằng migration.
- `<select>` bọc `INSERT/UPDATE … RETURNING` phải có `flushCache="true" useCache="false"`, nếu không lần gọi thứ hai trong cùng transaction nhận lại giá trị cũ từ cache.
- Boot 4 đổi chỗ so với Boot 3: dùng `@MockitoBean` (không phải `@MockBean`), `@WebMvcTest` nằm ở `org.springframework.boot.webmvc.test.autoconfigure`.
- Không dùng H2. Test chạy trên PostgreSQL thật.
- Thông báo lỗi tiếng Việt bị test so khớp nguyên văn. Sửa câu chữ là phải sửa cả test. Thông báo khi form nhận sai kiểu dữ liệu (`typeMismatch`) nằm ở `messages.properties`.
- `.mvn/jvm.config` ép stdout/stderr của Maven sang UTF-8. Trên Windows dùng locale không phải UTF-8 (ví dụ locale Nhật, MS932), thiếu nó thì tiếng Việt in ra thành `?`. Output có `?` hay ký tự lạ thay chữ có dấu là lỗi hiển thị, không phải dữ liệu sai. Chạy `java` bằng tay thì thêm `-Dstdout.encoding=UTF-8`.

## Test
- `@DisplayName` mở đầu bằng `TC-BRxx-yy`.
- Ba kiểu test: unit thuần (dựng bằng `new`, mock mapper, `Clock.fixed` múi giờ VN, thử cả mốc sau nửa đêm giờ VN khi UTC còn là hôm trước); `@WebMvcTest` + `@MockitoBean` cho controller; `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` + `@Transactional` khi cần CSDL thật.
- Service có hai lớp test tên gần giống nhau: `LabSampleServiceTests` (đăng ký) và `LabSampleServiceTest` (đổi trạng thái).

## Quy ước
- Branch `feature/LT-xxx-mo-ta`. Conventional Commits, mô tả tiếng Việt, mã BR ở cuối dòng tiêu đề, ví dụ `feat(sample): nhận kiểm nghiệm và hủy mẫu có lý do (BR-03, BR-05)`.
- Javadoc tiếng Việt, nói lý do chọn cách làm, không mô tả lại cú pháp.

## Chưa có — đừng tưởng đã xong
- Chưa có Spring Security: `SampleController.DEMO_USER` là người thực hiện hardcode, mọi endpoint đang mở, chưa có CSRF.

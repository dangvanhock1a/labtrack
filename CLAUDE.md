# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Quản lý mẫu kiểm nghiệm cho phòng QC dược. Ba tầng: Thymeleaf + Bootstrap → service → MyBatis → PostgreSQL 18.
Yêu cầu nghiệp vụ đánh mã `BR-xx` ở `docs/yeu-cau-nghiep-vu.md` mục 8 — task nhắc mã BR nào thì đọc mục đó trước khi code.

## Stack — không tự nâng hay hạ version
Java 21 · Spring Boot 4.1.1 · mybatis-spring-boot-starter 4.1.0 · Flyway · Thymeleaf · Bootstrap 5.3.8 (webjar)

## Lệnh
- `./mvnw -q test` · một lớp: `-Dtest=TenLop` · một method: `-Dtest='TenLop#tenMethod'` · đầy đủ: `./mvnw verify`
- `./mvnw spring-boot:run` → http://localhost:8080/samples (không có mapping cho `/`); CSDL dựng bằng `docker compose up -d`, cổng 5433
- Máy không có Docker: thêm `-Dspring.profiles.active=localdb` để tắt Testcontainers, cần PostgreSQL ở cổng 5433. Trỏ `DB_URL` sang CSDL riêng cho test (ví dụ `labtrack_test`), đừng chạy test lên CSDL dev. Quên profile thì hai test cần CSDL thật báo `Could not find a valid Docker environment`.

## Kiến trúc — bắt buộc
- Controller không gọi mapper. SQL chỉ nằm trong `src/main/resources/mapper/*.xml`.
- Quy tắc nghiệp vụ ở service, vi phạm thì ném `BusinessException(code, field, message)`; `field` là tên trường trên form, hoặc `null` nếu lỗi không thuộc trường nào.
- `GlobalControllerAdvice` biến mọi `BusinessException` chưa bắt thành flash `error` rồi redirect về trang chi tiết mẫu. Đừng thêm try/catch vào method mới. Riêng `SampleController.register` tự bắt, vì nó cần gắn lỗi vào `BindingResult` để hiện lại form kèm dữ liệu đã nhập.
- Thời gian lấy từ bean `Clock`, múi giờ đọc từ `labtrack.time-zone` chứ không theo JVM. Đường đọc nào trả thời điểm cũng phải đổi về múi giờ ứng dụng — xem `LabSampleService.toClockZone`.
- Quy tắc quan trọng bị chặn ở nhiều tầng một cách có chủ ý (form + service + `CHECK` của CSDL). Đừng bỏ lớp nào vì thấy lớp khác đã chặn.

## Bẫy
- Đổi schema là viết migration MỚI `V<n+1>__mo_ta.sql`. Không sửa migration đã commit.
- Tên hằng `SampleStatus` và `SampleType` được ghi nguyên văn vào cột có `CHECK` liệt kê giá trị. Đổi tên hằng là phải viết migration.
- Đổi trạng thái luôn chạy `UPDATE … WHERE id = ? AND status = <trạng thái cũ>`. 0 dòng nghĩa là người khác vừa đổi trước → `CONCURRENT_UPDATE`, không phải lỗi hệ thống.
- `<select>` bọc `INSERT/UPDATE … RETURNING` phải có `flushCache="true" useCache="false"`, nếu không lần gọi thứ hai trong cùng transaction nhận lại giá trị cũ từ cache.
- Boot 4 đổi chỗ so với Boot 3: dùng `@MockitoBean` (không phải `@MockBean`), `@WebMvcTest` nằm ở `org.springframework.boot.webmvc.test.autoconfigure`.
- Không dùng H2. Test chạy trên PostgreSQL thật.
- Thông báo lỗi tiếng Việt bị test so khớp nguyên văn. Sửa câu chữ là phải sửa cả test.

## Quy ước
- Branch `feature/LT-xxx-mo-ta`. Conventional Commits, mô tả tiếng Việt, mã BR ở cuối dòng tiêu đề.
- `@DisplayName` của test mở đầu bằng `TC-BRxx-yy`.
- Javadoc tiếng Việt, nói lý do chọn cách làm, không mô tả lại cú pháp.

## Chưa có — đừng tưởng đã xong
- BR-04 lịch sử trạng thái chưa làm: `StatusChange.reason` và `changedBy` hiện bị bỏ đi, `updateStatus` chỉ ghi `status` và `updated_at`. BR-05 vì thế mới xong một nửa — đã bắt buộc có lý do, chưa lưu lý do.
- Chưa có Spring Security: `SampleController.DEMO_USER` là người thực hiện hardcode, mọi endpoint đang mở, chưa có CSRF.
- `docs/cau-hoi-lam-ro.md` còn 10 câu hỏi chưa có đáp án (ví dụ mã mẫu lấy năm nào khi nhập lùi ngày nhận). Đừng tự quyết thay, hỏi người dùng.

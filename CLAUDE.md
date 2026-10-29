# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Quản lý mẫu kiểm nghiệm cho phòng QC dược. Ba tầng: Thymeleaf + Bootstrap → service → MyBatis → PostgreSQL 18.
Quy tắc riêng từng vùng code nằm ở `.claude/rules/*.md`, tự nạp khi đọc file khớp `paths`.

## Tài liệu nghiệp vụ
- `docs/yeu-cau-nghiep-vu.md`: quy tắc `BR-xx` ở mục 8, yêu cầu phi chức năng `NFR-xx` ở mục 10, kế hoạch increment ở mục 13 (BR nào làm ở increment nào, dùng migration số mấy). Task nhắc mã BR nào thì đọc mục đó trước khi code.
- `docs/cau-hoi-lam-ro.md`: mục 1 ghi, cho từng BR, tầng phải chặn và loại test cần có. Mục 2 là 10 câu hỏi chưa có đáp án (ví dụ mã mẫu lấy năm nào khi nhập lùi ngày nhận). Gặp đúng tình huống đó thì hỏi người dùng, đừng tự quyết.

## Stack — không tự nâng hay hạ version
Java 21 · Spring Boot 4.1.1 · mybatis-spring-boot-starter 4.1.0 · Flyway · Thymeleaf · Bootstrap 5.3.8 (webjar)
Không dùng H2: mọi test chạy trên PostgreSQL thật.

## Lệnh
- `./mvnw -q test` · một lớp `-Dtest=TenLop` · một method `-Dtest='TenLop#tenMethod'` · đầy đủ `./mvnw verify`. Không có linter hay formatter.
- `./mvnw spring-boot:run` → http://localhost:8080/. `/` là trang Tổng quan (BR-15), nơi tới sau khi đăng nhập.
- Tài khoản demo: 6 tài khoản trong `db/devdata/R__dev_seed.sql` (5 của LT-BRD mục 4 và `duyet2`), mật khẩu `Passw0rd!`.
- CSDL: PostgreSQL 18 cài trên máy (không dùng Docker), cổng 5433, user/mật khẩu `labtrack`.
  - Dev `labtrack`: ứng dụng dùng; đổi bằng `DB_URL`, `DB_USER`, `DB_PASSWORD`.
  - Test `labtrack_test`: mọi `@SpringBootTest` và E2E dùng; đổi bằng `TEST_DB_URL` (`src/test/resources/config/application.yml`). Đừng trỏ test vào CSDL dev: test đồng thời và E2E commit dữ liệu mà trigger V4 không cho xóa.
- E2E (`*IT.java`, gói `e2e`) chỉ chạy ở `./mvnw verify` (failsafe). Một lớp: `-Dit.test=SmokeIT`. Mặc định dùng Edge đã cài (`e2e.channel` trong `pom.xml`); đổi bằng `-De2e.channel=chrome`; `-De2e.headed=true` để xem trình duyệt.
  PowerShell 5.1 tách `-D` ở dấu chấm đầu tiên nếu không bọc nháy: `"-De2e.headed=true"`.
- Output có `?` thay chữ có dấu là lỗi hiển thị (locale Windows không UTF-8), không phải dữ liệu sai. `.mvn/jvm.config` đã ép UTF-8; chạy `java` tay thì thêm `-Dstdout.encoding=UTF-8`.

## Kiến trúc — bắt buộc
- `web` (controller, form) → `service` (quy tắc nghiệp vụ, transaction) → `persistence` (interface `@Mapper`). Controller không gọi mapper. SQL chỉ nằm trong `src/main/resources/mapper/*.xml`.
- Quy tắc quan trọng bị chặn ở nhiều tầng có chủ ý: form (Bean Validation) + service + `CHECK`/`UNIQUE` của CSDL. Đừng bỏ lớp nào vì thấy lớp khác đã chặn.
- Vi phạm quy tắc ở service → ném `BusinessException(code, field, message)`; tầng web đổi nó thành thông báo cho người dùng.
- Thời gian chỉ lấy từ bean `Clock` (`ClockConfig`), múi giờ đọc từ `labtrack.time-zone`, không theo JVM.
- Phân quyền (BR-12) chỉ cưỡng chế ở `SecurityConfig` theo URL + HTTP method. Endpoint mới chưa có luật thì bị 403 (`denyAll`).
- Controller truyền người thực hiện xuống service dưới dạng chuỗi `principal.getName()`. Package `service` không import Spring Security; chỉ `config` và `security` dùng nó.
- Thông báo lỗi tiếng Việt bị test so khớp nguyên văn: sửa câu chữ là phải sửa cả test.

## Bẫy ở mọi loại việc
- Đổi schema là viết migration MỚI `V<n+1>__mo_ta.sql`. Không sửa migration đã commit.
- Tên hằng `SampleStatus`, `SampleType`, `SamplePriority`, `StorageCondition`, `ResultStatus`, `Verdict`, `ResultAction`, `UserRole` được ghi nguyên văn vào CSDL (`CHECK`, trigger V4) và template. Thêm, đổi hay xóa hằng → migration mới + sửa template.

## Quy ước
- Branch `feature/LT-xxx-mo-ta`. Conventional Commits, mô tả tiếng Việt, mã BR ở cuối dòng tiêu đề, ví dụ `feat(sample): nhận kiểm nghiệm và hủy mẫu có lý do (BR-03, BR-05)`.
- Javadoc tiếng Việt, nói lý do chọn cách làm, không mô tả lại cú pháp.

## Chưa có — đừng tưởng đã xong
- Chưa có quản lý tài khoản (tạo, khóa, đổi mật khẩu). Tài khoản chỉ đến từ `db/devdata/R__dev_seed.sql`, và file này chạy ở mọi môi trường có location `db/devdata` trong `spring.flyway.locations`. Khi có profile production, phải tách location này ra.

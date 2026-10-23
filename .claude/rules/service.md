---
paths:
  - "src/main/java/**/service/**/*.java"
---
# Tầng service
- `BusinessException(code, field, message)`: `code` là UPPER_SNAKE ổn định (`CONCURRENT_UPDATE`, `REASON_REQUIRED`); `field` là tên property trên form (`reason`, `dueDate`) hoặc `null`.
- Overload hai tham số là `(field, message)`. Sai: `new BusinessException("REASON_REQUIRED", "Lý do…")`, vì mã lỗi bị hiểu thành tên trường. Đúng: `new BusinessException("REASON_REQUIRED", "reason", "Lý do…")`.
- Giờ hiện tại: `OffsetDateTime.now(clock)`, `LocalDate.now(clock)`. Sai: `LocalDateTime.now()`, `ZoneId.systemDefault()`.
- Đường đọc nào trả thời điểm cũng phải qua `toClockZone` (pgjdbc trả `TIMESTAMPTZ` theo UTC), như `LabSampleService.toClockZone`.
- Method public có ghi CSDL được đánh dấu `@Transactional`.
- Thao tác ghi mới đụng tới mẫu: gọi `sampleMapper.lockById(id)` trước mọi kiểm tra có đọc CSDL (như `TestResultService.lockSample`), để không deadlock. `changeStatus` không khóa trước; nó dựa vào `UPDATE … AND status = <cũ>`.
- `SampleController` gửi chuỗi trống xuống dưới dạng `null`, nhưng service vẫn tự xử lý `null` và khoảng trắng (`reason != null ? reason.trim() : ""`), vì test và E2E gọi thẳng service.
- Ngưỡng dùng chung chỉ khai báo một lần (`LabSampleService.MIN_REASON_LENGTH`/`MAX_REASON_LENGTH`, `TestResultService` dùng lại); không chép số 5/500.

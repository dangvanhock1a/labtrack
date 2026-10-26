---
paths:
  - "src/main/java/**/service/{LabSampleService,SampleCodeGenerator,InvalidStatusTransitionException}.java"
  - "src/main/java/**/domain/{LabSample,SampleStatus,StatusChange,StatusHistory}.java"
  - "src/main/resources/mapper/{LabSampleMapper,StatusHistoryMapper,SampleCodeCounterMapper}.xml"
---
# Vòng đời mẫu (BR-01..BR-05)
- Mã mẫu `LS-YYYY-NNNNN` (`ck_lab_sample_code`): YYYY là năm theo giờ VN, NNNNN là bộ đếm của năm đó trong `sample_code_counter`.
- Không sinh mã bằng `MAX(code) + 1` hay `count(*)`; chỉ qua `SampleCodeGenerator` (`INSERT … ON CONFLICT (year) DO UPDATE … RETURNING`).
- `SampleCodeGenerator` có `Propagation.MANDATORY`: chỉ gọi trong method `@Transactional` của service, để bộ đếm và mẫu cùng commit hoặc cùng rollback; gọi ngoài transaction là lỗi.
- Nhập lùi ngày nhận sang năm trước thì mã lấy năm nào là câu hỏi còn mở ở mục 2 `docs/cau-hoi-lam-ro.md`: hỏi người dùng, đừng tự quyết.
- Bảng chuyển trạng thái chỉ nằm ở `SampleStatus.canTransitionTo`; không viết `if (status == …)` để cho phép chuyển ở chỗ khác.
- Đổi trạng thái: dựng `StatusChange`, chạy `updateStatus` (`UPDATE … WHERE id = ? AND status = <cũ>`). Được 0 dòng → `BusinessException("CONCURRENT_UPDATE", null, …)`, không phải lỗi hệ thống.
- Được 1 dòng → ghi chính `StatusChange` đó vào `sample_status_history` trong cùng transaction (BR-04); không dựng lại thời điểm hay người thực hiện.
- Đăng ký ghi 1 dòng lịch sử với `from_status = null`, `to_status = RECEIVED` (`ck_status_history_new`).
- Kết luận (BR-11) khác `changeStatus`: khóa mẫu bằng `lockById` trước khi đọc kết quả (SPEC-004 R14). Điều kiện và kết luận chỉ tính ở `ConclusionReadiness`; ba cột `status`, `conclusion`, `completed_at` đổi trong một câu `conclude` (`ck_lab_sample_completed`, V5).
<<<<<<< HEAD
- `conclude` nhận `expectedVerdict` (trường ẩn của hộp thoại): tính lại sau khi khóa ra khác thì `CONCLUSION_CHANGED`, không lưu. Sai: bỏ tham số này rồi lưu kết luận tính lại, vì người duyệt sẽ Hoàn thành một kết luận chưa thấy.
=======
>>>>>>> 944256a4a47413ddbb22dac0b1aa09b84e28ec6d
- Lý do hủy dài 5–500 ký tự sau khi cắt khoảng trắng, chặn ở `<textarea minlength="5" maxlength="500" required>`, ở service (`REASON_REQUIRED`) và ở CSDL (`ck_status_history_reason`, `ck_status_history_cancel_reason`).

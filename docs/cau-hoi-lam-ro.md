# Câu hỏi làm rõ yêu cầu nghiệp vụ LabTrack

| Mục | Nội dung |
|---|---|
| Nguồn | [Yêu cầu nghiệp vụ](yeu-cau-nghiep-vu.md) (LT-BRD, phiên bản 1.0) |
| Ngày lập | 09/10/2026 |
| Phạm vi | Chỉ dựa trên LT-BRD. Các tài liệu 02–07 và hình `hinh/*.svg` được LT-BRD liên kết tới nhưng chưa có trong repo |

## 1. BR, tầng cưỡng chế và loại test

Ký hiệu loại test:

- **U**: unit test (JUnit + Mockito)
- **W**: test tầng web (MockMvc + spring-security-test)
- **I**: test tích hợp với PostgreSQL thật (CSDL `labtrack_test`)
- **DB**: ghi thẳng bằng SQL để kiểm tra ràng buộc
- **CC**: chạy đồng thời
- **TZ**: múi giờ (JVM chạy UTC, `Clock` cố định)
- **E2E**: Playwright

Dấu `*` nghĩa là LT-BRD không ghi dòng "Cưỡng chế ở" cho BR đó; tầng được suy ra từ phần mô tả.

| BR | Tầng cưỡng chế | Cần test |
|---|---|---|
| 01 Mã mẫu | Service (`INSERT … ON CONFLICT … RETURNING`) + DB (`UNIQUE`, `CHECK` định dạng) | **U** định dạng, đệm số 0 · **CC** nhiều luồng đăng ký cùng lúc vẫn không trùng · **TZ** chuyển năm (23:59 ngày 31/12 giờ VN khác năm với UTC), số đếm quay về `00001` · **I** tới 99.999 thì báo hết dải mã · **DB** mã sai định dạng hoặc trùng bị chặn |
| 02 Dữ liệu đăng ký | Form + Service (múi giờ) + DB (`NOT NULL`, `CHECK`) | **U** biên độ dài 0/1/200/201 và 50/51; số lô có dấu hoặc khoảng trắng; giá trị mặc định · **TZ** thời điểm nhận ở tương lai; hạn trả trùng ngày nhận hoặc sớm hơn 1 ngày · **W** thông báo lỗi tiếng Việt · **DB** giá trị NULL hoặc ngoài miền bị chặn |
| 03 Luồng trạng thái | Service (bảng chuyển `SampleStatus`) + `UPDATE … WHERE status=<cũ>` + DB (`CHECK`) | **U** bảng 4×4 tham số hóa, đủ mọi cặp hợp lệ và không hợp lệ · **CC** 2 giao dịch đổi cùng lúc thì 1 thành công, 1 nhận "Mẫu vừa được người khác cập nhật…" · **DB** trạng thái lạ bị chặn |
| 04 Lịch sử trạng thái | Service, cùng transaction* | **I** đăng ký sinh đúng 1 dòng *Tạo mới → Đã tiếp nhận*; mỗi lần chuyển sinh đúng 1 dòng, đủ trường; chuyển thất bại thì không có dòng nào (rollback) |
| 05 Hủy mẫu | Form + Service* | **U** lý do dài 4/5/500/501 ký tự hoặc chỉ có khoảng trắng; không hủy được mẫu đã kết thúc · **I** lý do được lưu vào lịch sử · **W** chỉ Tiếp nhận và Người duyệt được hủy |
| 06 Điều kiện nhập kết quả | Form + Service + DB (unique partial index) | **U** mẫu không ở *Đang KN*; chỉ tiêu ngừng dùng; giá trị âm; 9 chữ số phần nguyên hoặc 5 chữ số thập phân · **DB/CC** chặn 2 kết quả hiệu lực cho cùng một chỉ tiêu, kể cả khi insert đồng thời; vẫn nhập lại được sau khi bị từ chối |
| 07 Đạt/Không đạt | Service* (tự tính) | **U** tham số hóa: đúng biên, sát dưới và sát trên biên, biên trống (LOD, DISSOLUTION), đủ 5 chỉ tiêu của V4 · **W** request giả mạo có gửi kèm kết quả đánh giá thì phần đó bị bỏ qua |
| 08 Nguyên tắc 4 mắt | UI (nút vô hiệu) + Service + DB (`ck_four_eyes`) | **U** cùng một người thì báo lỗi; kết quả không ở *Chờ duyệt* thì báo "đã được xử lý"; mẫu không ở *Đang KN* · **DB** `UPDATE` thẳng với người duyệt = người nhập bị chặn · **W** POST thẳng, bỏ qua giao diện vẫn bị chặn · **CC** 2 người duyệt cùng lúc · **E2E** nút bị vô hiệu |
| 09 Từ chối kết quả | Form + Service*; không xóa cứng (NFR-03) | **U** lý do 5–500 ký tự · **I** bản bị từ chối vẫn còn; nhập lại tạo bản ghi mới; bản cũ hết hiệu lực · **E2E** bản cũ bị gạch ngang và hiện lý do |
| 10 Audit trail kết quả | Service, cùng transaction*; tài liệu chưa nói cách cưỡng chế "chỉ thêm" | **I** mỗi thao tác nhập, duyệt hoặc từ chối sinh đúng 1 dòng; thao tác thất bại thì không có dòng nào · **DB** `UPDATE`/`DELETE` vào `result_audit` bị chặn (nếu quyết định chặn ở DB) |
| 11 Kết luận mẫu | Service* | **U** chưa có kết quả nào; còn kết quả *Chờ duyệt*; mẫu không ở *Đang KN*; tất cả Đạt thì Đạt; có 1 Không đạt thì Không đạt; không tính kết quả bị từ chối · **I** chuyển sang *Hoàn thành*, lưu kết luận, thời điểm và dòng lịch sử |
| 12 Phân quyền | Spring Security theo URL; giao diện chỉ ẩn nút | **W** ma trận vai trò × endpoint: chưa đăng nhập thì về trang đăng nhập, thiếu quyền thì 403, kể cả khi gõ thẳng URL; thiếu CSRF thì 403 · **E2E** nút ẩn đúng theo vai trò |
| 13 Tra cứu | Mapper SQL + Service* | **I** không phân biệt hoa thường; `%` và `_` hiểu theo nghĩa đen; các bộ lọc kết hợp AND; sắp xếp đúng thứ tự; 20 dòng/trang và nhãn "a–b / N" (cả trang cuối và khi N = 0); tiến độ x/y; mỗi trang ≤ 3 câu SQL (NFR-01) |
| 14 Quá hạn | Service/SQL theo "hôm nay" giờ VN* | **TZ** hạn trả là hôm nay thì chưa quá hạn, là hôm qua thì quá hạn; thử lúc 00:30 giờ VN khi JVM chạy UTC · **U** mẫu đã kết thúc không bao giờ quá hạn · **E2E** nhãn đỏ ở cả 3 màn hình |
| 15 Tổng quan, TAT | Service/SQL* | **I** trạng thái không có mẫu nào vẫn hiện 0; biên của cửa sổ 30 ngày; TAT tính bằng giờ, làm tròn 1 chữ số thập phân; tối đa 10 mẫu cần chú ý; trường hợp chưa có mẫu nào hoàn thành |
| 16 Xuất CSV | Spring Security + Service* | **U** thêm `'` vào ô bắt đầu bằng `= + - @`; có BOM; ô chứa dấu phẩy, ngoặc kép hoặc xuống dòng · **W** 403 cho Tiếp nhận và Kiểm nghiệm viên; tên file theo ngày VN; xuất đúng bộ lọc đang xem; giới hạn 10.000 dòng |
| 17 Ghi chú | Form + Service + DB (qua BR-02) | **U** để trống, 1000 và 1001 ký tự · **W/E2E** hiển thị ở trang chi tiết |
| 18 Bảo quản | Form + Service + DB (qua BR-02) | **U** chỉ nhận 3 giá trị, mặc định là Nhiệt độ phòng · **DB** `CHECK` · **W** hiển thị ở chi tiết và trong CSV |

## 2. Câu hỏi cần làm rõ trước khi code

1. **Năm trong mã mẫu (BR-01 × BR-02, chưa rõ).** "Năm nhận" là năm của trường *Thời điểm nhận* (có thể lùi về quá khứ) hay năm lúc bấm Lưu? Ví dụ: ngày 02/01/2027 đăng ký một mẫu nhận lúc 31/12/2026 thì mã là `LS-2026-…` hay `LS-2027-…`?
2. **Hạn trả là ngày hay thời điểm (thuật ngữ × BR-14 × NFR-04, hơi mâu thuẫn).** Phần thuật ngữ nói "hạn trả đã qua", còn BR-14 nói "trước ngày hôm nay". Cần xác nhận ba điều: hạn trả có kiểu ngày; hạn trả là hôm nay thì chưa quá hạn; và "ngày nhận + 7" tính theo ngày lịch giờ VN.
3. **TAT tính từ mốc nào (BR-15 × thuật ngữ, chưa rõ).** "Lúc nhận mẫu" là *Thời điểm nhận* do người dùng nhập, hay lúc bấm *Nhận kiểm nghiệm*? "30 ngày" là 30×24 giờ hay 30 ngày lịch theo giờ VN? Khi chưa có mẫu nào hoàn thành thì hiển thị gì?
4. **Mẫu có thể kẹt vì nguyên tắc 4 mắt (BR-08 × mục 4, trường hợp biên).** Người duyệt được làm "mọi việc của kiểm nghiệm viên", nhưng dữ liệu demo chỉ có một người duyệt là `duyet`. Kết quả do `duyet` tự nhập sẽ không ai duyệt được, và mẫu kẹt ở *Đang kiểm nghiệm*. Chấp nhận như vậy, thêm người duyệt thứ hai, hay không cho người duyệt nhập kết quả?
5. **Tiếp nhận được hủy đến đâu (mục 4 × BR-05 × ma trận, mâu thuẫn).** Mục 4 nói Tiếp nhận hủy "mẫu gửi nhầm", nhưng ma trận phân quyền cho phép hủy cả mẫu *Đang kiểm nghiệm* đã có kết quả. Với kết quả *Chờ duyệt* của mẫu đã hủy, BR-08 không cho xử lý tiếp, vậy chúng ở trạng thái nào?
6. **Có sửa được mẫu sau khi đăng ký không (chưa rõ).** Tài liệu chỉ nói *mã mẫu* không sửa được và không có use case sửa mẫu. Nhập sai số lô hay hạn trả thì chỉ còn cách hủy rồi đăng ký lại phải không? Nếu được sửa thì có phải ghi lịch sử không?
7. **Phân công mẫu (BR-03, BR-06, BR-09, chưa rõ).** Bấm *Nhận kiểm nghiệm* có gán mẫu cho người bấm không? `kiemnghiem2` có được nhập kết quả cho mẫu `kiemnghiem1` đã nhận không? Khi nhập lại kết quả bị từ chối, có bắt buộc là người đã nhập lần đầu không?
8. **"Chỉ tiêu đang được dùng" và bộ chỉ tiêu bắt buộc (BR-06 × BR-11, chưa rõ).** "Đang được dùng" là một cờ trong danh mục, hay là chỉ tiêu được chỉ định cho mẫu? Tài liệu không quy định bộ chỉ tiêu bắt buộc, nên một mẫu chỉ có kết quả pH Đạt vẫn kết luận Đạt được. Nghiệp vụ có muốn như vậy không?
9. **Làm tròn và tiêu chuẩn áp dụng (BR-07 × BR-06, trường hợp biên).** Giá trị đo được so nguyên văn với tiêu chuẩn, hay làm tròn theo số chữ số của tiêu chuẩn như quy ước dược điển? Ví dụ ASSAY 105.04: so nguyên văn thì Không đạt, làm tròn thành 105.0 thì Đạt. Tiêu chuẩn có được lưu kèm vào kết quả không, hay luôn đọc từ danh mục, kể cả khi một migration sau này đổi tiêu chuẩn?
10. **CSV vượt 10.000 dòng và số lô bắt đầu bằng `-` (BR-16 × BR-02, trường hợp biên).** Nếu bộ lọc trả về hơn 10.000 mẫu thì cắt bớt rồi báo người dùng, hay từ chối xuất? Ngoài ra BR-02 cho phép số lô bắt đầu bằng `-` hoặc `/`, nên `-2601` sẽ thành `'-2601` trong CSV. Chấp nhận như vậy, hay cấm số lô bắt đầu bằng hai ký tự này?

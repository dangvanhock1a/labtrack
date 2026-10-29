# 01. Yêu cầu nghiệp vụ: LabTrack, hệ thống quản lý mẫu kiểm nghiệm

| Mục | Nội dung |
|---|---|
| Mã tài liệu | LT-BRD |
| Phiên bản | 1.0 (07/10/2026) |
| Dùng cho | Dự án thực hành trong lộ trình tự học Claude Code |
| Dữ liệu | **Chỉ dữ liệu giả.** Mọi tên người, sản phẩm, mã mẫu trong tài liệu và code đều là hư cấu |
| Tài liệu liên quan | [02 Use case](02-use-case.md) · [03 Kiến trúc](03-kien-truc.md) · [04 CSDL](04-thiet-ke-csdl.md) · [05 Màn hình](05-thiet-ke-man-hinh.md) · [06 Test case](06-test-case.md) · [07 Nghiệm thu](07-nghiem-thu.md) |

> **Cách đọc tài liệu này khi tự học.** Mỗi quy tắc có mã `BR-xx`. Bạn sẽ đưa mã này vào prompt cho Claude ("implement BR-05"), vào tên test (`TC-BR05-01`) và vào commit. Nhờ vậy, từ một dòng yêu cầu bạn lần được tới code, test và bằng chứng. Đây là kỹ năng "truy vết" mà mọi dự án thật đều cần.

---

## 1. Bối cảnh và vấn đề

Phòng Kiểm nghiệm chất lượng (QC) của một công ty dược nhận mỗi ngày vài chục mẫu: nguyên liệu, bán thành phẩm, thành phẩm và mẫu theo dõi độ ổn định. Hiện mẫu được quản lý bằng Excel và sổ tay, dẫn tới:

| # | Vấn đề hiện tại | Hậu quả |
|---|---|---|
| P1 | Mã mẫu đánh tay, đôi khi **trùng** | Kết quả gắn nhầm mẫu |
| P2 | Không biết mẫu đang ở bước nào | Gọi điện hỏi nhau, trễ hạn |
| P3 | Người nhập kết quả cũng tự "duyệt" | Vi phạm nguyên tắc 4 mắt của GMP |
| P4 | Sửa số liệu trên Excel không để lại dấu vết | Không đáp ứng yêu cầu toàn vẹn dữ liệu (ALCOA+) khi thanh tra |
| P5 | Không ai cảnh báo mẫu sắp/đã quá hạn | Sản xuất phải chờ, không đo được thời gian quay vòng (TAT) |

## 2. Mục tiêu và chỉ số thành công

| Mã | Mục tiêu | Đo bằng |
|---|---|---|
| M1 | Mọi mẫu có mã duy nhất, sinh tự động | Ràng buộc UNIQUE + test sinh mã đồng thời |
| M2 | Không kết quả nào được duyệt bởi chính người nhập | Chặn ở cả tầng nghiệp vụ và CHECK của CSDL |
| M3 | Mọi thay đổi trạng thái và thao tác trên kết quả đều có dấu vết | Bảng lịch sử + audit trail, chỉ thêm, không sửa/xóa |
| M4 | Thấy ngay mẫu quá hạn và mẫu khẩn | Màn hình tổng quan, bộ lọc "Quá hạn" |
| M5 | Đo được TAT trung bình 30 ngày | Chỉ số trên màn hình tổng quan |

## 3. Phạm vi

**Trong phạm vi (phiên bản 1.0):** đăng nhập theo vai trò; đăng ký mẫu; theo dõi trạng thái; nhập, duyệt, từ chối kết quả theo nguyên tắc 4 mắt; kết luận mẫu; lịch sử trạng thái và audit trail; tra cứu, lọc, phân trang; cảnh báo quá hạn; màn hình tổng quan; xuất CSV.

**Ngoài phạm vi** (ghi rõ để Claude không "làm thêm" khi bạn giao việc):

| Ngoài phạm vi | Thay thế trong 1.0 |
|---|---|
| Màn hình quản lý người dùng | Người dùng demo tạo bằng script dữ liệu giả (`R__dev_seed.sql`) |
| Màn hình quản lý danh mục chỉ tiêu | 5 chỉ tiêu nạp sẵn trong migration `V4` |
| Chữ ký điện tử theo 21 CFR Part 11, in nhãn/mã vạch | Không có |
| Gửi email, thông báo đẩy | Không có |
| API REST cho hệ thống khác, ứng dụng di động | Không có |
| Đa ngôn ngữ | Giao diện tiếng Việt |

## 4. Người dùng và vai trò

| Vai trò (mã) | Tên hiển thị | Việc chính | Tài khoản demo |
|---|---|---|---|
| `RECEPTIONIST` | Tiếp nhận mẫu | Đăng ký mẫu, hủy mẫu gửi nhầm | `tiepnhan` |
| `ANALYST` | Kiểm nghiệm viên | Nhận mẫu vào kiểm nghiệm, nhập kết quả | `kiemnghiem1`, `kiemnghiem2` |
| `REVIEWER` | Người duyệt (QA) | Mọi việc của kiểm nghiệm viên + duyệt/từ chối kết quả, kết luận mẫu, hủy mẫu, xuất CSV | `duyet` |
| `MANAGER` | Quản lý | Xem tổng quan, tra cứu, xuất CSV | `quanly` |

Mật khẩu mọi tài khoản demo: `Passw0rd!` (chỉ môi trường dev/test).

![Sơ đồ use case](../hinh/use-case.svg)

## 5. Thuật ngữ

| Thuật ngữ | Nghĩa trong LabTrack |
|---|---|
| Mẫu (sample) | Một lượng vật liệu gửi tới phòng QC để kiểm nghiệm, gắn với một sản phẩm và một số lô |
| Số lô (batch no.) | Mã lô sản xuất của sản phẩm, ví dụ `PA-2601` |
| Chỉ tiêu (test parameter) | Một phép thử, ví dụ "Định lượng hoạt chất", "pH" |
| Tiêu chuẩn (spec) | Khoảng giá trị chấp nhận của chỉ tiêu, ví dụ 95–105 % |
| Kết quả (test result) | Giá trị đo của một chỉ tiêu trên một mẫu, kèm đánh giá Đạt/Không đạt |
| Nguyên tắc 4 mắt | Người duyệt kết quả phải khác người nhập kết quả |
| Kết luận mẫu | Đánh giá cuối cùng của mẫu: **Đạt** khi mọi kết quả hiệu lực đều Đạt |
| Quá hạn | Mẫu chưa hoàn thành/hủy mà hạn trả kết quả đã qua |
| TAT (turnaround time) | Thời gian từ lúc nhận mẫu tới lúc kết luận mẫu |
| Audit trail | Nhật ký chỉ-thêm ghi ai, làm gì, lúc nào trên kết quả |
| Kết quả hiệu lực | Kết quả chưa bị từ chối (trạng thái Chờ duyệt hoặc Đã duyệt) |

## 6. Quy trình nghiệp vụ tổng quát

1. **Tiếp nhận** đăng ký mẫu → hệ thống sinh mã `LS-2026-00001`, trạng thái *Đã tiếp nhận*.
2. **Kiểm nghiệm viên** bấm *Nhận kiểm nghiệm* → trạng thái *Đang kiểm nghiệm*.
3. Kiểm nghiệm viên nhập kết quả từng chỉ tiêu → hệ thống tự đánh giá Đạt/Không đạt theo tiêu chuẩn.
4. **Người duyệt** (khác người nhập) duyệt hoặc từ chối từng kết quả. Kết quả bị từ chối phải được nhập lại.
5. Khi mọi kết quả hiệu lực đã duyệt, người duyệt *Kết luận mẫu* → trạng thái *Hoàn thành* kèm kết luận Đạt/Không đạt.
6. Ở bước 1–4, mẫu có thể bị *Hủy* (bắt buộc ghi lý do).
7. Mọi bước trên được ghi lịch sử/audit. **Quản lý** theo dõi trên màn hình tổng quan và xuất CSV.

![Máy trạng thái mẫu và kết quả](../hinh/trang-thai.svg)

## 7. Yêu cầu chức năng (use case)

Chi tiết từng luồng (tiền điều kiện, luồng chính, luồng thay thế) ở [02-use-case.md](02-use-case.md).

| Mã | Use case | Vai trò | Quy tắc liên quan |
|---|---|---|---|
| UC-01 | Đăng nhập / đăng xuất | Tất cả | BR-12, NFR-02 |
| UC-02 | Đăng ký mẫu | Tiếp nhận | BR-01, BR-02, BR-04, BR-17, BR-18 |
| UC-03 | Nhận mẫu vào kiểm nghiệm | Kiểm nghiệm viên, Người duyệt | BR-03, BR-04 |
| UC-04 | Nhập kết quả | Kiểm nghiệm viên, Người duyệt | BR-06, BR-07, BR-10 |
| UC-05 | Duyệt kết quả | Người duyệt | BR-08, BR-10 |
| UC-06 | Từ chối kết quả | Người duyệt | BR-08, BR-09, BR-10 |
| UC-07 | Kết luận mẫu | Người duyệt | BR-11, BR-04 |
| UC-08 | Xem tổng quan | Tất cả | BR-14, BR-15 |
| UC-09 | Tra cứu danh sách mẫu | Tất cả | BR-13, BR-14 |
| UC-10 | Xuất CSV | Người duyệt, Quản lý | BR-16 |
| UC-11 | Hủy mẫu | Tiếp nhận, Người duyệt | BR-03, BR-05, BR-04 |
| UC-12 | Xem chi tiết mẫu | Tất cả | BR-04, BR-10, BR-14 |

## 8. Quy tắc nghiệp vụ

Cột **Cưỡng chế ở** cho biết tầng nào chặn vi phạm: *Form* = kiểm tra định dạng ở tầng trình bày, *Service* = tầng nghiệp vụ, *DB* = ràng buộc CSDL. Quy tắc quan trọng được chặn ở nhiều tầng ("phòng thủ nhiều lớp"). Cột **Lab** là bài lab bạn hiện thực quy tắc đó.

### BR-01. Mã mẫu
- Định dạng `LS-YYYY-NNNNN`: `YYYY` là năm nhận theo giờ Việt Nam, `NNNNN` là số thứ tự trong năm, đệm số 0, bắt đầu lại từ `00001` mỗi năm.
- Mã do hệ thống sinh, **không trùng kể cả khi hai người đăng ký cùng lúc**, không sửa được.
- Vượt 99.999 mẫu/năm: báo lỗi "Đã hết dải mã mẫu của năm …".
- Cưỡng chế ở: Service (sinh mã nguyên tử bằng `INSERT … ON CONFLICT … RETURNING`) + DB (`UNIQUE`, `CHECK` định dạng). Lab 03.

### BR-02. Dữ liệu khi đăng ký mẫu
| Trường | Bắt buộc | Ràng buộc | Thông báo lỗi |
|---|---|---|---|
| Tên sản phẩm | Có | 1–200 ký tự | "Vui lòng nhập tên sản phẩm" |
| Số lô | Có | 1–50 ký tự, chỉ chữ không dấu, số, `-`, `/` | "Số lô chỉ gồm chữ không dấu, số, dấu - và /" |
| Loại mẫu | Có | Nguyên liệu / Bán thành phẩm / Thành phẩm / Mẫu độ ổn định | "Vui lòng chọn loại mẫu" |
| Mức ưu tiên | Có (mặc định Bình thường) | Thấp / Bình thường / Khẩn | (BR-13) |
| Điều kiện bảo quản | Có (mặc định Nhiệt độ phòng) | xem BR-18 | |
| Thời điểm nhận | Có (mặc định: bây giờ) | Không ở tương lai (theo giờ Việt Nam) | "Thời điểm nhận mẫu không được ở tương lai" |
| Hạn trả kết quả | Có (mặc định: ngày nhận + 7) | Không trước ngày nhận | "Hạn trả kết quả phải từ ngày nhận mẫu trở đi" |
| Ghi chú | Không | Tối đa 1000 ký tự | xem BR-17 |

Cưỡng chế ở: Form (định dạng) + Service (thời điểm/hạn trả theo múi giờ cấu hình) + DB (`NOT NULL`, `CHECK`). Lab 03.

### BR-03. Luồng trạng thái mẫu
Chỉ cho phép: *Đã tiếp nhận → Đang kiểm nghiệm*, *Đã tiếp nhận → Đã hủy*, *Đang kiểm nghiệm → Hoàn thành*, *Đang kiểm nghiệm → Đã hủy*. *Hoàn thành* và *Đã hủy* là trạng thái kết thúc. Chuyển sai: "Không thể chuyển mẫu từ "X" sang "Y"". Nếu người khác vừa đổi trạng thái cùng lúc: "Mẫu vừa được người khác cập nhật, hãy tải lại trang".
Cưỡng chế ở: Service (bảng chuyển trong `SampleStatus`) + câu `UPDATE … WHERE status = <trạng thái cũ>` + DB (`CHECK` giá trị). Lab 04.

### BR-04. Lịch sử trạng thái
Mỗi lần đổi trạng thái (kể cả lúc đăng ký: *Tạo mới → Đã tiếp nhận*) ghi đúng 1 dòng: từ, đến, người thực hiện, thời điểm, lý do (nếu có), **trong cùng transaction** với việc đổi trạng thái. Chuyển trạng thái thất bại thì không có dòng lịch sử. Lab 05.

### BR-05. Hủy mẫu
Hủy được khi mẫu chưa kết thúc. **Bắt buộc lý do 5–500 ký tự**, lưu vào lịch sử. Thiếu lý do: "Lý do phải dài từ 5 đến 500 ký tự". Lab 04.

### BR-06. Điều kiện nhập kết quả
- Chỉ nhập khi mẫu *Đang kiểm nghiệm*: "Chỉ nhập kết quả cho mẫu đang kiểm nghiệm".
- Chỉ tiêu phải đang được dùng.
- Mỗi chỉ tiêu trên một mẫu chỉ có **1 kết quả hiệu lực**: "Chỉ tiêu "…" đã có kết quả đang hiệu lực".
- Giá trị ≥ 0, tối đa 8 chữ số phần nguyên và 4 chữ số thập phân.
- Cưỡng chế ở: Form + Service + DB (unique partial index). Lab 07.

### BR-07. Đánh giá Đạt/Không đạt
Kết quả **Đạt** khi `spec_min ≤ giá trị ≤ spec_max` (tính cả biên; biên trống = không giới hạn phía đó), ngược lại **Không đạt**. Hệ thống tự đánh giá lúc nhập, người dùng không chọn tay. Lab 07.

### BR-08. Nguyên tắc 4 mắt
Người duyệt/từ chối **phải khác** người nhập kết quả: "Nguyên tắc 4 mắt: bạn không được duyệt hoặc từ chối kết quả do chính mình nhập". Chỉ xử lý kết quả *Chờ duyệt* của mẫu *Đang kiểm nghiệm*; kết quả đã xử lý: "Kết quả này đã được xử lý trước đó". Trên giao diện, nút Duyệt/Từ chối bị vô hiệu với kết quả của chính mình.
Cưỡng chế ở: Giao diện (nút vô hiệu) + Service + DB (`CHECK ck_four_eyes`). Lab 07.

### BR-09. Từ chối kết quả
Bắt buộc lý do 5–500 ký tự. Kết quả bị từ chối giữ nguyên trong CSDL (gạch ngang trên giao diện, hiện lý do); kiểm nghiệm viên nhập lại thành **bản ghi mới**. Lab 07.

### BR-10. Audit trail kết quả
Mỗi thao tác nhập / duyệt / từ chối ghi 1 dòng `result_audit` (thao tác, người, thời điểm, chi tiết) **trong cùng transaction**. Audit trail chỉ thêm, không sửa, không xóa. Lab 07.

### BR-11. Kết luận mẫu
Chỉ kết luận khi mẫu *Đang kiểm nghiệm*, có **ít nhất 1 kết quả hiệu lực** ("Mẫu chưa có kết quả nào nên chưa thể kết luận") và **mọi kết quả hiệu lực đã được duyệt** ("Còn kết quả chưa được duyệt nên chưa thể kết luận"). Kết luận **Đạt** khi mọi kết quả hiệu lực Đạt, ngược lại **Không đạt**. Mẫu chuyển *Hoàn thành*, lưu kết luận và thời điểm hoàn thành. Lab 10 (ticket LT-102).

### BR-12. Phân quyền
Xem ma trận ở mục 9. Chưa đăng nhập → chuyển tới trang đăng nhập. Không đủ quyền → trang 403, kể cả khi gõ thẳng URL. Cưỡng chế ở: Spring Security theo URL; giao diện chỉ ẩn nút để dễ dùng. Lab 06.

### BR-13. Tra cứu danh sách, mức ưu tiên
- Mức ưu tiên: Thấp / Bình thường / Khẩn, mặc định Bình thường.
- Tìm theo từ khóa trên mã mẫu, tên sản phẩm, số lô; **không phân biệt hoa thường**; ký tự `%` và `_` được hiểu là ký tự thường.
- Lọc theo trạng thái, ưu tiên, loại mẫu, chỉ mẫu quá hạn. Các bộ lọc kết hợp với nhau (AND).
- Sắp xếp: Khẩn trước, rồi Bình thường, rồi Thấp; cùng mức thì ngày nhận mới nhất trước.
- Phân trang 20 dòng/trang, hiển thị "Hiển thị a–b / tổng N mẫu".
- Mỗi dòng hiện tiến độ "số kết quả đã duyệt / tổng số kết quả hiệu lực". Lab 11.

### BR-14. Quá hạn
Mẫu **quá hạn** khi chưa *Hoàn thành*/*Đã hủy* và hạn trả kết quả **trước ngày hôm nay theo giờ Việt Nam**. Hiển thị nhãn đỏ "Quá hạn" trên danh sách, chi tiết, tổng quan. Lab 12.

### BR-15. Tổng quan và TAT
Trang chủ sau khi đăng nhập hiển thị: số mẫu theo từng trạng thái (trạng thái không có mẫu hiện 0), số mẫu quá hạn, số mẫu hoàn thành trong 30 ngày, **TAT trung bình 30 ngày** (giờ, 1 chữ số thập phân), tối đa 10 mẫu cần chú ý (Khẩn hoặc quá hạn, chưa kết thúc) sắp theo hạn trả. Lab 16.

### BR-16. Xuất CSV
Người duyệt và Quản lý xuất danh sách **theo đúng bộ lọc đang xem**, tối đa 10.000 dòng. File UTF-8 có BOM (mở đúng tiếng Việt bằng Excel), tên `labtrack-mau-<yyyy-mm-dd>.csv`, cột: Mã mẫu, Sản phẩm, Số lô, Loại mẫu, Ưu tiên, Bảo quản, Trạng thái, Ngày nhận, Hạn trả, Quá hạn, Kết luận. Ô bắt đầu bằng `= + - @` được thêm `'` phía trước để chống CSV injection. Lab 15.

### BR-17. Ghi chú mẫu
Trường ghi chú tùy chọn, tối đa 1000 ký tự, nhập khi đăng ký, hiển thị ở trang chi tiết. Lab 13.

### BR-18. Điều kiện bảo quản
Bắt buộc, một trong: Nhiệt độ phòng (15–30 °C, mặc định), Mát (2–8 °C), Đông lạnh (≤ −20 °C). Hiển thị ở chi tiết và CSV. Lab 15.

## 9. Ma trận phân quyền

| Chức năng | Tiếp nhận | Kiểm nghiệm viên | Người duyệt | Quản lý |
|---|:-:|:-:|:-:|:-:|
| Xem tổng quan, danh sách, chi tiết | ✔ | ✔ | ✔ | ✔ |
| Đăng ký mẫu (`GET /samples/new`, `POST /samples`) | ✔ | | | |
| Nhận kiểm nghiệm (`POST /samples/{id}/start`) | | ✔ | ✔ | |
| Nhập kết quả (`POST /samples/{id}/results`) | | ✔ | ✔ | |
| Duyệt / từ chối kết quả | | | ✔ (không phải kết quả của mình) | |
| Kết luận mẫu | | | ✔ | |
| Hủy mẫu | ✔ | | ✔ | |
| Xuất CSV (`GET /samples/export.csv`) | | | ✔ | ✔ |

## 10. Yêu cầu phi chức năng

| Mã | Yêu cầu | Tiêu chí kiểm chứng | Lab |
|---|---|---|---|
| NFR-01 Hiệu năng | Một trang danh sách tốn **≤ 3 câu SQL** (không N+1); truy vấn theo khóa ngoại có index | Test đếm câu SQL; `EXPLAIN ANALYZE` trên 10.000 mẫu dùng Index Scan | 17 |
| NFR-02 Bảo mật | Đăng nhập form, mật khẩu BCrypt, CSRF cho mọi POST, phiên hết hạn sau 30 phút; không có secret trong repo; chống CSV injection | Test 403/CSRF, `/security-review`, hook chặn đọc `.env` | 06, 13, 18 |
| NFR-03 Toàn vẹn dữ liệu (ALCOA+) | Không xóa cứng mẫu/kết quả; lịch sử và audit chỉ thêm; quy tắc quan trọng có ràng buộc ở CSDL | Test ràng buộc DB (ghi thẳng SQL vẫn bị chặn) | 05, 07 |
| NFR-04 Thời gian, ngôn ngữ | Lưu `TIMESTAMPTZ`; hiển thị và tính "hôm nay" theo `Asia/Ho_Chi_Minh` dù máy chủ chạy múi giờ nào; ngày `dd/MM/yyyy`; giao diện tiếng Việt | Test định dạng giờ với JVM ở UTC | 09 |
| NFR-05 Chất lượng | Mỗi BR có ít nhất 1 test; tầng service ≥ 80% dòng (JaCoCo); E2E cho các UC chính; CI xanh trên PostgreSQL thật | `./mvnw verify` và GitHub Actions | 08, 15, 18 |
| NFR-06 Bảo trì | Kiến trúc 3 tầng; SQL chỉ trong mapper XML; schema chỉ đổi bằng migration Flyway mới; CLAUDE.md và `.claude/` mô tả quy ước | Review + hook chặn sửa migration cũ | 03, 09, 13 |
| NFR-07 Dữ liệu | Chỉ dữ liệu giả ở mọi môi trường của dự án học | Quy tắc an toàn, review | Mọi lab |
| NFR-08 Giao diện | Bootstrap 5, dùng được ở màn hình ≥ 360 px; mọi ô nhập có nhãn; thông báo lỗi rõ ràng bằng tiếng Việt | E2E dùng `getByLabel`, ảnh chụp màn hình | 08 |

## 11. Dữ liệu tham chiếu

**Danh mục chỉ tiêu** (nạp trong migration `V4`):

| Mã | Tên | Đơn vị | Tiêu chuẩn |
|---|---|---|---|
| ASSAY | Định lượng hoạt chất | % | 95 – 105 |
| PH | pH | pH | 4.5 – 7 |
| LOD | Mất khối lượng do làm khô | % | ≤ 0.5 |
| DISSOLUTION | Độ hòa tan (30 phút) | % | ≥ 80 |
| IMPURITY_TOTAL | Tạp chất tổng | % | ≤ 1.0 |

## 12. Giả định và ràng buộc kỹ thuật

| Hạng mục | Lựa chọn |
|---|---|
| Ngôn ngữ backend | OpenJDK 21 |
| Framework | Spring Boot 4.1.x (thực tế dùng 4.1.1), Spring Security 7 |
| O/R mapper | MyBatis: `mybatis-spring-boot-starter` 4.1.0 (kèm MyBatis 3.5.19) |
| Giao diện | Thymeleaf 3.1, HTML, CSS, JavaScript thuần, Bootstrap 5.3.8 |
| CSDL | PostgreSQL 18, schema quản lý bằng Flyway |
| Kiến trúc | 3 tầng: trình bày → nghiệp vụ → truy cập dữ liệu |
| Kiểm thử | JUnit 6, Mockito, Spring MockMvc, PostgreSQL thật (CSDL `labtrack_test`), **Playwright** (E2E) |
| Môi trường | Windows 11 + VS Code; PostgreSQL 18 cài trên máy, không dùng Docker |

> **Ghi chú về "MyBatis 4.1.x".** MyBatis lõi đang ở 3.5.x. Số `4.1.x` là phiên bản của **`mybatis-spring-boot-starter`** dành cho Spring Boot 4.1. Spring Initializr (start.spring.io) tại thời điểm viết chỉ cho chọn MyBatis với Boot 4.0.x, nên ở Lab 03 bạn thêm starter này vào `pom.xml` thủ công.

## 13. Kế hoạch phát hành theo increment

| Increment | Lab | Nội dung | Migration | Quy tắc |
|---|---|---|---|---|
| I-1 | 03 | Khung 3 tầng, đăng ký mẫu, danh sách đơn giản | V1 | BR-01, BR-02 |
| I-2 | 04 | Nhận kiểm nghiệm, hủy mẫu | | BR-03, BR-05 |
| I-3 | 05 | Lịch sử trạng thái, trang chi tiết | V2 | BR-04 |
| I-4 | 06 | Đăng nhập, phân quyền | V3 | BR-12 |
| I-5 | 07 | Kết quả, duyệt 4 mắt, audit | V4 | BR-06..BR-10 |
| I-6 | 08 | Bộ test E2E Playwright | | NFR-05, NFR-08 |
| I-7 | 09 | Sửa lỗi hiển thị giờ (LT-101) | | NFR-04 |
| I-8 | 10 | Kết luận mẫu (LT-102) | V5 | BR-11 |
| I-9 | 11 | Mức ưu tiên, tìm kiếm, lọc, phân trang | V6 | BR-13 |
| I-10 | 12 | Cảnh báo quá hạn | | BR-14 |
| I-11 | 13 | Ghi chú mẫu | V7 | BR-17 |
| I-12 | 15 | Xuất CSV ∥ điều kiện bảo quản | V8 | BR-16, BR-18 |
| I-13 | 16 | Tổng quan và TAT | | BR-15 |
| I-14 | 17 | Tối ưu danh sách, index khóa ngoại | V9 | NFR-01 |
| I-15 | 18 | Chất lượng, bảo mật, tài liệu | | NFR-02, NFR-05 |

## 14. Tiêu chí nghiệm thu

Checklist nghiệm thu 12 mục và mẫu biên bản ở [07-nghiem-thu.md](07-nghiem-thu.md).

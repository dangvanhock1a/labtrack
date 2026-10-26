# Kịch bản E2E (I-6)

Kịch bản kiểm thử đầu-cuối cho UC-01..UC-07, chạy bằng Playwright trên `E2ETestBase` (NFR-05, NFR-08). Chưa có code.

- **Nguồn:** LT-BRD mục 4, 6–9 và giao diện hiện tại. Repo không có `02-use-case.md`, nên luồng thay thế của từng UC chưa được rà.
- **Câu chữ:** chữ trong ngoặc kép chép nguyên văn từ template, controller và service. Sửa câu chữ ở code là phải sửa cả kịch bản và test.
- **Dữ liệu:** trước mỗi test, `DbCleaner` dọn CSDL và giữ tài khoản demo cùng danh mục chỉ tiêu. Vì vậy mẫu đầu tiên luôn là `LS-YYYY-00001`, với `YYYY` là năm hiện tại theo giờ VN. Mật khẩu mọi tài khoản là `Passw0rd!`.
- **Tổng quan:** trang Tổng quan chưa có (BR-15, I-13), nên đăng nhập xong thì vào `/samples`. Làm I-13 thì sửa TC-E2E-02.

| Mã TC | Use case | Vai trò | Bước | Kết quả mong đợi |
|---|---|---|---|---|
| **TC-E2E-01**<br>Sai mật khẩu | UC-01 · BR-12 | Chưa đăng nhập | 1. Mở `/login`, nhập `kiemnghiem1` / `SaiMatKhau1`, bấm **Đăng nhập** | Ở lại `/login?error`, hiện "Tên đăng nhập hoặc mật khẩu không đúng". Navbar không có họ tên, không có nút **Đăng xuất** |
| | | Chưa đăng nhập | 2. Nhập tên không tồn tại `khongco` / `Passw0rd!` | Đúng câu lỗi như bước 1, không lộ tên đăng nhập nào có thật |
| **TC-E2E-02**<br>Đăng nhập đúng, đăng xuất | UC-01 · BR-12 | Tiếp nhận (`tiepnhan`) | 1. Đăng nhập `tiepnhan` | Chuyển tới `/samples`, tiêu đề "Danh sách mẫu". Navbar có "Trần Thị Thu", nhãn "Tiếp nhận mẫu" và link **Đăng ký mẫu**. Bảng trống: "Chưa có mẫu nào." |
| | | Tiếp nhận | 2. Bấm **Đăng xuất** | Về `/login?logout`, hiện "Bạn đã đăng xuất" |
| | | Chưa đăng nhập | 3. Gõ thẳng `/samples` | Bị chuyển về `/login`, tức phiên đã kết thúc thật |
| **TC-E2E-03**<br>Luồng chính | UC-02..UC-05, UC-07 · BR-01..BR-08, BR-10, BR-11 | Tiếp nhận | 1. Bấm **Đăng ký mẫu**, để trống Tên sản phẩm, Số lô, Loại mẫu, bấm **Đăng ký** | Ở lại form, mỗi ô có lỗi riêng: "Vui lòng nhập tên sản phẩm", "Số lô chỉ gồm chữ không dấu, số, dấu - và /", "Vui lòng chọn loại mẫu". Thời điểm nhận và Hạn trả vẫn giữ giá trị mặc định |
| | | Tiếp nhận | 2. Nhập "Paracetamol 500 mg", "PA-2601", chọn "Thành phẩm", bấm **Đăng ký** | Sang trang chi tiết, hiện "Đã đăng ký mẫu LS-YYYY-00001", trạng thái "Đã tiếp nhận". Lịch sử có 1 dòng "Tạo mới → Đã tiếp nhận", người thực hiện `tiepnhan`, lý do "Đăng ký mẫu". Không có nút **Nhận kiểm nghiệm** |
| | | Kiểm nghiệm viên (`kiemnghiem1`) | 3. Đăng xuất, đăng nhập `kiemnghiem1`, bấm mã mẫu trong danh sách, bấm **Nhận kiểm nghiệm** | Hiện "Mẫu LS-YYYY-00001 đã chuyển sang Đang kiểm nghiệm". Lịch sử thêm dòng "Đã tiếp nhận → Đang kiểm nghiệm". 5 chỉ tiêu có ô nhập |
| | | Kiểm nghiệm viên | 4. Ô "Giá trị Định lượng hoạt chất (%)" nhập `98.5`, bấm **Lưu** | Hiện "Đã nhập kết quả Định lượng hoạt chất: 98.5 % (Đạt)". Dòng đó: "Đạt", "Chờ duyệt", "kiemnghiem1 · dd/MM/yyyy HH:mm" |
| | | Kiểm nghiệm viên | 5. Ô "Giá trị pH (pH)" nhập `7.5`, bấm **Lưu** | Hiện "Đã nhập kết quả pH: 7.5 pH (Không đạt)". Nhật ký kết quả có 2 dòng "Nhập". Không có nút **Duyệt** hay **Từ chối** nào (vai trò không có quyền) |
| | | Người duyệt (`duyet`) | 6. Đăng xuất, đăng nhập `duyet`, mở mẫu, rê chuột lên nút **Kết luận mẫu**, rồi bấm **Duyệt** ở dòng Định lượng hoạt chất và ở dòng pH | Trước khi duyệt: **Kết luận mẫu** bị vô hiệu, tooltip và mô tả của nút là "Còn kết quả chưa được duyệt nên chưa thể kết luận". Mỗi lần duyệt hiện "Đã duyệt kết quả …". Cả 2 dòng ở "Đã duyệt", kèm "duyet · …". Không còn nút **Duyệt**. Nhật ký có 4 dòng (2 Nhập, 2 Duyệt). Mẫu vẫn "Đang kiểm nghiệm" vì duyệt hết không tự kết luận; **Kết luận mẫu** bấm được |
| | | Người duyệt | 7. Bấm **Kết luận mẫu**; trong hộp thoại "Kết luận mẫu LS-YYYY-00001" bấm **Kết luận** | Hộp thoại ghi kết luận "Không đạt". Hiện "Đã kết luận mẫu: Không đạt", trạng thái "Hoàn thành", mục Kết luận "Không đạt". Lịch sử có 3 dòng, dòng mới "Đang kiểm nghiệm → Hoàn thành", `duyet`, lý do "Kết luận: Không đạt". Không còn nút **Kết luận mẫu** hay **Hủy mẫu**. Bấm **Quay lại danh sách**: dòng của mẫu có "Hoàn thành" và "Không đạt" |
| **TC-E2E-04**<br>Người duyệt tự nhập | UC-04, UC-05 · BR-08 | Người duyệt (`duyet`) | Tiền điều kiện: mẫu đang kiểm nghiệm.<br>1. Đăng nhập `duyet`, mở mẫu, nhập pH `6.2`, bấm **Lưu** | Hiện "Đã nhập kết quả pH: 6.2 pH (Đạt)", người nhập `duyet` |
| | | Người duyệt (`duyet`) | 2. Xem dòng pH | **Duyệt** và **Từ chối** vẫn hiện nhưng bị vô hiệu. Dưới nút có câu "Bạn đã nhập kết quả này; cần người duyệt khác (nguyên tắc 4 mắt)". Rê chuột lên nhóm nút thì tooltip hiện cùng câu đó |
| | | Người duyệt thứ hai (`duyet2`) | 3. Đăng xuất, đăng nhập `duyet2`, mở mẫu, bấm **Duyệt** ở dòng pH | Nút bấm được, hiện "Đã duyệt kết quả pH". Dòng pH "Đã duyệt", kèm "duyet2 · …" |
| **TC-E2E-05**<br>Từ chối có lý do | UC-06, UC-04 · BR-09, BR-10 | Người duyệt (`duyet`) | Tiền điều kiện: mẫu đang kiểm nghiệm, `kiemnghiem1` đã nhập pH `6.2`.<br>1. Đăng nhập `duyet`, mở mẫu, bấm **Từ chối** ở dòng pH | Mở hộp thoại "Từ chối kết quả pH", có dòng "6.2 pH, do kiemnghiem1 nhập". Con trỏ nằm ở ô "Lý do từ chối (5–500 ký tự)" |
| | | Người duyệt | 2. Để trống lý do, bấm **Từ chối** trong hộp thoại | Trình duyệt chặn gửi vì ô bắt buộc, hộp thoại vẫn mở. Kết quả vẫn "Chờ duyệt" |
| | | Người duyệt | 3. Nhập 5 dấu cách, bấm **Từ chối** | Trình duyệt cho gửi, nhưng server cắt khoảng trắng và báo đỏ "Lý do phải dài từ 5 đến 500 ký tự". Kết quả vẫn "Chờ duyệt" |
| | | Người duyệt | 4. Mở lại hộp thoại, nhập "Sai độ pha loãng mẫu chuẩn", bấm **Từ chối** | Hiện "Đã từ chối kết quả pH". Bên dưới dòng pH: "6.2 pH" bị gạch ngang, nhãn "Đã từ chối", "duyet · …", "Lý do: Sai độ pha loãng mẫu chuẩn". Nhật ký có dòng "Từ chối". Dòng pH lại có ô nhập |
| | | Kiểm nghiệm viên | 5. Đăng xuất, đăng nhập `kiemnghiem1`, nhập lại pH `6.4` | Hiện "Đã nhập kết quả pH: 6.4 pH (Đạt)". Dòng pH: "6.4 pH (Lần 2)", "Chờ duyệt". Bản 6.2 gạch ngang vẫn còn bên dưới |
| **TC-E2E-06**<br>Gõ thẳng URL không có quyền | UC-02 · BR-12 | Kiểm nghiệm viên (`kiemnghiem1`) | 1. Đăng nhập `kiemnghiem1` | Danh sách mẫu và navbar không có **Đăng ký mẫu** |
| | | Kiểm nghiệm viên | 2. Gõ thẳng `/samples/new` | HTTP 403, trang "Bạn không có quyền thực hiện thao tác này" nêu vai trò "Kiểm nghiệm viên". Navbar vẫn có "Lê Văn Minh", không phải trang lỗi trắng |
| | | Kiểm nghiệm viên | 3. Bấm **Về danh sách mẫu** | Về `/samples`, vẫn đăng nhập |

## Lựa chọn trong kịch bản

- TC-E2E-03 dùng pH `7.5` (tiêu chuẩn 4.5–7) để có một kết quả Không đạt, cho cả hai nhãn Đạt/Không đạt xuất hiện trên giao diện. Cũng vì kết quả này mà mẫu kết luận Không đạt ở bước 7 (BR-11: chỉ Đạt khi mọi kết quả hiệu lực Đạt).
- TC-E2E-05 bước 3 dùng 5 dấu cách: chuỗi này qua được kiểm tra `required minlength="5"` của trình duyệt, nên đây là cách duy nhất từ giao diện để kiểm tra phần chặn ở server (BR-09). Lý do để trống thì trình duyệt chặn trước khi gửi.

## Đã quyết

1. **Tên test case:** `@DisplayName` mở đầu bằng `TC-E2E-xx`, mã BR ghi ở cuối. Test khói `SmokeIT` là `TC-E2E-00`. CLAUDE.md đã ghi ngoại lệ này.
2. **Tiền điều kiện của TC-E2E-04 và 05** dựng qua service (`seedSampleInTesting`, `seedResult`), không đi lại qua giao diện, vì đường giao diện đã có TC-E2E-03 kiểm.
3. **Câu chữ thông báo** lấy theo ứng dụng hiện tại. Danh mục thông báo `du-an/05` mục 9 không có trong repo, nên chưa đối chiếu được. Câu ví dụ "Đã nhận mẫu vào kiểm nghiệm" khác câu ứng dụng đang hiện ("Mẫu … đã chuyển sang Đang kiểm nghiệm").

## Test hiện thực

| Mã TC | Lớp · method |
|---|---|
| TC-E2E-00 | `SmokeIT.loginPageShowsSignInButton` |
| TC-E2E-01 | `LoginIT.wrongCredentialsStayOnLoginPage` |
| TC-E2E-02 | `LoginIT.signInShowsUserThenSignOutEndsSession` |
| TC-E2E-03 | `SampleLifecycleIT.fullLifecycle` |
| TC-E2E-04 | `AuthorizationIT.reviewerCannotReviewOwnResult` |
| TC-E2E-05 | `SampleLifecycleIT.rejectWithReasonThenReenter` |
| TC-E2E-06 | `AuthorizationIT.analystTypingRegisterUrlGetsForbiddenPage` |

# Kịch bản E2E (I-6)

Kịch bản kiểm thử đầu-cuối cho UC-01..UC-09, chạy bằng Playwright trên `E2ETestBase` (NFR-05, NFR-08). Chưa có code.

- **Nguồn:** LT-BRD mục 4, 6–9 và giao diện hiện tại. Repo không có `02-use-case.md`, nên luồng thay thế của từng UC chưa được rà.
- **Câu chữ:** chữ trong ngoặc kép chép nguyên văn từ template, controller và service. Sửa câu chữ ở code là phải sửa cả kịch bản và test.
- **Dữ liệu:** trước mỗi test, `DbCleaner` dọn CSDL và giữ tài khoản demo cùng danh mục chỉ tiêu. Vì vậy mẫu đầu tiên luôn là `LS-YYYY-00001`, với `YYYY` là năm hiện tại theo giờ VN. Mật khẩu mọi tài khoản là `Passw0rd!`.
- **Tổng quan:** đăng nhập xong vào trang Tổng quan `/` (BR-15, I-13). Trang này chỉ có link tới mẫu Khẩn hoặc quá hạn, nên muốn mở một mẫu thì bấm **Danh sách mẫu** trên navbar rồi bấm mã mẫu (`openSample`).

| Mã TC | Use case | Vai trò | Bước | Kết quả mong đợi |
|---|---|---|---|---|
| **TC-E2E-01**<br>Sai mật khẩu | UC-01 · BR-12 | Chưa đăng nhập | 1. Mở `/login`, nhập `kiemnghiem1` / `SaiMatKhau1`, bấm **Đăng nhập** | Ở lại `/login?error`, hiện "Tên đăng nhập hoặc mật khẩu không đúng". Navbar không có họ tên, không có nút **Đăng xuất** |
| | | Chưa đăng nhập | 2. Nhập tên không tồn tại `khongco` / `Passw0rd!` | Đúng câu lỗi như bước 1, không lộ tên đăng nhập nào có thật |
| **TC-E2E-02**<br>Đăng nhập đúng, đăng xuất | UC-01 · BR-12 | Tiếp nhận (`tiepnhan`) | 1. Đăng nhập `tiepnhan` | Chuyển tới `/`, tiêu đề "Tổng quan". Navbar có "Trần Thị Thu", nhãn "Tiếp nhận mẫu" và link **Đăng ký mẫu**. Chưa có mẫu: "Không có mẫu cần chú ý." |
| | | Tiếp nhận | 2. Bấm **Đăng xuất** | Về `/login?logout`, hiện "Bạn đã đăng xuất" |
| | | Chưa đăng nhập | 3. Gõ thẳng `/samples` | Bị chuyển về `/login`, tức phiên đã kết thúc thật |
| **TC-E2E-03**<br>Luồng chính | UC-02..UC-05, UC-07 · BR-01..BR-08, BR-10, BR-11 | Tiếp nhận | 1. Bấm **Đăng ký mẫu** trên navbar, để trống Tên sản phẩm, Số lô, Loại mẫu, bấm **Đăng ký** | Ở lại form, mỗi ô có lỗi riêng: "Vui lòng nhập tên sản phẩm", "Số lô chỉ gồm chữ không dấu, số, dấu - và /", "Vui lòng chọn loại mẫu". Thời điểm nhận và Hạn trả vẫn giữ giá trị mặc định |
| | | Tiếp nhận | 2. Nhập "Paracetamol 500 mg", "PA-2601", chọn "Thành phẩm", bấm **Đăng ký** | Sang trang chi tiết, hiện "Đã đăng ký mẫu LS-YYYY-00001", trạng thái "Đã tiếp nhận". Lịch sử có 1 dòng "Tạo mới → Đã tiếp nhận", người thực hiện `tiepnhan`, lý do "Đăng ký mẫu". Không có nút **Nhận kiểm nghiệm** |
| | | Kiểm nghiệm viên (`kiemnghiem1`) | 3. Đăng xuất, đăng nhập `kiemnghiem1`, mở **Danh sách mẫu**, bấm mã mẫu, bấm **Nhận kiểm nghiệm** | Hiện "Mẫu LS-YYYY-00001 đã chuyển sang Đang kiểm nghiệm". Lịch sử thêm dòng "Đã tiếp nhận → Đang kiểm nghiệm". 5 chỉ tiêu có ô nhập |
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
| **TC-E2E-06**<br>Gõ thẳng URL không có quyền | UC-02 · BR-12 | Kiểm nghiệm viên (`kiemnghiem1`) | 1. Đăng nhập `kiemnghiem1` | Trang Tổng quan và navbar không có **Đăng ký mẫu** |
| | | Kiểm nghiệm viên | 2. Gõ thẳng `/samples/new` | HTTP 403, trang "Bạn không có quyền thực hiện thao tác này" nêu vai trò "Kiểm nghiệm viên". Navbar vẫn có "Lê Văn Minh", không phải trang lỗi trắng |
| | | Kiểm nghiệm viên | 3. Bấm **Về danh sách mẫu** | Về `/samples`, vẫn đăng nhập |
| **TC-E2E-07**<br>Lọc mẫu Khẩn | UC-09 · BR-13 | Quản lý (`quanly`) | Tiền điều kiện: đăng ký qua service, theo thứ tự, 4 mẫu "Thành phẩm": "Amoxicillin 250 mg" `AM-2601` Khẩn; "Vitamin C 500 mg" `VC-2601` Khẩn, đã nhận kiểm nghiệm; "Ibuprofen 400 mg" `IB-2601` Bình thường; "Cetirizin 10 mg" `CE-2601` Thấp.<br>1. Đăng nhập `quanly`, mở **Danh sách mẫu** | "Hiển thị 1–4 / tổng 4 mẫu". Các dòng theo thứ tự số lô `VC-2601`, `AM-2601`, `IB-2601`, `CE-2601`: Khẩn đứng đầu dù `CE-2601` nhận sau cùng |
| | | Quản lý | 2. Ô "Ưu tiên" chọn "Khẩn", bấm **Lọc** | URL có `priority=URGENT`. "Hiển thị 1–2 / tổng 2 mẫu". Chỉ còn 2 dòng `VC-2601`, `AM-2601`, cả hai có nhãn "Khẩn"; không còn `IB-2601`, `CE-2601`. Ô "Ưu tiên" vẫn chọn "Khẩn" |
| | | Quản lý | 3. Ô "Trạng thái" chọn "Đang kiểm nghiệm", bấm **Lọc** | URL có cả `priority=URGENT` và `status=IN_TESTING`. "Hiển thị 1–1 / tổng 1 mẫu", chỉ còn dòng `VC-2601`. Ô "Ưu tiên" vẫn chọn "Khẩn" |
| | | Quản lý | 4. Ô "Trạng thái" chọn "Hoàn thành", bấm **Lọc** | Hiện "Không có mẫu nào khớp bộ lọc.", không có bảng và không có dòng "Hiển thị …" |
| | | Quản lý | 5. Bấm **Xóa bộ lọc** | Về `/samples` không kèm tham số. "Hiển thị 1–4 / tổng 4 mẫu", 4 dòng theo thứ tự như bước 1. Ô "Ưu tiên" và "Trạng thái" về "Tất cả" |
| **TC-E2E-08**<br>Tổng quan | UC-08 · BR-15, BR-14 | Quản lý (`quanly`) | Tiền điều kiện: đăng ký qua service 2 mẫu "Thành phẩm": "Amoxicillin 250 mg" `AM-2601` Khẩn, nhận lúc 08:00 hôm kia, hạn trả hôm qua; "Ibuprofen 400 mg" `IB-2601` Bình thường, nhận lúc này, hạn trả mặc định.<br>1. Đăng nhập `quanly` | Ở `/`, tiêu đề "Tổng quan". Ô "Đã tiếp nhận" là 2; "Đang kiểm nghiệm", "Hoàn thành", "Đã hủy" đều là 0. "Quá hạn" 1, "Hoàn thành trong 30 ngày" 0, "TAT trung bình 30 ngày" là "—" kèm "Chưa có mẫu hoàn thành trong 30 ngày". "Mẫu cần chú ý" có đúng 1 dòng: `AM-2601`, nhãn "Khẩn" và "Quá hạn"; không có `IB-2601` |
| | | Quản lý | 2. Bấm ô **Quá hạn** | Sang `/samples?overdueOnly=true`, ô "Quá hạn" được chọn. "Hiển thị 1–1 / tổng 1 mẫu", chỉ có dòng `AM-2601`, mang nhãn "Quá hạn"; không có `IB-2601` |
| | | Quản lý | 3. Bấm mã mẫu ở dòng `AM-2601` | Sang trang chi tiết của mẫu đó, tiêu đề "Mẫu LS-…". Trạng thái "Đã tiếp nhận", cạnh đó nhãn "Quá hạn" |
| **TC-E2E-09**<br>Xuất CSV theo bộ lọc | UC-10, UC-12 · BR-16, BR-18, BR-12 | Quản lý (`quanly`) | Tiền điều kiện: đăng ký qua service 3 mẫu "Thành phẩm", theo thứ tự dưới đây, nhận lúc này, hạn trả mặc định: "Amoxicillin 250 mg" `AM-2601` Khẩn, bảo quản Mát; "Vitamin C 500 mg" `VC-2601` Khẩn, không chọn bảo quản; "Ibuprofen 400 mg" `IB-2601` Bình thường.<br>1. Đăng nhập `quanly`, mở **Danh sách mẫu** | "Hiển thị 1–3 / tổng 3 mẫu". Có link **Xuất CSV** |
| | | Quản lý | 2. Ô "Ưu tiên" chọn "Khẩn", bấm **Lọc** | URL có `priority=URGENT`, "Hiển thị 1–2 / tổng 2 mẫu". Link **Xuất CSV** trỏ tới `/samples/export.csv?priority=URGENT` |
| | | Quản lý | 3. Bấm **Xuất CSV** | Trình duyệt tải về `labtrack-mau-<hôm nay giờ VN, yyyy-mm-dd>.csv`; trang vẫn là danh sách đã lọc. File bắt đầu bằng BOM UTF-8, mọi dòng kết thúc bằng CRLF. Dòng 1: "Mã mẫu,Sản phẩm,Số lô,Loại mẫu,Ưu tiên,Bảo quản,Trạng thái,Ngày nhận,Hạn trả,Quá hạn,Kết luận". Đúng 2 dòng dữ liệu, theo thứ tự của danh sách: `VC-2601` rồi `AM-2601`. Cả hai dòng có "Thành phẩm", "Khẩn", "Đã tiếp nhận", Quá hạn "Không", Kết luận để trống; Ngày nhận và Hạn trả trùng chữ trên dòng tương ứng của danh sách. Bảo quản: `AM-2601` "Mát (2–8 °C)", `VC-2601` "Nhiệt độ phòng (15–30 °C)". Không có `IB-2601` |
| | | Quản lý | 4. Bấm mã mẫu ở dòng `AM-2601` | Trang chi tiết "Mẫu LS-…", mục Bảo quản là "Mát (2–8 °C)", trùng ô trong CSV |
| | | Kiểm nghiệm viên (`kiemnghiem1`) | 5. Đăng xuất, đăng nhập `kiemnghiem1`, mở **Danh sách mẫu** | "Hiển thị 1–3 / tổng 3 mẫu" nhưng không có link **Xuất CSV** |
| | | Kiểm nghiệm viên | 6. Gõ thẳng `/samples/export.csv?priority=URGENT` | HTTP 403, trang "Bạn không có quyền thực hiện thao tác này" nêu vai trò "Kiểm nghiệm viên": trang lỗi hiện ra, không có file nào được tải |

## Lựa chọn trong kịch bản

- TC-E2E-03 dùng pH `7.5` (tiêu chuẩn 4.5–7) để có một kết quả Không đạt, cho cả hai nhãn Đạt/Không đạt xuất hiện trên giao diện. Cũng vì kết quả này mà mẫu kết luận Không đạt ở bước 7 (BR-11: chỉ Đạt khi mọi kết quả hiệu lực Đạt).
- TC-E2E-05 bước 3 dùng 5 dấu cách: chuỗi này qua được kiểm tra `required minlength="5"` của trình duyệt, nên đây là cách duy nhất từ giao diện để kiểm tra phần chặn ở server (BR-09). Lý do để trống thì trình duyệt chặn trước khi gửi.
- TC-E2E-07 đăng ký mẫu Thấp `CE-2601` sau cùng. Nếu danh sách chỉ xếp theo ngày nhận thì mẫu này đứng đầu, nên bước 1 chỉ qua khi mức ưu tiên được xếp trước. `VC-2601` được nhận kiểm nghiệm để ở bước 3, thêm bộ lọc trạng thái còn đúng 1 trong 2 mẫu Khẩn: hai bộ lọc kết hợp với nhau, bộ lọc sau không thay bộ lọc trước. Vai trò là `quanly` vì tra cứu là việc chính của Quản lý (mục 4 LT-BRD) và chưa kịch bản nào dùng tài khoản này.
- TC-E2E-08 cho `AM-2601` hạn trả hôm qua, mốc gần nhất đã quá hạn: hạn hôm nay thì chưa quá hạn (BR-14). Mẫu nhận hôm kia vì hạn trả không được trước ngày nhận. `AM-2601` vừa Khẩn vừa quá hạn nên có cả hai nhãn trong "Mẫu cần chú ý". `IB-2601` Bình thường, chưa tới hạn nên không được đưa vào bảng đó, cũng không lọt bộ lọc Quá hạn: đây là hai bước phủ định. Bước 3 mở trang chi tiết để kịch bản đi qua đủ ba màn hình BR-14 yêu cầu có nhãn "Quá hạn": tổng quan, danh sách, chi tiết. Chưa mẫu nào hoàn thành, nên ô TAT phải hiện "—" (câu hỏi 3 `docs/cau-hoi-lam-ro.md`). Vai trò là `quanly` vì xem tổng quan là việc chính của Quản lý (mục 4 LT-BRD).
- TC-E2E-09 chỉ kiểm phần mà unit test và `@WebMvcTest` không chạm tới: trình duyệt tải file thật qua Tomcat (tên file lấy từ `Content-Disposition`, BOM và UTF-8 đi qua HTTP), link **Xuất CSV** mang bộ lọc form vừa gửi, giá trị Bảo quản đi từ CSDL ra file, và phân quyền thật. `VC-2601` không chọn bảo quản để giá trị mặc định Nhiệt độ phòng (BR-18) đi tới tận file; `AM-2601` chọn Mát để cột Bảo quản đọc theo từng mẫu, không in một giá trị chung. `AM-2601` đăng ký trước, mà cùng mức ưu tiên thì mẫu nhận sau đứng trước (`received_at DESC, id DESC`), nên thứ tự đúng là `VC-2601` rồi `AM-2601`. `IB-2601` Bình thường là bước phủ định của bộ lọc. Ngày nhận và Hạn trả phụ thuộc giờ chạy test nên không ghi giá trị cố định: test so chúng với chữ trên danh sách, nơi dùng cùng định dạng (NFR-04). Ô bắt đầu bằng `= + - @`, bọc ngoặc kép và giới hạn 10.000 mẫu không kiểm ở đây: TC-BR16-01..04 và TC-BR16-11, 12 đã kiểm, còn dựng 10.001 mẫu qua E2E thì quá chậm. Vai trò là `quanly`; `duyet` cũng được xuất, TC-BR12-22 đã kiểm.

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
| TC-E2E-08 | `DashboardIT.overviewShowsCountsAndAttention` |
| TC-E2E-09 | `SampleExportIT.exportFollowsCurrentFilter` |

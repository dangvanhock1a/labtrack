# SPEC-004. Kết quả kiểm nghiệm: nhập, tự đánh giá, duyệt 4 mắt, từ chối, audit trail

| Mục | Nội dung |
|---|---|
| Quy tắc | BR-06, BR-07, BR-08, BR-09, BR-10 (LT-BRD mục 8); liên quan BR-05, BR-12, NFR-03, NFR-08 |
| Use case | UC-04 Nhập kết quả · UC-05 Duyệt · UC-06 Từ chối · UC-12 Xem chi tiết |
| Increment | I-5 (Lab 07), migration `V4` |
| Branch | `feature/LT-005-ket-qua` |
| Ngày | 23/10/2026 |
| Trạng thái | Đã chốt qua phỏng vấn với người phụ trách; các điểm ở mục 6 chờ nghiệp vụ trả lời |
| Dữ liệu | Chỉ dữ liệu giả (NFR-07) |

## 0. Quyết định đã chốt (phỏng vấn 23/10/2026)

Các quyết định dưới đây trả lời những chỗ LT-BRD bỏ ngỏ, trong đó có câu Q4, Q5, Q7, Q8, Q9 của `cau-hoi-lam-ro.md` mục 2. Câu nào chỉ được trả lời một phần thì phần còn lại nằm ở mục 6.

| # | Chủ đề | Quyết định | Trả lời |
|---|---|---|---|
| D1 | Phạm vi | Làm trọn BR-06..BR-10 trong một increment | |
| D2 | Người duyệt tự nhập kết quả | REVIEWER vẫn được nhập (LT-BRD mục 4, mục 9). Dữ liệu demo thêm người duyệt thứ hai `duyet2`, để kết quả do `duyet` nhập vẫn có người duyệt | Q4 |
| D3 | Phân công mẫu | Không phân công. Mọi ANALYST/REVIEWER nhập được mọi chỉ tiêu của mọi mẫu *Đang kiểm nghiệm*; nhập lại sau khi bị từ chối không bắt buộc cùng người | Q7 |
| D4 | Sửa kết quả sai | Chỉ một đường: người duyệt khác từ chối, rồi nhập lại. Không có "rút lại"; kết quả *Đã duyệt* là chốt | |
| D5 | Kiểu lưu giá trị | `NUMERIC` không cố định scale + `CHECK (value >= 0 AND value < 100000000 AND scale(value) <= 4)`. CSDL từ chối chứ không làm tròn ngầm; `98.50` được lưu và hiển thị đúng là `98.50` | |
| D6 | So với tiêu chuẩn | So nguyên văn, không làm tròn: ASSAY 95–105, đo 105.04 → **Không đạt** | Q9 (một phần) |
| D7 | Tiêu chuẩn áp dụng | Chụp `unit`, `spec_min`, `spec_max` vào bản ghi kết quả lúc nhập; CSDL kiểm tra verdict khớp với giá trị và bản chụp | Q9 (một phần) |
| D8 | "Chỉ tiêu đang được dùng" | Là cờ `test_parameter.active`. Không có bộ chỉ tiêu bắt buộc theo mẫu | Q8 (một phần) |
| D9 | Đồng thời | Mọi thao tác trên kết quả mở đầu bằng `SELECT … FROM lab_sample WHERE id = ? FOR UPDATE` | |
| D10 | Hủy mẫu còn kết quả *Chờ duyệt* | Kết quả giữ nguyên *Chờ duyệt*; không sửa `LabSampleService.cancel`; giao diện ẩn mọi thao tác trên kết quả | Q5 (một phần) |
| D11 | Cưỡng chế "chỉ thêm" | Trigger trong CSDL: `result_audit` không UPDATE/DELETE/TRUNCATE được; `test_result` không DELETE/TRUNCATE được và chỉ được UPDATE đúng một lần, từ PENDING sang APPROVED/REJECTED. `sample_status_history` (V2) giữ nguyên | |
| D12 | Nội dung audit | `detail JSONB`, dựng bằng `jsonb_build_object` trong mapper XML từ chính dòng `test_result` ngay sau thao tác. Bản nhập lại trỏ về bản bị từ chối qua `test_result.replaces_id` | |
| D13 | Màn hình nhập | Bảng chỉ tiêu ngay trên `/samples/{id}`, mỗi chỉ tiêu một form; mỗi lần gửi 1 chỉ tiêu | |
| D14 | Báo lỗi | Flash `error` rồi quay về trang chi tiết, như *Nhận kiểm nghiệm*/*Hủy mẫu*; không thêm try/catch trong controller. Giá trị vừa gõ không được giữ lại | |
| D15 | Duyệt | Nút Duyệt/Từ chối cho từng kết quả; không duyệt hàng loạt | |
| D16 | Kiểm chứng end-to-end | Kịch bản tay + một test HTTP thật (`RANDOM_PORT`); Playwright để I-6 | |

## 1. Phạm vi

### 1.1 Trong phạm vi

1. Nhập kết quả cho một chỉ tiêu của mẫu *Đang kiểm nghiệm*; hệ thống tự đánh giá Đạt/Không đạt (BR-06, BR-07).
2. Duyệt, hoặc từ chối kèm lý do, từng kết quả *Chờ duyệt* theo nguyên tắc 4 mắt (BR-08, BR-09).
3. Nhập lại chỉ tiêu có kết quả bị từ chối, thành bản ghi mới trỏ về bản cũ (BR-09).
4. Audit trail chỉ thêm cho ba thao tác nhập, duyệt, từ chối (BR-10, NFR-03).
5. Trang chi tiết mẫu có thêm mục **Kết quả kiểm nghiệm** và **Nhật ký kết quả** (UC-12).
6. Migration `V4`: danh mục `test_parameter` kèm 5 chỉ tiêu của LT-BRD mục 11, `test_result`, `result_audit`, trigger.
7. Luật phân quyền mới cho duyệt/từ chối trong `SecurityConfig` (BR-12).
8. Tài khoản demo `duyet2` (REVIEWER) trong `db/devdata/R__dev_seed.sql`, sửa TC-BR12-05 thành 6 tài khoản.
9. Cập nhật CLAUDE.md: mục *Bẫy* (xem 3.7), số tài khoản demo, các endpoint mới.

### 1.2 Thành phần

| Tầng | Thành phần | Ghi chú |
|---|---|---|
| domain | `TestParameter`, `TestResult`, `ResultAudit` (dòng nhật ký để hiển thị), `Spec` (record `min`, `max`; `Verdict evaluate(BigDecimal)`, `String label()`), enum `ResultStatus`, `Verdict`, `ResultAction` | Nhãn tiếng Việt nằm trong enum, giống `SampleStatus` |
| persistence | `TestParameterMapper`, `TestResultMapper`, `ResultAuditMapper` + XML; `LabSampleMapper.lockById` | SQL chính ở mục 3.4 |
| service | `TestResultService` (`enter`, `approve`, `reject`, `findSheet`, `findAudit`), record `NewResult(Long parameterId, BigDecimal value)` | Không import Spring Security; người thực hiện là `String actor` |
| web | `ResultForm` (`parameterId`, `value`); 3 endpoint thêm vào `SampleController` | Thêm vào `SampleController` để dùng chung `@InitBinder` cắt khoảng trắng và để `detail()` nạp thêm dữ liệu kết quả |
| config | `SecurityConfig`: luật cho `/samples/*/results/*/approve` và `/reject` | Cập nhật Javadoc đang ghi "duyệt/từ chối chưa có URL" |
| template | `samples/detail.html`; fragment `resultStatusBadge`, `verdictBadge` trong `fragments/layout.html` | |
| resources | `V4__create_test_result.sql`, `messages.properties` (`typeMismatch.resultForm.*`), `R__dev_seed.sql` (`duyet2`) | |

Dùng lại, không viết mới: `BusinessException(code, field, message)`, `GlobalControllerAdvice` (biến đường dẫn phải tên `id`), bean `Clock`, cách đổi múi giờ như `LabSampleService.toClockZone`, `@WithAppUser`, `TestcontainersConfiguration`.

### 1.3 Ngoài phạm vi

| Ngoài phạm vi | Ghi chú |
|---|---|
| Kết luận mẫu (BR-11) | I-8, migration V5. V4 không được chặn đường cho nó: kết luận sẽ khóa dòng mẫu theo đúng D9 |
| Tiến độ "đã duyệt / hiệu lực" trên danh sách (BR-13) | I-9 |
| Màn hình quản lý chỉ tiêu | LT-BRD mục 3; đổi danh mục bằng migration mới |
| Rút lại kết quả *Chờ duyệt*, thu hồi kết quả *Đã duyệt* | D4; xem câu hỏi mở 6.4 |
| Duyệt hàng loạt | D15 |
| Bộ chỉ tiêu bắt buộc theo loại mẫu | D8; câu hỏi mở 6.1 |
| Playwright | I-6 |
| Trigger "chỉ thêm" cho `sample_status_history` | D11; nếu cần thì viết migration riêng |
| Sửa LT-BRD và `cau-hoi-lam-ro.md` | Câu hỏi mở 6.9 |

## 2. Quy tắc

Ký hiệu loại test giống `cau-hoi-lam-ro.md` mục 1: **U** unit (Mockito, `Clock.fixed` múi giờ VN) · **W** `@WebMvcTest` + `@Import(SecurityConfig.class)` · **I** `@SpringBootTest` + Testcontainers · **DB** ghi thẳng SQL bằng `JdbcTemplate` · **CC** chạy đồng thời bằng 2 transaction thật · **H** HTTP thật (`RANDOM_PORT`).

### 2.1 Bảng quy tắc

| R | Quy tắc | BR | Tầng chặn | Mã lỗi | Test |
|---|---|---|---|---|---|
| R1 | Chỉ nhập kết quả khi mẫu đang ở *Đang kiểm nghiệm*. Trạng thái được đọc **sau khi** đã khóa dòng mẫu (R14) | BR-06 | Giao diện (ẩn ô nhập) · Service | `SAMPLE_NOT_IN_TESTING` | TC-BR06-01 · TC-BR08-08 |
| R2 | Chỉ tiêu phải tồn tại và `active = TRUE`. Chỉ tiêu ngừng dùng vẫn hiện trên trang chi tiết nếu mẫu đã có kết quả cho nó, nhưng không có ô nhập | BR-06 | Giao diện (chỉ chỉ tiêu active có ô nhập) · Service · DB `fk_test_result_parameter` | `PARAMETER_NOT_FOUND`, `PARAMETER_INACTIVE` | TC-BR06-02 · TC-BR06-11 |
| R3 | Giá trị bắt buộc, ≥ 0, tối đa 8 chữ số phần nguyên và 4 chữ số thập phân, đếm cả số 0 ở cuối (`98.50000` bị chặn). Ký hiệu khoa học có scale âm (`1e3`) được service đổi về scale 0 (`1000`). Giá trị được lưu và hiển thị đúng số chữ số người nhập (`98.50`) | BR-06 | Form (`@NotNull`, `@DecimalMin("0")`, `@Digits(integer = 8, fraction = 4)`; HTML `min="0"` `step="0.0001"`) · Service (kiểm tra `scale()` không bỏ số 0 cuối) · DB `ck_test_result_value` | `INVALID_VALUE` (field `value`) | TC-BR06-03 · TC-BR06-04 · TC-BR06-05 · TC-BR06-09 |
| R4 | Mỗi (mẫu, chỉ tiêu) chỉ có 1 kết quả hiệu lực (*Chờ duyệt* hoặc *Đã duyệt*). Kết quả *Đã từ chối* không tính, nên vẫn nhập lại được. Lỗi `DuplicateKeyException` trên `uq_test_result_effective` được service đổi thành cùng thông báo | BR-06 | Service (kiểm tra khi đang giữ khóa mẫu) · DB `uq_test_result_effective` (unique partial index) | `RESULT_ALREADY_EFFECTIVE` | TC-BR06-06 · TC-BR06-07 · TC-BR06-08 |
| R5 | Đánh giá do hệ thống tự tính: **Đạt** khi `(spec_min IS NULL OR value ≥ spec_min) AND (spec_max IS NULL OR value ≤ spec_max)`, ngược lại **Không đạt**. So bằng `BigDecimal.compareTo`, không làm tròn. Form không có trường đánh giá; request gửi kèm `verdict` thì phần đó bị bỏ qua | BR-07 | Service (`Spec.evaluate`) · DB `ck_test_result_verdict_spec` | | TC-BR07-01 · TC-BR07-02 · TC-BR07-03 |
| R6 | Lúc nhập, `unit`, `spec_min`, `spec_max` được chụp từ danh mục vào kết quả. Đánh giá và hiển thị dùng bản chụp; danh mục đổi sau đó không ảnh hưởng tới kết quả đã có | BR-07, NFR-03 | Service · DB (cột NOT NULL, `ck_test_result_spec_range`, `ck_test_result_verdict_spec`) | | TC-BR07-04 · TC-BR06-09 |
| R7 | Nguyên tắc 4 mắt: người duyệt hoặc từ chối phải khác `entered_by`. Trên giao diện, nút Duyệt/Từ chối bị vô hiệu với kết quả do chính mình nhập, kèm câu giải thích | BR-08 | Giao diện (`disabled`) · Service · DB `ck_four_eyes` | `FOUR_EYES` | TC-BR08-01 · TC-BR08-05 · TC-BR08-06 · TC-BR10-07 |
| R8 | Chỉ xử lý kết quả *Chờ duyệt* của mẫu *Đang kiểm nghiệm*, và kết quả phải thuộc đúng mẫu trong URL. Thứ tự kiểm tra ở 2.3. `UPDATE … WHERE status = 'PENDING'` cập nhật 0 dòng cũng là `RESULT_ALREADY_PROCESSED` | BR-08 | Service · DB trigger `tg_test_result_guard_update` | `SAMPLE_NOT_IN_TESTING`, `RESULT_NOT_FOUND`, `RESULT_ALREADY_PROCESSED` | TC-BR08-02 · TC-BR08-03 · TC-BR08-04 · TC-BR08-07 · TC-BR10-05 |
| R9 | Quyền: `POST /samples/{id}/results` cho ANALYST, REVIEWER (luật đã có). `POST /samples/{id}/results/{resultId}/approve` và `/reject` **chỉ** REVIEWER. Chưa đăng nhập thì về `/login`; thiếu CSRF thì 403 | BR-12, BR-08 | `SecurityConfig` (luật mới đặt trước `GET /samples/*`) | (403) | TC-BR12-15 · TC-BR12-16 · TC-BR12-17 |
| R10 | Từ chối bắt buộc lý do dài 5–500 ký tự sau khi cắt khoảng trắng; lưu bản đã cắt. Lý do được kiểm tra trước khi đọc CSDL, như `cancel` | BR-09 | Form (HTML `required minlength=5 maxlength=500`) · Service · DB `ck_test_result_reject_reason` | `REASON_REQUIRED` (field `reason`) | TC-BR09-01 · TC-BR09-03 |
| R11 | Kết quả bị từ chối được giữ nguyên (NFR-03). Nhập lại tạo dòng mới có `replaces_id` = bản *Đã từ chối* gần nhất của cùng (mẫu, chỉ tiêu) chưa bị thay. Mỗi bản bị từ chối chỉ được thay một lần; bản thay phải cùng mẫu và cùng chỉ tiêu. Giao diện gạch ngang bản cũ, hiện lý do, đánh số "Lần n" | BR-09 | Service · DB `uq_test_result_replaces`, `fk_test_result_replaces` (FK ghép), `ck_test_result_replaces_self` | | TC-BR09-02 · TC-BR09-03 · TC-BR09-04 |
| R12 | Mỗi thao tác nhập/duyệt/từ chối thành công ghi **đúng 1** dòng `result_audit` (thao tác, người, thời điểm lấy từ `Clock`, chi tiết) trong cùng transaction. Thao tác thất bại thì không có dòng nào; ghi audit lỗi thì thao tác rollback theo | BR-10 | Service (`@Transactional`; kiểm tra INSERT audit trả về 1) | | TC-BR10-01 · TC-BR10-02 · TC-BR10-03 · TC-BR10-07 |
| R13 | Dữ liệu chỉ thêm: `result_audit` không UPDATE/DELETE/TRUNCATE được; `test_result` không DELETE/TRUNCATE được và chỉ được UPDATE một lần, từ PENDING sang APPROVED/REJECTED, ở 4 cột `status`, `reviewed_by`, `reviewed_at`, `reject_reason` | BR-10, NFR-03 | DB trigger · mapper không có câu UPDATE/DELETE nào cho `result_audit` | | TC-BR10-04 · TC-BR10-05 |
| R14 | Thao tác trên kết quả và thao tác đổi trạng thái mẫu (hủy; sau này là kết luận) không xen nhau: thao tác kết quả khóa dòng mẫu bằng `FOR UPDATE` trước mọi kiểm tra; `UPDATE lab_sample … WHERE status = <cũ>` của hủy mẫu phải chờ khóa đó | BR-06, BR-08, BR-03 | Service (`LabSampleMapper.lockById`) | | TC-BR06-10 · TC-BR08-07 |
| R15 | Mẫu bị hủy khi còn kết quả *Chờ duyệt*: kết quả giữ nguyên *Chờ duyệt*, không sinh dòng audit; trang chi tiết không còn ô nhập hay nút Duyệt/Từ chối, và ghi chú "Mẫu đã hủy, kết quả chờ duyệt không còn được xử lý" | BR-05, BR-08 | Service (R1, R8) · Giao diện | | TC-BR08-08 |
| R16 | Dữ liệu demo có ít nhất 2 REVIEWER (`duyet`, `duyet2`) để mọi kết quả đều có người duyệt được | BR-08 × LT-BRD mục 4 | Seed `R__dev_seed.sql` | | TC-BR12-05 (sửa) |
| R17 | Mọi vai trò đã đăng nhập xem được kết quả và nhật ký kết quả trên trang chi tiết; thời điểm hiển thị theo múi giờ ứng dụng (`dd/MM/yyyy HH:mm:ss`) dù pgjdbc trả UTC | UC-12, NFR-04 | Service (đổi múi giờ) · Template | | TC-BR10-01 · TC-BR10-06 |

### 2.2 Thông báo lỗi

Test so khớp nguyên văn. "Đề xuất" nghĩa là LT-BRD chưa có câu chữ này, cần nghiệp vụ xác nhận (câu hỏi mở 6.8).

| Mã (`BusinessException.code`) | Field | Thông báo | Nguồn |
|---|---|---|---|
| `SAMPLE_NOT_FOUND` | | Không tìm thấy mẫu | Có sẵn |
| `SAMPLE_NOT_IN_TESTING` (nhập) | | Chỉ nhập kết quả cho mẫu đang kiểm nghiệm | LT-BRD BR-06 |
| `SAMPLE_NOT_IN_TESTING` (duyệt, từ chối) | | Chỉ duyệt hoặc từ chối kết quả của mẫu đang kiểm nghiệm | Đề xuất |
| `PARAMETER_NOT_FOUND` | `parameterId` | Không tìm thấy chỉ tiêu | Đề xuất |
| `PARAMETER_INACTIVE` | `parameterId` | Chỉ tiêu "{tên}" đã ngừng sử dụng | Đề xuất |
| `RESULT_ALREADY_EFFECTIVE` | `parameterId` | Chỉ tiêu "{tên}" đã có kết quả đang hiệu lực | LT-BRD BR-06 |
| `INVALID_VALUE` | `value` | Vui lòng nhập giá trị | Đề xuất |
| `INVALID_VALUE` | `value` | Giá trị phải từ 0 trở lên | Đề xuất |
| `INVALID_VALUE` | `value` | Giá trị tối đa 8 chữ số phần nguyên và 4 chữ số thập phân | Đề xuất |
| (`typeMismatch.resultForm.value`) | `value` | Giá trị phải là số, phần thập phân dùng dấu chấm | Đề xuất, đặt trong `messages.properties` |
| (`typeMismatch.resultForm.parameterId`, `@NotNull`) | `parameterId` | Không tìm thấy chỉ tiêu | Đề xuất |
| `RESULT_NOT_FOUND` | | Không tìm thấy kết quả | Đề xuất |
| `RESULT_ALREADY_PROCESSED` | | Kết quả này đã được xử lý trước đó | LT-BRD BR-08 |
| `FOUR_EYES` | | Nguyên tắc 4 mắt: bạn không được duyệt hoặc từ chối kết quả do chính mình nhập | LT-BRD BR-08 |
| `REASON_REQUIRED` | `reason` | Lý do phải dài từ 5 đến 500 ký tự | LT-BRD BR-05, dùng lại cho BR-09 |

Lỗi Bean Validation của `ResultForm` không đi qua `GlobalControllerAdvice`. Controller tự lấy **một** lỗi rồi đổi thành flash `error`, phân giải qua `MessageSource` để thông báo `typeMismatch` lấy từ `messages.properties`; flash thêm `errorParameterId` để template tô đỏ đúng dòng. Khi có nhiều lỗi, chọn theo thứ tự: lỗi của `parameterId` → `typeMismatch` → `@NotNull` → `@DecimalMin` → `@Digits`. Làm vậy không cần try/catch trong controller.

### 2.3 Thứ tự kiểm tra trong service

Lỗi nào không phụ thuộc dữ liệu thì kiểm tra trước, để không giữ khóa khi không cần. Cách này giống `LabSampleService.cancel`.

**`enter(sampleId, NewResult, actor)`**, `@Transactional`:
1. Giá trị: `null` → "Vui lòng nhập giá trị"; `< 0` → "phải từ 0 trở lên"; `scale() > 4` hoặc phần nguyên quá 8 chữ số → "tối đa 8…4…". Scale âm thì đổi về `setScale(0)`.
2. `lockById(sampleId)`: không có → `SAMPLE_NOT_FOUND`.
3. Mẫu khác `IN_TESTING` → `SAMPLE_NOT_IN_TESTING`.
4. Chỉ tiêu không có → `PARAMETER_NOT_FOUND`; `active = false` → `PARAMETER_INACTIVE`.
5. Đã có kết quả hiệu lực cho (mẫu, chỉ tiêu) → `RESULT_ALREADY_EFFECTIVE`.
6. `replacesId` = bản `REJECTED` gần nhất của (mẫu, chỉ tiêu) chưa bị thay, hoặc `null`.
7. Chụp spec, tính verdict, `enteredAt = OffsetDateTime.now(clock)`, INSERT (`DuplicateKeyException` trên `uq_test_result_effective` → `RESULT_ALREADY_EFFECTIVE`).
8. INSERT audit `ENTER`; số dòng khác 1 thì `IllegalStateException`.

**`approve(sampleId, resultId, actor)`** và **`reject(sampleId, resultId, reason, actor)`**, `@Transactional`:
1. (chỉ `reject`) Lý do sau khi cắt khoảng trắng dài 5–500 ký tự, nếu không → `REASON_REQUIRED`.
2. `lockById(sampleId)`: không có → `SAMPLE_NOT_FOUND`.
3. Mẫu khác `IN_TESTING` → `SAMPLE_NOT_IN_TESTING`.
4. Kết quả không tồn tại, hoặc `sample_id` khác mẫu trong URL → `RESULT_NOT_FOUND`.
5. Kết quả khác `PENDING` → `RESULT_ALREADY_PROCESSED`.
6. `entered_by` = `actor` → `FOUR_EYES`.
7. `UPDATE … WHERE id = ? AND sample_id = ? AND status = 'PENDING'`, cập nhật 0 dòng → `RESULT_ALREADY_PROCESSED`.
8. INSERT audit `APPROVE`/`REJECT`; số dòng khác 1 thì `IllegalStateException`.

Theo thứ tự này, kết quả của chính mình mà đã có người khác xử lý thì nhận "đã được xử lý", không nhận "nguyên tắc 4 mắt".

### 2.4 Danh mục test

| TC | Lớp | Loại | Kiểm tra |
|---|---|---|---|
| TC-BR06-01 | `TestResultServiceTests` | U | Tham số hóa mẫu `RECEIVED`, `COMPLETED`, `CANCELLED` → `SAMPLE_NOT_IN_TESTING`, không INSERT, không audit |
| TC-BR06-02 | `TestResultServiceTests` | U | Chỉ tiêu không tồn tại; chỉ tiêu `active = false` → thông báo có tên chỉ tiêu |
| TC-BR06-03 | `TestResultServiceTests` | U | Tham số hóa. Hợp lệ: `0`, `0.0000`, `98.50`, `99999999.9999`, `1e3` (lưu `1000`). Bị chặn: `null`, `-0.0001`, `100000000`, `1.23456`, `98.50000` |
| TC-BR06-04 | `SampleControllerTests` | W | Gửi `value` = rỗng, `abc`, `98,5`, `-1`, `1.23456` → redirect `/samples/{id}`, flash `error` nguyên văn, có `errorParameterId`, service không được gọi |
| TC-BR06-05 | `TestResultConstraintTests` | DB | INSERT thẳng giá trị âm, scale 5, `100000000` → lỗi chứa `ck_test_result_value` |
| TC-BR06-06 | `TestResultServiceTests` | U | Đã có kết quả `PENDING`, đã có `APPROVED` → "Chỉ tiêu "pH" đã có kết quả đang hiệu lực"; chỉ có `REJECTED` → nhập được, `replacesId` đúng |
| TC-BR06-07 | `TestResultConstraintTests` | DB | 2 dòng hiệu lực cùng (mẫu, chỉ tiêu) → `uq_test_result_effective`; 1 `REJECTED` + 1 `PENDING` → ghi được |
| TC-BR06-08 | `TestResultConcurrencyTests` | CC | 2 luồng nhập cùng chỉ tiêu cùng mẫu → đúng 1 thành công, luồng kia nhận `RESULT_ALREADY_EFFECTIVE`; đúng 1 dòng audit `ENTER` |
| TC-BR06-09 | `TestResultIntegrationTests` | I | Nhập ASSAY `98.50`: đọc lại được `98.50` (`toPlainString`), `PENDING`, `PASS`, bản chụp `%`/95/105, `entered_by`, `entered_at` lệch +07:00 |
| TC-BR06-10 | `TestResultConcurrencyTests` | CC | Chiều 1: luồng A nhập kết quả và giữ transaction (latch), luồng B hủy mẫu thì bị chờ; A commit xong B mới hủy được, kết quả còn `PENDING`. Chiều 2: B hủy trước, A sau đó nhận `SAMPLE_NOT_IN_TESTING` |
| TC-BR06-11 | `TestResultIntegrationTests` | I | Chèn trong test một chỉ tiêu `active = false` → nhập bị chặn; trang dữ liệu (`findSheet`) không có dòng nhập cho nó, trừ khi mẫu đã có kết quả |
| TC-BR07-01 | `SpecTest` | U | Tham số hóa biên của 5 chỉ tiêu V4: ASSAY 94.9999 / 95 / 105 / 105.0001 / **105.04 → Không đạt**; PH 4.4999 / 4.5 / 7 / 7.0001; LOD 0 / 0.5 / 0.5001; DISSOLUTION 79.9999 / 80 / 99999999.9999; IMPURITY_TOTAL 1.0 / 1.0001. Nhãn tiêu chuẩn "95 – 105", "≤ 0.5", "≥ 80" |
| TC-BR07-02 | `SampleControllerTests` | W | POST có thêm `verdict=PASS` cho giá trị Không đạt → `NewResult` (bắt bằng `ArgumentCaptor`) chỉ có `parameterId`, `value` |
| TC-BR07-03 | `TestResultConstraintTests` | DB | INSERT `105.04` + `PASS`; `80` + `FAIL` với spec ≥ 80 → `ck_test_result_verdict_spec` |
| TC-BR07-04 | `TestResultIntegrationTests` | I | Nhập xong, `UPDATE test_parameter` đổi spec ASSAY ngay trong test → kết quả cũ giữ spec và verdict cũ; nhập cho mẫu khác thì dùng spec mới |
| TC-BR08-01 | `TestResultServiceTests` | U | `approve` và `reject` kết quả do chính `actor` nhập → `FOUR_EYES`, không UPDATE, không audit |
| TC-BR08-02 | `TestResultServiceTests` | U | Kết quả `APPROVED`, `REJECTED`; và UPDATE trả 0 dòng → "Kết quả này đã được xử lý trước đó" |
| TC-BR08-03 | `TestResultServiceTests` | U | Mẫu `COMPLETED`, `CANCELLED` → "Chỉ duyệt hoặc từ chối kết quả của mẫu đang kiểm nghiệm" |
| TC-BR08-04 | `TestResultServiceTests` | U | `resultId` không tồn tại; `resultId` thuộc mẫu khác → "Không tìm thấy kết quả" |
| TC-BR08-05 | `TestResultConstraintTests` | DB | `UPDATE` thẳng `reviewed_by = entered_by` → `ck_four_eyes` |
| TC-BR08-06 | `SampleControllerTests` | W | `@WithAppUser("duyet", REVIEWER)`: kết quả do `duyet` nhập có nút `disabled` và câu giải thích; kết quả do `kiemnghiem1` nhập có nút bật; với ANALYST thì không có nút |
| TC-BR08-07 | `TestResultConcurrencyTests` | CC | `duyet` duyệt và `duyet2` từ chối cùng một kết quả cùng lúc → 1 thành công, 1 nhận `RESULT_ALREADY_PROCESSED`; đúng 1 dòng audit APPROVE hoặc REJECT |
| TC-BR08-08 | `TestResultIntegrationTests` + `SampleControllerTests` | I + W | Hủy mẫu còn kết quả `PENDING` → kết quả vẫn `PENDING`, số dòng audit không đổi; trang chi tiết mẫu đã hủy không có ô nhập, nút Duyệt/Từ chối, mà có ghi chú R15 |
| TC-BR09-01 | `TestResultServiceTests` | U | Lý do dài 4 / 5 / 500 / 501 ký tự, chuỗi chỉ có khoảng trắng, `null`; lý do được lưu đã cắt khoảng trắng |
| TC-BR09-02 | `TestResultIntegrationTests` | I | Nhập → từ chối → nhập lại: còn 2 dòng; dòng cũ `REJECTED` kèm lý do, `reviewed_by`, `reviewed_at`; dòng mới `PENDING`, `replaces_id` = dòng cũ; chỉ dòng mới hiệu lực |
| TC-BR09-03 | `TestResultConstraintTests` | DB | `REJECTED` thiếu lý do hoặc lý do `'  sai  '`; `APPROVED` có lý do → `ck_test_result_reject_reason`. Thay một bản bị từ chối lần thứ hai → `uq_test_result_replaces`. `replaces_id` trỏ sang mẫu hoặc chỉ tiêu khác → `fk_test_result_replaces` |
| TC-BR09-04 | `SampleControllerTests` | W | Dòng bị từ chối có lớp `text-decoration-line-through`, hiện lý do và người từ chối; bản nhập lại hiện "Lần 2" |
| TC-BR10-01 | `TestResultIntegrationTests` | I | Nhập, duyệt, từ chối: mỗi thao tác đúng 1 dòng, `action`/`actor` đúng, `acted_at` lệch +07:00, `detail->>'value'` = `98.50`, `detail->>'status'` khớp trạng thái sau thao tác |
| TC-BR10-02 | `TestResultIntegrationTests` | I | Các thao tác thất bại (4 mắt, đã xử lý, mẫu không Đang KN, trùng hiệu lực) → số dòng audit không đổi |
| TC-BR10-03 | `TestResultIntegrationTests` | I | `@Transactional(propagation = NOT_SUPPORTED)` + `@MockitoSpyBean ResultAuditMapper` ném lỗi khi insert → không có kết quả mới, kết quả được duyệt vẫn `PENDING` (dữ liệu rollback nên không phải dọn) |
| TC-BR10-04 | `TestResultConstraintTests` | DB | `UPDATE`, `DELETE`, `TRUNCATE result_audit` → lỗi chứa `tg_result_audit_append_only` / `tg_result_audit_no_truncate` |
| TC-BR10-05 | `TestResultConstraintTests` | DB | `DELETE test_result` → `tg_test_result_no_delete`; đổi `value` (cả `98.50` → `98.5`), `entered_by`; `APPROVED` → `PENDING`; `REJECTED` → `APPROVED` → `tg_test_result_guard_update`; `PENDING` → `APPROVED` đủ cột thì ghi được |
| TC-BR10-06 | `SampleControllerTests` | W | Tham số hóa 4 vai trò: trang chi tiết có mục "Kết quả kiểm nghiệm" và "Nhật ký kết quả", có dòng nhật ký mock |
| TC-BR10-07 | `ResultFlowIntegrationTests` | H | Luồng trọn ở mục 5.2 |
| TC-BR12-05 | `AppUserMapperTests` | I | Sửa: 6 tài khoản, thêm dòng `duyet2, REVIEWER`; sửa `@DisplayName` |
| TC-BR12-15 | `AccessControlTests` | W | `POST /samples/1/results`: ANALYST, REVIEWER qua lớp bảo mật; RECEPTIONIST, MANAGER → 403 |
| TC-BR12-16 | `AccessControlTests` | W | `POST …/results/2/approve` và `…/reject`: chỉ REVIEWER qua; ANALYST, RECEPTIONIST, MANAGER → 403 |
| TC-BR12-17 | `AccessControlTests` | W | 3 endpoint: chưa đăng nhập → redirect `/login`; thiếu CSRF → 403 |

## 3. Data model

### 3.1 Quan hệ và trạng thái

```
lab_sample 1 ──< test_result >── 1 test_parameter
                   │  └─ replaces_id ──> test_result (bản bị từ chối, cùng mẫu + chỉ tiêu)
                   └──< result_audit
```

Trạng thái kết quả: `PENDING` (Chờ duyệt) → `APPROVED` (Đã duyệt) hoặc `REJECTED` (Đã từ chối). Hai trạng thái sau là kết thúc. **Hiệu lực** = `PENDING` hoặc `APPROVED` (LT-BRD mục 5).

| Enum Java | Hằng (ghi nguyên văn vào cột có CHECK) | Nhãn |
|---|---|---|
| `ResultStatus` | `PENDING`, `APPROVED`, `REJECTED` | Chờ duyệt, Đã duyệt, Đã từ chối |
| `Verdict` | `PASS`, `FAIL` | Đạt, Không đạt |
| `ResultAction` | `ENTER`, `APPROVE`, `REJECT` | Nhập, Duyệt, Từ chối |

### 3.2 `V4__create_test_result.sql`

```sql
-- BR-06, BR-07: danh mục chỉ tiêu (LT-BRD mục 11). Phiên bản 1.0 không có màn hình quản lý chỉ tiêu
-- (LT-BRD mục 3): thêm chỉ tiêu, đổi tiêu chuẩn hay ngừng dùng chỉ tiêu đều bằng migration mới.
CREATE TABLE test_parameter (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(30)  NOT NULL
                  CONSTRAINT uq_test_parameter_code UNIQUE
                  CONSTRAINT ck_test_parameter_code CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    name          VARCHAR(200) NOT NULL
                  CONSTRAINT ck_test_parameter_name CHECK (btrim(name) <> ''),
    unit          VARCHAR(20)  NOT NULL
                  CONSTRAINT ck_test_parameter_unit CHECK (btrim(unit) <> ''),
    -- NUMERIC không cố định scale để giữ đúng cách viết của tiêu chuẩn: "1.0" chứ không thành "1.0000".
    -- NULL = không giới hạn phía đó (BR-07).
    spec_min      NUMERIC
                  CONSTRAINT ck_test_parameter_spec_min CHECK (spec_min >= 0 AND scale(spec_min) <= 4),
    spec_max      NUMERIC
                  CONSTRAINT ck_test_parameter_spec_max CHECK (spec_max >= 0 AND scale(spec_max) <= 4),
    -- BR-06: chỉ nhập kết quả cho chỉ tiêu đang được dùng.
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order SMALLINT     NOT NULL
                  CONSTRAINT uq_test_parameter_display_order UNIQUE,
    -- Chỉ tiêu không có biên nào thì mọi giá trị đều Đạt, tức là không kiểm tra gì.
    CONSTRAINT ck_test_parameter_spec_bound CHECK (spec_min IS NOT NULL OR spec_max IS NOT NULL),
    CONSTRAINT ck_test_parameter_spec_range CHECK (spec_min IS NULL OR spec_max IS NULL OR spec_min <= spec_max)
);

-- Dữ liệu tham chiếu, không phải dữ liệu demo: nằm trong migration chứ không ở db/devdata.
INSERT INTO test_parameter (code, name, unit, spec_min, spec_max, display_order)
VALUES ('ASSAY',          'Định lượng hoạt chất',      '%',  95,   105,  1),
       ('PH',             'pH',                        'pH', 4.5,  7,    2),
       ('LOD',            'Mất khối lượng do làm khô', '%',  NULL, 0.5,  3),
       ('DISSOLUTION',    'Độ hòa tan (30 phút)',      '%',  80,   NULL, 4),
       ('IMPURITY_TOTAL', 'Tạp chất tổng',             '%',  NULL, 1.0,  5);

-- BR-06..BR-09: kết quả kiểm nghiệm. Không xóa cứng (NFR-03): bản bị từ chối được giữ lại,
-- bản nhập lại là một dòng mới trỏ về bản cũ qua replaces_id.
CREATE TABLE test_result (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sample_id     BIGINT       NOT NULL
                  CONSTRAINT fk_test_result_sample REFERENCES lab_sample (id) ON DELETE RESTRICT,
    parameter_id  BIGINT       NOT NULL
                  CONSTRAINT fk_test_result_parameter REFERENCES test_parameter (id) ON DELETE RESTRICT,
    -- BR-06: ≥ 0, tối đa 8 chữ số phần nguyên và 4 chữ số thập phân. Không dùng NUMERIC(12,4): kiểu đó
    -- làm tròn ngầm 1.23456 thành 1.2346 thay vì từ chối, và đổi 98.50 người dùng nhập thành 98.5000.
    value         NUMERIC      NOT NULL
                  CONSTRAINT ck_test_result_value
                  CHECK (value >= 0 AND value < 100000000 AND scale(value) <= 4),
    -- Tiêu chuẩn chụp từ test_parameter lúc nhập (ALCOA+): migration sau này đổi danh mục thì kết quả
    -- cũ vẫn mang đúng tiêu chuẩn đã dùng để đánh giá nó.
    unit          VARCHAR(20)  NOT NULL
                  CONSTRAINT ck_test_result_unit CHECK (btrim(unit) <> ''),
    spec_min      NUMERIC,
    spec_max      NUMERIC,
    verdict       VARCHAR(10)  NOT NULL
                  CONSTRAINT ck_test_result_verdict CHECK (verdict IN ('PASS', 'FAIL')),
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                  CONSTRAINT ck_test_result_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    entered_by    VARCHAR(50)  NOT NULL
                  CONSTRAINT ck_test_result_entered_by CHECK (btrim(entered_by) <> ''),
    entered_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_by   VARCHAR(50)
                  CONSTRAINT ck_test_result_reviewed_by CHECK (btrim(reviewed_by) <> ''),
    reviewed_at   TIMESTAMPTZ,
    reject_reason VARCHAR(500),
    -- BR-09: bản nhập lại trỏ về bản bị từ chối mà nó thay. UNIQUE: mỗi bản bị từ chối chỉ được thay một lần,
    -- nên hai người nhập lại cùng lúc thì một người bị chặn.
    replaces_id   BIGINT
                  CONSTRAINT uq_test_result_replaces UNIQUE,
    -- Đích của fk_test_result_replaces: bản thay phải cùng mẫu, cùng chỉ tiêu với bản bị thay.
    CONSTRAINT uq_test_result_identity UNIQUE (id, sample_id, parameter_id),
    CONSTRAINT fk_test_result_replaces FOREIGN KEY (replaces_id, sample_id, parameter_id)
        REFERENCES test_result (id, sample_id, parameter_id) ON DELETE RESTRICT,
    CONSTRAINT ck_test_result_replaces_self CHECK (replaces_id <> id),
    CONSTRAINT ck_test_result_spec_range CHECK (spec_min IS NULL OR spec_max IS NULL OR spec_min <= spec_max),
    -- BR-07: verdict phải khớp giá trị và tiêu chuẩn đã chụp. So nguyên văn, tính cả biên, biên NULL là
    -- không giới hạn. Service tự tính verdict; ràng buộc này chặn mọi đường ghi khác.
    CONSTRAINT ck_test_result_verdict_spec CHECK (verdict = CASE
        WHEN (spec_min IS NULL OR value >= spec_min) AND (spec_max IS NULL OR value <= spec_max) THEN 'PASS'
        ELSE 'FAIL' END),
    -- BR-08: nguyên tắc 4 mắt. Tên ràng buộc theo đúng LT-BRD.
    CONSTRAINT ck_four_eyes CHECK (reviewed_by IS NULL OR reviewed_by <> entered_by),
    -- Chờ duyệt ⇔ chưa có người xử lý; người xử lý và thời điểm xử lý luôn đi cùng nhau.
    CONSTRAINT ck_test_result_review
        CHECK ((status = 'PENDING') = (reviewed_by IS NULL) AND (reviewed_by IS NULL) = (reviewed_at IS NULL)),
    -- BR-09: Đã từ chối ⇔ có lý do 5–500 ký tự sau khi cắt khoảng trắng (cận trên do VARCHAR(500) chặn).
    CONSTRAINT ck_test_result_reject_reason
        CHECK ((status = 'REJECTED') = (reject_reason IS NOT NULL)
               AND (reject_reason IS NULL OR char_length(btrim(reject_reason)) >= 5))
);

-- BR-06: mỗi chỉ tiêu trên một mẫu chỉ có 1 kết quả hiệu lực (Chờ duyệt hoặc Đã duyệt), kể cả khi
-- hai người nhập cùng lúc. Bản Đã từ chối nằm ngoài index nên vẫn nhập lại được.
CREATE UNIQUE INDEX uq_test_result_effective ON test_result (sample_id, parameter_id)
    WHERE status IN ('PENDING', 'APPROVED');

-- Trang chi tiết đọc mọi kết quả của một mẫu (UC-12).
CREATE INDEX ix_test_result_sample ON test_result (sample_id, parameter_id, id);

-- BR-10: audit trail kết quả, chỉ thêm (NFR-03). Không lưu sample_id: trang chi tiết JOIN qua test_result,
-- nên không có cột nào có thể lệch với kết quả mà dòng audit nói tới.
CREATE TABLE result_audit (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    result_id BIGINT      NOT NULL
              CONSTRAINT fk_result_audit_result REFERENCES test_result (id) ON DELETE RESTRICT,
    action    VARCHAR(10) NOT NULL
              CONSTRAINT ck_result_audit_action CHECK (action IN ('ENTER', 'APPROVE', 'REJECT')),
    -- NFR-03 (ALCOA+, chữ A là Attributable).
    actor     VARCHAR(50) NOT NULL
              CONSTRAINT ck_result_audit_actor CHECK (btrim(actor) <> ''),
    acted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Ảnh chụp dòng test_result ngay sau thao tác, dựng bằng jsonb_build_object trong ResultAuditMapper.xml.
    detail    JSONB       NOT NULL
              CONSTRAINT ck_result_audit_detail CHECK (jsonb_typeof(detail) = 'object')
);

CREATE INDEX ix_result_audit_result ON result_audit (result_id, acted_at);

-- NFR-03, BR-10: chặn ở CSDL để cả SQL ghi thẳng cũng không sửa, xóa được dữ liệu chỉ thêm.
-- ERRCODE restrict_violation (23001) thuộc lớp 23, nên Spring đổi thành DataIntegrityViolationException.
-- Thông báo mở đầu bằng tên trigger để test so khớp.
CREATE FUNCTION forbid_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '%: không được % trên bảng %', TG_NAME, TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER tg_result_audit_append_only
    BEFORE UPDATE OR DELETE ON result_audit
    FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TRIGGER tg_result_audit_no_truncate
    BEFORE TRUNCATE ON result_audit
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_change();

CREATE TRIGGER tg_test_result_no_delete
    BEFORE DELETE ON test_result
    FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TRIGGER tg_test_result_no_truncate
    BEFORE TRUNCATE ON test_result
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_change();

-- BR-08, BR-09, NFR-03: một kết quả chỉ được đổi đúng một lần, từ Chờ duyệt sang Đã duyệt hoặc Đã từ chối,
-- và chỉ ở 4 cột của bước xử lý (status, reviewed_by, reviewed_at, reject_reason). Số được so ở dạng text
-- để cả việc đổi 98.50 thành 98.5 (bằng nhau về giá trị) cũng bị chặn.
CREATE FUNCTION guard_test_result_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status <> 'PENDING' OR NEW.status NOT IN ('APPROVED', 'REJECTED')
       OR (NEW.id, NEW.sample_id, NEW.parameter_id, NEW.value::text, NEW.unit, NEW.spec_min::text,
           NEW.spec_max::text, NEW.verdict, NEW.entered_by, NEW.entered_at, NEW.replaces_id)
          IS DISTINCT FROM
          (OLD.id, OLD.sample_id, OLD.parameter_id, OLD.value::text, OLD.unit, OLD.spec_min::text,
           OLD.spec_max::text, OLD.verdict, OLD.entered_by, OLD.entered_at, OLD.replaces_id)
    THEN
        RAISE EXCEPTION '%: kết quả % chỉ được chuyển một lần từ PENDING sang APPROVED hoặc REJECTED', TG_NAME, OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_test_result_guard_update
    BEFORE UPDATE ON test_result
    FOR EACH ROW EXECUTE FUNCTION guard_test_result_update();
```

### 3.3 Đã chạy thử

Ngày 23/10/2026: chạy V1–V4 trên PostgreSQL 18.4 (Windows, cổng 5433, CSDL `labtrack_test`), trong một schema tạm bọc bởi `BEGIN … ROLLBACK`, nên không để lại gì.

- Các ca hợp lệ ghi được: 5 chỉ tiêu (đọc lại `1.0` vẫn là `1.0`), `98.50` đọc lại là `98.50`, `99999999.9999`, `105.04` + `FAIL`, từ chối có lý do, duyệt bởi người khác, nhập lại có `replaces_id`, `FOR UPDATE` trên `lab_sample`. `jsonb_build_object` giữ nguyên `98.50` và `6.30` trong `detail`.
- 32 ca vi phạm đều bị chặn đúng tên: `ck_test_result_value` (×4: âm, 5 chữ số thập phân, `6.50000`, `1e8`), `ck_test_result_verdict_spec` (×2), `uq_test_result_effective`, `ck_four_eyes`, `ck_test_result_review` (×2), `ck_test_result_reject_reason` (×3), `tg_test_result_guard_update` (×5: đổi value, `98.50` → `98.5`, đổi `entered_by`, `REJECTED` → `APPROVED`, `APPROVED` → `PENDING`), `tg_test_result_no_delete`, `tg_test_result_no_truncate`, `uq_test_result_replaces`, `fk_test_result_replaces`, `tg_result_audit_append_only` (UPDATE, DELETE), `tg_result_audit_no_truncate`, `ck_result_audit_detail`, `ck_result_audit_action`, `ck_result_audit_actor`, `ck_test_parameter_spec_bound`, `ck_test_parameter_spec_range`, xóa chỉ tiêu đang được dùng, xóa mẫu.
- SQLSTATE: CHECK 23514, UNIQUE 23505 (Spring: `DuplicateKeyException`), FK 23503, trigger và `ON DELETE RESTRICT` 23001. Tất cả thuộc lớp 23, nên Spring đổi thành `DataIntegrityViolationException`.

### 3.4 Câu SQL chính trong mapper

```xml
<!-- LabSampleMapper.xml. R14: khóa dòng mẫu tới hết transaction. flushCache để lần đọc này luôn đi xuống CSDL. -->
<select id="lockById" resultType="LabSample" flushCache="true" useCache="false">
    SELECT <include refid="columns"/>
    FROM lab_sample
    WHERE id = #{id}
    FOR UPDATE
</select>
```

```sql
-- TestResultMapper.review (R8): 0 dòng = người khác đã xử lý trước.
UPDATE test_result
SET status = #{toStatus}, reviewed_by = #{actor}, reviewed_at = #{reviewedAt}, reject_reason = #{rejectReason}
WHERE id = #{resultId} AND sample_id = #{sampleId} AND status = 'PENDING';

-- TestResultMapper.findBySample (R11, R17): "Lần n" đánh số theo thứ tự nhập của từng chỉ tiêu.
SELECT r.*, p.code AS parameter_code, p.name AS parameter_name, p.active AS parameter_active,
       row_number() OVER (PARTITION BY r.parameter_id ORDER BY r.id) AS attempt_no
FROM test_result r
JOIN test_parameter p ON p.id = r.parameter_id
WHERE r.sample_id = #{sampleId}
ORDER BY p.display_order, r.id;

-- ResultAuditMapper.insert (R12, D12): chụp dòng kết quả ngay sau thao tác. Service kiểm tra số dòng = 1.
INSERT INTO result_audit (result_id, action, actor, acted_at, detail)
SELECT r.id, #{action}, #{actor}, #{actedAt},
       jsonb_build_object('parameter', p.code, 'value', r.value, 'unit', r.unit,
                          'specMin', r.spec_min, 'specMax', r.spec_max, 'verdict', r.verdict,
                          'status', r.status, 'rejectReason', r.reject_reason, 'replacesId', r.replaces_id)
FROM test_result r
JOIN test_parameter p ON p.id = r.parameter_id
WHERE r.id = #{resultId};

-- ResultAuditMapper.findBySample (R17)
SELECT a.id, a.action, a.actor, a.acted_at, p.name AS parameter_name,
       a.detail ->> 'value' AS value, a.detail ->> 'unit' AS unit,
       a.detail ->> 'verdict' AS verdict, a.detail ->> 'rejectReason' AS reject_reason
FROM result_audit a
JOIN test_result r ON r.id = a.result_id
JOIN test_parameter p ON p.id = r.parameter_id
WHERE r.sample_id = #{sampleId}
ORDER BY a.acted_at, a.id;
```

### 3.5 Dữ liệu demo

Thêm vào `db/devdata/R__dev_seed.sql`, dùng cùng hash `Passw0rd!` và giữ `ON CONFLICT … DO UPDATE` để file chạy lại bao nhiêu lần cũng ra cùng kết quả:

```sql
('duyet2', 'Đỗ Thị Lan', '{bcrypt}$2a$10$hbR7RS/2BOVupE7seDYmruEfQhomYdrcagZP0uSyViN4vNz83ainK', 'REVIEWER', TRUE)
```

### 3.6 Vì sao chọn như vậy

- **Khóa dòng mẫu thay vì trigger kiểm tra trạng thái mẫu:** quy tắc nằm trong service, test được bằng unit test, và cùng một khóa phục vụ được cả BR-11 sau này. Chỉ dùng điều kiện `WHERE status = …` thì không đủ, vì INSERT kết quả không cập nhật dòng mẫu.
- **Không có deadlock:** mọi thao tác (nhập, duyệt, từ chối, hủy mẫu) đều lấy khóa dòng `lab_sample` trước tiên, rồi mới đụng tới `test_result` và `result_audit`.
- **Audit dựng bằng SQL** (`INSERT … SELECT jsonb_build_object`): chi tiết audit chính là dòng đã lưu trong CSDL, không phải bản sao do Java tự dựng. Tầng service cũng không cần Jackson hay `TypeHandler` cho JSONB.
- **FK ghép `(replaces_id, sample_id, parameter_id)`**: CSDL tự bảo đảm bản nhập lại cùng mẫu và cùng chỉ tiêu với bản nó thay, không cần trigger.

### 3.7 Lưu ý triển khai và rủi ro

| # | Rủi ro / lưu ý | Cách xử lý |
|---|---|---|
| L1 | Trigger chặn DELETE nên dữ liệu mà test đã commit (CC, H) **không xóa được nữa**. Profile `localdb` dùng CSDL test cố định nên dữ liệu tích lũy qua các lần chạy | Test chỉ đếm trên mẫu do chính nó tạo, không đếm toàn bảng. Ghi vào CLAUDE.md mục *Bẫy* |
| L2 | Máy chủ PostgreSQL có thể trả thông báo lỗi theo ngôn ngữ hệ điều hành (máy dev hiện tại trả **tiếng Nhật**) | Test so theo tên ràng buộc hoặc trigger (`hasMessageContaining("ck_four_eyes")`), không so câu tiếng Anh |
| L3 | `@Digits` của Hibernate Validator có thể bỏ số 0 cuối trước khi đếm, khiến `98.50000` lọt qua form | Service kiểm tra `scale()` trên giá trị gốc; TC-BR06-03 (U) giữ ca này; TC-BR06-04 (W) không dựa vào nó |
| L4 | `SampleController` có thêm dependency `TestResultService` | `SampleControllerTests` và `AccessControlTests` phải thêm `@MockitoBean TestResultService`; Mockito trả list rỗng nên các test cũ không phải sửa gì thêm |
| L5 | Tên hằng `ResultStatus`, `Verdict`, `ResultAction` được ghi vào cột có CHECK và được so dạng chuỗi trong template | Ghi vào CLAUDE.md mục *Bẫy*, như `SampleStatus` |
| L6 | `lockById` là `<select>`: không có `flushCache="true" useCache="false"` thì lần gọi thứ hai trong cùng transaction có thể nhận dữ liệu từ cache của MyBatis | Khai báo như ở 3.4 |
| L7 | pgjdbc trả `TIMESTAMPTZ` theo UTC | `findSheet`, `findAudit` đổi `entered_at`, `reviewed_at`, `acted_at` về múi giờ của `Clock`, như `toClockZone` |
| L8 | Môi trường thật chỉ có 1 người duyệt thì kết quả do người đó nhập sẽ kẹt (R16 chỉ lo cho dữ liệu demo) | Câu hỏi mở 6.10 |
| L9 | `R__dev_seed.sql` đổi nội dung nên Flyway chạy lại nó ở lần khởi động sau | File đã viết idempotent; TC-BR12-05 phát hiện được hash dán sai |
| L10 | Gửi form ở tab cũ sau khi người khác đã xử lý kết quả | Người gửi sau nhận "Kết quả này đã được xử lý trước đó" (R8) hoặc "đã có kết quả đang hiệu lực" (R4); không phải lỗi hệ thống |

## 4. Màn hình và URL

### 4.1 URL

| Method | URL | Vai trò | Tham số | Thành công | Lỗi |
|---|---|---|---|---|---|
| GET | `/samples/{id}` | Mọi vai trò đã đăng nhập (có sẵn) | | Trang chi tiết có thêm `resultSheet`, `resultAudit` | 404 nếu không có mẫu (có sẵn) |
| POST | `/samples/{id}/results` | ANALYST, REVIEWER (luật có sẵn) | `parameterId`, `value`, `_csrf` | Redirect `/samples/{id}`, flash `message` = "Đã nhập kết quả pH: 6.2 pH (Đạt)" | Redirect `/samples/{id}`, flash `error` (+ `errorParameterId` khi lỗi form) |
| POST | `/samples/{id}/results/{resultId}/approve` | REVIEWER (luật mới) | `_csrf` | Flash "Đã duyệt kết quả pH" | Flash `error` qua `GlobalControllerAdvice` |
| POST | `/samples/{id}/results/{resultId}/reject` | REVIEWER (luật mới) | `reason`, `_csrf` | Flash "Đã từ chối kết quả pH" | Flash `error` qua `GlobalControllerAdvice` |

Biến đường dẫn của mẫu phải tên `id`, vì `GlobalControllerAdvice` dùng nó để quay về đúng trang chi tiết. Luật thêm vào `SecurityConfig`, cạnh luật `cancel`:

```java
.requestMatchers(HttpMethod.POST, "/samples/*/results/*/approve", "/samples/*/results/*/reject")
.hasRole(REVIEWER.name())
```

`/samples/*/results` không khớp với `/samples/1/results/2/approve`, vì `*` chỉ khớp một đoạn đường dẫn. Hai luật không chồng lên nhau.

### 4.2 Mục "Kết quả kiểm nghiệm" trên trang chi tiết

Nằm ngay sau phần thông tin mẫu, trước *Lịch sử trạng thái*. Bảng nằm trong `table-responsive` để dùng được ở màn hình 360 px (NFR-08).

```
Kết quả kiểm nghiệm
┌──────────────────────┬──────────┬────────────────────┬──────────┬────────────┬───────────────────────┬─────────────────────┐
│ Chỉ tiêu             │ Tiêu chuẩn│ Kết quả            │ Đánh giá │ Trạng thái │ Người nhập · lúc      │ Thao tác            │
├──────────────────────┼──────────┼────────────────────┼──────────┼────────────┼───────────────────────┼─────────────────────┤
│ Định lượng hoạt chất │ 95 – 105 │ 98.50 %            │ [Đạt]    │ [Chờ duyệt]│ kiemnghiem1 · 23/10…  │ [Duyệt] [Từ chối]   │
│ pH                   │ 4.5 – 7  │ 6.30 pH  (Lần 2)   │ [Đạt]    │ [Đã duyệt] │ kiemnghiem2 · …       │                     │
│   └ ~~6.2 pH~~ Đã từ chối · duyet · "Sai độ pha loãng"                                                              │
│ Mất khối lượng…      │ ≤ 0.5    │ [______] %  [Lưu]  │          │            │                       │                     │
└──────────────────────┴──────────┴────────────────────┴──────────┴────────────┴───────────────────────┴─────────────────────┘
```

| Điều kiện | Hiển thị |
|---|---|
| Danh sách dòng | Mỗi chỉ tiêu `active` một dòng, theo `display_order`; thêm các chỉ tiêu đã ngừng dùng nhưng mẫu có kết quả (R2) |
| Có kết quả hiệu lực | Giá trị `value.toPlainString()` + đơn vị chụp; nhãn đánh giá (`verdictBadge`: Đạt xanh lá, Không đạt đỏ); nhãn trạng thái (`resultStatusBadge`); người nhập và thời điểm; "Lần n" khi n > 1 |
| Không có kết quả hiệu lực, mẫu *Đang KN*, chỉ tiêu active, vai trò ANALYST/REVIEWER | Form `th:action="@{/samples/{id}/results(id=${sample.id})}"`: hidden `parameterId`, `<input type="number" min="0" step="0.0001" inputmode="decimal" required>` có `<label class="visually-hidden">` "Giá trị {tên} ({đơn vị})", nút "Lưu". Ô có `is-invalid` nếu `errorParameterId` trùng chỉ tiêu |
| Không có kết quả hiệu lực, các trường hợp khác | "Chưa có kết quả" |
| Kết quả `PENDING`, mẫu *Đang KN*, vai trò REVIEWER, `entered_by` ≠ `#authentication.name` | Nút "Duyệt" (form POST `…/approve`) và "Từ chối" (mở modal riêng của kết quả đó) |
| Như trên nhưng `entered_by` = `#authentication.name` | Hai nút có `disabled` và `aria-describedby` trỏ tới dòng chữ nhỏ "Bạn đã nhập kết quả này; cần người duyệt khác (nguyên tắc 4 mắt)" (R7) |
| Kết quả `REJECTED` | Dòng phụ dưới chỉ tiêu: giá trị trong `text-decoration-line-through`, nhãn "Đã từ chối", người từ chối, thời điểm, lý do (R11) |
| Mẫu *Đã hủy* và còn kết quả `PENDING` | Không có ô nhập hay nút; ghi chú "Mẫu đã hủy, kết quả chờ duyệt không còn được xử lý" (R15) |

**Modal từ chối:** mỗi kết quả `PENDING` có một modal riêng (tối đa 5), `id="rejectModal-{resultId}"`, nên không cần JS để gắn `resultId`. Nhãn "Lý do từ chối (5–500 ký tự)", `<textarea name="reason" required minlength="5" maxlength="500">`, ghi chú "Lý do được lưu cùng kết quả và không sửa được sau đó". Focus vào ô lý do khi modal mở, giống modal hủy mẫu.

**Hiển thị tiêu chuẩn** (`Spec.label()`): có cả hai biên → "95 – 105"; chỉ có cận trên → "≤ 0.5"; chỉ có cận dưới → "≥ 80". Số in bằng `toPlainString()` (`1.0` vẫn là `1.0`).

### 4.3 Mục "Nhật ký kết quả"

Nằm sau *Lịch sử trạng thái*; mọi vai trò xem được (R17). Cột: Thời điểm (`dd/MM/yyyy HH:mm:ss`, giờ VN) · Người thực hiện · Thao tác (Nhập / Duyệt / Từ chối) · Chỉ tiêu · Chi tiết. Cột chi tiết: Nhập → "98.50 % – Đạt"; Duyệt → "Đã duyệt"; Từ chối → "Lý do: …". Sắp cũ trước mới sau. Không có dòng nào thì hiện "Chưa có thao tác nào trên kết quả".

## 5. Kiểm chứng end-to-end

### 5.1 Tự động

```powershell
./mvnw verify
# hoặc từng nhóm:
./mvnw -q test "-Dtest=SpecTest,TestResultServiceTests,SampleControllerTests,AccessControlTests"
./mvnw -q test "-Dtest=TestResultIntegrationTests,TestResultConstraintTests,TestResultConcurrencyTests,ResultFlowIntegrationTests,AppUserMapperTests"
```

Máy không có Docker thì chạy với profile `localdb` trên `labtrack_test`, như hướng dẫn trong CLAUDE.md (nhớ L1).

### 5.2 TC-BR10-07: luồng trọn qua HTTP thật

`ResultFlowIntegrationTests`, `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Import(TestcontainersConfiguration.class)`. Mỗi người dùng một `HttpClient` có `CookieManager` riêng; lấy CSRF token từ trang như `ForbiddenPageIntegrationTests`; id chỉ tiêu đọc bằng `TestParameterMapper` theo `code`.

| # | Người | Thao tác | Mong đợi |
|---|---|---|---|
| 1 | `tiepnhan` | POST `/samples` | 302 tới `/samples/{id}` |
| 2 | `kiemnghiem1` | POST `/samples/{id}/start` | Mẫu *Đang kiểm nghiệm* |
| 3 | `kiemnghiem1` | POST `/results` PH = `6.2` | Trang chi tiết có "Đã nhập kết quả pH: 6.2 pH (Đạt)" |
| 4 | `kiemnghiem1` | POST `/results/{rPH}/approve` | 403 |
| 5 | `duyet` | POST `/results/{rPH}/reject` với lý do `Sai độ pha loãng` | "Đã từ chối kết quả pH" |
| 6 | `kiemnghiem1` | POST `/results` PH = `6.30` | Trang có `6.2` bị gạch ngang và "Lần 2" |
| 7 | `duyet` | POST `/results` ASSAY = `98.50` | Trang của `duyet`: nút Duyệt của ASSAY có `disabled` |
| 8 | `duyet` | POST `/results/{rASSAY}/approve` | Flash "Nguyên tắc 4 mắt: bạn không được duyệt hoặc từ chối kết quả do chính mình nhập" |
| 9 | `duyet2` | Duyệt `6.30` (PH) và ASSAY | "Đã duyệt kết quả …" ×2 |
| 10 | `quanly` | GET `/samples/{id}` | Có `98.50` và `6.30`, 2 nhãn "Đã duyệt"; *Nhật ký kết quả* có đúng **6** dòng: Nhập, Từ chối, Nhập, Nhập, Duyệt, Duyệt (bước 4 và 8 thất bại nên không có dòng) |

### 5.3 Kịch bản tay

Chạy `./mvnw spring-boot:run`, mở http://localhost:8080/samples. Dùng các cửa sổ ẩn danh hoặc profile trình duyệt khác nhau cho từng tài khoản (mật khẩu `Passw0rd!`).

| # | Bước | Mong đợi |
|---|---|---|
| 1 | Làm lại các bước 1–10 ở 5.2 trên giao diện | Như 5.2; thông báo tiếng Việt hiện ở khung xanh hoặc đỏ đầu trang |
| 2 | `kiemnghiem1` nhập pH `98,5`, rồi `-1`, rồi gửi ô trống bằng DevTools (bỏ `required`) | Lỗi đỏ tương ứng ở 2.2, dòng pH viền đỏ |
| 3 | Hai cửa sổ `duyet` và `duyet2` cùng mở một kết quả *Chờ duyệt* của `kiemnghiem1`; `duyet` bấm Duyệt, ngay sau đó `duyet2` bấm Từ chối | `duyet2` nhận "Kết quả này đã được xử lý trước đó"; nhật ký chỉ có 1 dòng Duyệt |
| 4 | Mẫu khác: nhập 1 kết quả, rồi `tiepnhan` hủy mẫu | Kết quả vẫn *Chờ duyệt*, có ghi chú R15, không còn ô nhập hay nút |
| 5 | Bật DevTools, chế độ thiết bị 360 px | Bảng kết quả cuộn ngang trong khung, trang không cuộn ngang |
| 6 | `psql` vào CSDL dev: `UPDATE result_audit SET actor = 'x';` · `DELETE FROM test_result;` · `UPDATE test_result SET value = 1 WHERE status = 'APPROVED';` | Lần lượt bị chặn bởi `tg_result_audit_append_only`, `tg_test_result_no_delete`, `tg_test_result_guard_update` |

## 6. Câu hỏi mở (cần nghiệp vụ trả lời)

| # | Câu hỏi | Vì sao quan trọng | Spec đang tạm làm | Ảnh hưởng nếu đổi |
|---|---|---|---|---|
| 6.1 | Mỗi loại mẫu có **bộ chỉ tiêu bắt buộc** không? | Không có bộ bắt buộc thì mẫu chỉ có pH Đạt vẫn kết luận Đạt (BR-11) | Không bắt buộc (D8) | Thêm bảng loại mẫu ↔ chỉ tiêu (V5), BR-11 kiểm tra đủ bộ |
| 6.2 | So với tiêu chuẩn có **làm tròn theo dược điển** không? Mỗi chỉ tiêu báo cáo mấy chữ số? | 105.04 với 95–105: so nguyên văn thì Không đạt, làm tròn thì Đạt | So nguyên văn (D6), giữ số chữ số người nhập | Thêm cột số chữ số làm tròn vào `test_parameter`; sửa `ck_test_result_verdict_spec`, `Spec.evaluate`, TC-BR07-01 |
| 6.3 | Kết quả **Không đạt (OOS)** có cần xác nhận, ghi chú hay mở điều tra bắt buộc không? | GMP thường yêu cầu điều tra OOS | Không có xử lý riêng | Thêm bước xác nhận khi nhập hoặc trường ghi chú bắt buộc |
| 6.4 | Kết quả **đã duyệt** mà phát hiện sai thì xử lý thế nào? | Hiện chỉ còn cách hủy mẫu và đăng ký lại | Không có đường sửa (D4) | Thêm thao tác thu hồi duyệt (trạng thái và audit mới; nới trigger) |
| 6.5 | Kết quả *Chờ duyệt* của mẫu đã hủy có cần trạng thái riêng không (ví dụ "Hủy theo mẫu")? | Báo cáo, CSV (BR-16) và tiến độ (BR-13) sẽ thấy kết quả "Chờ duyệt" mãi mãi | Giữ `PENDING` (D10) | Thêm trạng thái, sửa `ck_test_result_status`, trigger, `cancel()` |
| 6.6 | Tiếp nhận có được hủy mẫu *Đang kiểm nghiệm* **đã có kết quả** không? (phần còn lại của Q5) | Mục 4 LT-BRD nói Tiếp nhận hủy "mẫu gửi nhầm" | Theo ma trận mục 9: được | Thêm điều kiện ở `cancel()` và luật bảo mật |
| 6.7 | Người duyệt vừa **từ chối** có được tự nhập lại giá trị cho chỉ tiêu đó không? | Người đó vừa đánh giá vừa đo lại | Được (D3); bản mới phải do người duyệt khác duyệt | Thêm kiểm tra `actor` ≠ `reviewed_by` của bản bị thay |
| 6.8 | Xác nhận câu chữ các thông báo "đề xuất" ở 2.2 | Test so khớp nguyên văn | Dùng câu đề xuất | Sửa thông báo và test |
| 6.9 | Ai cập nhật LT-BRD mục 4 (6 tài khoản demo, thêm `duyet2`), mục 9 (URL duyệt/từ chối) và đánh dấu Q4, Q5, Q7, Q8, Q9 trong `cau-hoi-lam-ro.md` là đã trả lời? | Tài liệu nguồn đang lệch với spec | Spec ghi các điểm lệch này | Chỉ sửa tài liệu |
| 6.10 | Môi trường thật có quy định **tối thiểu 2 người duyệt đang hoạt động** không? | Chỉ có 1 người duyệt thì kết quả người đó tự nhập sẽ kẹt (L8) | Chỉ lo cho dữ liệu demo (R16) | Quy trình vận hành, hoặc cấm REVIEWER nhập kết quả |
| 6.11 | Chỉ tiêu bị **ngừng dùng** khi mẫu còn kết quả *Chờ duyệt* hoặc vừa bị từ chối thì sao? | BR-06 chỉ nói về lúc nhập | Duyệt/từ chối vẫn được; nhập lại thì bị chặn (R2) | Cho phép nhập lại với chỉ tiêu đã có kết quả trên mẫu |
| 6.12 | Audit có cần ghi cả **lần thử bị chặn** không (ví dụ cố duyệt kết quả của chính mình)? | Có đoàn thanh tra muốn thấy cả hành vi bị từ chối | Không ghi (R12: thất bại thì rollback) | Ghi trong transaction riêng (`REQUIRES_NEW`), thêm giá trị cho `action` |

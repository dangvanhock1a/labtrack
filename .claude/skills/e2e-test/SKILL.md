---
name: e2e-test
description: >-
  Viết, sửa hoặc chữa đỏ test E2E Playwright (lớp *IT kế thừa E2ETestBase) theo kinh nghiệm Lab 08: viết kịch bản
  dạng bảng trong docs/kich-ban-e2e.md trước và chờ người dùng đồng ý, rồi mới viết lớp *IT; chạy riêng từng lớp bằng
  ./mvnw verify -Dit.test=<Lop>; khi đỏ đọc ảnh target/e2e-screenshots trước khi sửa và phân loại lỗi test hay lỗi
  ứng dụng. Dùng khi người dùng nói "test E2E", "kịch bản E2E", "TC-E2E", "Playwright", "lớp IT", "test đầu-cuối",
  "E2E đỏ", "E2E fail", hoặc đồng ý viết kịch bản E2E mà skill new-screen đề xuất. Không dùng cho unit test,
  @WebMvcTest hay test tích hợp *Tests.
argument-hint: "<chức năng hoặc mã TC-E2E cần viết/sửa, hoặc tên lớp *IT đang đỏ>"
allowed-tools: Bash(./mvnw *) Bash(git grep *) Bash(git diff *) Bash(git status *)
---
# Test E2E Playwright: kịch bản trước, code sau

Việc cần làm: $ARGUMENTS

## File mẫu phải đọc trước
| File | Lấy gì |
|---|---|
| `e2e/E2ETestBase.java` | Helper có sẵn (danh sách ở dưới). Không viết lại phần đăng nhập, mở mẫu, tìm dòng kết quả |
| `e2e/SampleLifecycleIT.java` | Luồng nhiều vai trò; hộp thoại `AriaRole.DIALOG` theo tiêu đề; ô `required` kiểm bằng `evaluate("e => e.validity.valueMissing")`; tiền điều kiện `seedResult` qua service |
| `e2e/AuthorizationIT.java` | Nút vô hiệu: `isDisabled` + `hasAccessibleDescription` + `hover(new Locator.HoverOptions().setForce(true))`. Trang 403: `Response response = page.navigate(url(…))` |
| `docs/kich-ban-e2e.md` | Cột của bảng, cách ghi tiền điều kiện, mục "Lựa chọn trong kịch bản", bảng "Test hiện thực" |
| Template + controller của màn hình cần test | Chữ nguyên văn, `<label for>`, `data-testid`, `sec:authorize` |

Đọc file trong `e2e/` hay `docs/kich-ban-e2e.md` sẽ tự nạp `.claude/rules/e2e.md`. Các quy tắc ở đó áp dụng cho mọi bước bên dưới.

## Hiện trạng
Kịch bản đã có:
!`git grep -h -o -E "\*\*TC-E2E-[0-9]+\*\*<br>[^|]*" -- docs/kich-ban-e2e.md`

Đã có test (mã TC → lớp.method):
!`git grep -h -E "^\| TC-E2E-[0-9]+ \|" -- docs/kich-ban-e2e.md`

Helper của `E2ETestBase`:
!`git grep -n -E "^[[:space:]]+protected " -- "*/e2e/E2ETestBase.java"`

`data-testid` đang có trong template:
!`git grep -n -o -E "data-testid=.[A-Za-z-]+." -- src/main/resources/templates`

## Quy trình
1. **Viết kịch bản dạng bảng, rồi DỪNG.** Chưa viết Java ở bước này.
   - Thêm hàng vào bảng của `docs/kich-ban-e2e.md`, lấy mã `TC-E2E-xx` kế tiếp danh sách ở trên. Ô Mã TC ghi
     `**TC-E2E-xx**<br>Tên ngắn`. Ô Use case ghi `UC-0x · BR-yy`. Cột Vai trò ghi kèm tài khoản ở lần đầu, ví dụ
     `Người duyệt (`duyet`)`. Mỗi bước một hàng; hàng tiếp nối để trống hai cột đầu.
   - Chữ trong ngoặc kép mở template, controller, service, `messages.properties` ra chép, đừng gõ theo trí nhớ.
     Nút và link viết đậm, ví dụ **Lưu**.
   - Kết quả mong đợi phải là thứ assert được: chữ hiện ra, URL, trạng thái, số dòng, nút có / không có / bị vô hiệu.
     Mỗi kịch bản có ít nhất một bước phủ định: vai trò không có quyền thì không thấy nút, dữ liệu bị chặn thì không đổi.
   - Tiền điều kiện ghi đầu ô Bước (`Tiền điều kiện: …<br>1. …`) và dựng qua service. Chỉ TC-E2E-03 đi hết luồng
     bằng giao diện.
   - Giá trị chọn vì một lý do không hiển nhiên (pH `7.5` để có Không đạt, 5 dấu cách để lọt `minlength`) thì ghi lý do
     vào mục "Lựa chọn trong kịch bản".
   - Gặp đúng câu hỏi ở mục 2 `docs/cau-hoi-lam-ro.md` thì hỏi người dùng, không tự quyết.
   - Trình bày các hàng vừa thêm cho người dùng. Chỉ sang bước 2 khi có câu đồng ý rõ ràng. Người dùng sửa kịch
     bản thì sửa bảng rồi trình bày lại.
2. **Viết lớp `*IT` kế thừa `E2ETestBase`.**
   - Cùng chủ đề với lớp có sẵn (đăng nhập, vòng đời mẫu, phân quyền) thì thêm method vào lớp đó. Chủ đề mới thì tạo
     `<ChucNang>IT` package-private trong gói `e2e`. Mỗi TC một method `@Test`.
   - Mỗi bước của bảng là một đoạn code: thao tác → assertion → `screenshot("0n-<mo-ta-khong-dau>")`. Nhờ vậy ảnh
     cuối cùng còn lưu cho biết test đã đi tới bước nào.
   - Locator theo thứ tự `getByRole` → `getByLabel` → `getByTestId`. Cả ba không bắt được phần tử thì thêm
     `data-testid` vào template. Đây là sửa code ứng dụng, phải ghi vào báo cáo. Không dùng CSS hay XPath. Không lấy
     phần tử theo vị trí (`nth`, `first`), trừ khi thứ tự chính là điều cần kiểm, như thứ tự sắp xếp.
   - Mọi thứ trên trang chỉ kiểm bằng `PlaywrightAssertions.assertThat(locator|page)`: các assertion này tự chờ và
     tự thử lại. Dùng `hasText` khi cả phần tử phải khớp nguyên văn; `containsText` khi phần tử còn chữ khác, như giờ
     nhập. Người + giờ thì so bằng `actorAt(username)`, không so giờ cụ thể. JUnit `assertEquals`/`assertTrue` chỉ
     dùng cho giá trị đã lấy về xong: `response.status()`, kết quả `evaluate`.
     Sai: `assertTrue(locator.isVisible())`, `assertEquals("…", locator.textContent())`. Hai cách này đọc trang
     đúng một lần, không chờ, nên test lúc xanh lúc đỏ.
   - Mã mẫu lấy từ object service trả về (`sample.getCode()`). Đi qua giao diện thì mẫu đầu tiên là
     `LS-<năm>-00001`, vì `DbCleaner` xóa cả bộ đếm.
3. **Cập nhật bảng "Test hiện thực"**: `| TC-E2E-xx | `Lop.method` |`. `@DisplayName` mở đầu bằng đúng mã đó.
4. **Chạy riêng lớp đó:** `./mvnw verify -Dit.test=<Lop>`. Chạy một method: `"-Dit.test=<Lop>#<method>"`.
   - `verify` chạy hết unit test (surefire) trước. Một unit test đỏ là dừng, E2E không chạy.
   - Không có Docker thì dùng profile `localdb` trên `labtrack_test` như `CLAUDE.md`, không chạy lên CSDL dev. Trên
     PowerShell, bọc nháy mọi `-D` có dấu chấm:
     `$env:DB_URL='jdbc:postgresql://localhost:5433/labtrack_test'; ./mvnw verify "-Dspring.profiles.active=localdb" "-Dit.test=<Lop>"`.
   - Máy không tải được trình duyệt của Playwright thì mỗi lớp treo khoảng 10 phút rồi báo `Timed out waiting for
     browsers to install`. Gặp vậy thì dừng lệnh và chạy lại kèm `"-De2e.channel=msedge"` (Edge hoặc Chrome đã cài),
     đừng chờ hết.
5. **Test đỏ: đọc ảnh trước khi sửa.** Chưa sửa dòng code nào cho tới khi xong bước c.
   a. Đọc thông báo lỗi trong `target/failsafe-reports/*.txt`: locator nào, mong đợi gì, nhận được gì.
   b. Mở bằng Read ảnh `target/e2e-screenshots/<Lop>-<method>-end.png` và ảnh đánh số cuối cùng đã chụp được (bước
      cuối đã qua). Ghi một câu: ảnh đang ở trang nào, hiện chữ gì, trạng thái gì. Ảnh chưa đủ để kết luận thì gửi
      người dùng trace `target/playwright-traces/<Lop>-<method>.zip`, mở bằng trace.playwright.dev.
   c. So ảnh với hàng tương ứng trong `docs/kich-ban-e2e.md` và BR liên quan, rồi phân loại theo bảng bên dưới.
   d. Lỗi test thì sửa test. Lỗi ứng dụng thì DỪNG và báo người dùng: ảnh, bước, BR bị vi phạm, đề xuất cách sửa.
      Chỉ sửa ứng dụng khi người dùng đồng ý. Khi sửa, trước hết viết test ở tầng thấp hơn tái hiện lỗi
      (`@WebMvcTest`, service hoặc mapper), giữ nguyên E2E làm bằng chứng.
6. **Báo cáo:** các hàng kịch bản đã thêm, lớp/method đã viết, lệnh đã chạy, số test chạy/đỏ kèm output lỗi thật. Với
   từng lần đỏ: ảnh cho thấy gì, đã phân loại là gì, đã sửa gì.

## Lỗi test hay lỗi ứng dụng
| Ảnh và thông báo lỗi cho thấy | Thường là | Làm gì |
|---|---|---|
| Trang đúng như kịch bản nhưng locator không tìm thấy phần tử, hoặc `strict mode violation` (khớp 2 phần tử) | Lỗi test | Sửa locator: đúng role/tên, thêm `setExact(true)`, thu phạm vi về dòng hay hộp thoại |
| Chữ trên trang khác chữ trong test, còn kịch bản khớp với template | Lỗi test (chép sai chữ) | Chép lại từ template |
| Chữ trên trang khác kịch bản vì code vừa đổi câu chữ hay `data-testid` | Đổi một nơi mà không đổi nơi kia | Hỏi người dùng chữ mới có chủ ý không. Có thì sửa cả kịch bản lẫn test |
| Kịch bản ghi "chưa có chức năng X", và increment này vừa làm X | Kịch bản cũ | Cập nhật kịch bản và test, như BR-11 đã làm với TC-E2E-03 bước 6 |
| Trang 500 hay trang lỗi trắng, sai trạng thái, thiếu dữ liệu, vai trò không có quyền vẫn thấy hoặc bấm được nút | Lỗi ứng dụng | Bước 5d. Không sửa test cho xanh |
| Tìm có dấu không ra kết quả trên `localdb` dù qua trên Docker | Lỗi ứng dụng (collation, xem `.claude/rules/mybatis.md`) | Bước 5d |
| Đang ở trang đăng nhập khi lẽ ra đã vào trong | Thường do test: sai tài khoản, quên `login` sau `logout` | Đối chiếu `db/devdata/R__dev_seed.sql` |
| Đỏ trước khi mở được trang: `Failed to create driver`, `Could not find a valid Docker environment`, Flyway lỗi | Môi trường | Sửa lệnh chạy theo bước 4, không sửa code |
| Cùng code mà lúc xanh lúc đỏ | Test chờ sai cách | Tìm `isVisible()` dùng như boolean, `textContent()`, sleep; thay bằng `assertThat` |

Không bao giờ được làm cho test xanh bằng cách:
- nới assertion (`hasText` thành `containsText`, bỏ `setExact`, xóa bước, tăng timeout);
- thêm sleep;
- sửa kịch bản cho khớp với hành vi sai của ứng dụng.

## Bẫy hay gặp (Lab 08)
- `setName("Duyệt")` không kèm `setExact(true)` khớp cả nút "Phê duyệt". Nút bị đổi chữ mà test vẫn xanh (commit
  `3e22317`).
- Kiểm "không có nút" thì đếm mọi nút trong phạm vi: `assertThat(row.getByRole(AriaRole.BUTTON)).hasCount(0)`. Đếm
  theo tên thì nút bị đổi chữ vẫn ra 0, assertion luôn đúng.
- Assertion phủ định (`hasCount(0)`, `not().isVisible()`) vẫn xanh khi locator viết sai. Dùng cùng một biến locator,
  hoặc cùng tên chính xác, cho cả trạng thái phải có. Ví dụ `approve` trong `AuthorizationIT`: `isDisabled()` với
  người tự nhập, rồi `click()` với người duyệt khác.
- Rê chuột lên nút bị vô hiệu để xem tooltip thì dùng `hover(new Locator.HoverOptions().setForce(true))` như
  `AuthorizationIT` và `SampleLifecycleIT`.
- Hộp thoại Bootstrap: lấy bằng `page.getByRole(AriaRole.DIALOG, …setName("<tiêu đề>"))`, rồi tìm phần tử bên
  trong hộp thoại đó. Tìm trên cả `page` thì nút **Từ chối** của hộp thoại trùng với nút cùng tên ngoài bảng.
- Ô `required` để trống thì trình duyệt chặn, không có request nào gửi đi. Kiểm `validity.valueMissing`, cộng với
  việc dữ liệu không đổi. Muốn kiểm phần chặn ở server thì gửi giá trị lọt được kiểm tra của trình duyệt.
- Sau khi bấm nút gửi form, assert trên trang mới (flash, URL) trước rồi mới đọc phần khác. Đọc ngay sau `click()`
  có thể trúng trang cũ.
- Thêm bảng tham chiếu có dữ liệu nạp sẵn thì phải thêm tên bảng vào `NOT IN (…)` của `DbCleaner`, nếu không E2E
  xóa mất danh mục.

## Tự kiểm tra trước khi báo xong
- `git grep -n -E "Thread\.sleep|waitForTimeout|page\.locator\(|Pattern\.quote\(" -- "src/test/java/*/e2e/*"` ra 0 dòng.
- `git grep -n -E "assert(True|False)\(.*\.(isVisible|isEnabled|isDisabled|isChecked)\(\)" -- "src/test/java/*/e2e/*"` ra 0 dòng.
- Các dòng `setName(` mới trong `git diff -U0 -- src/test/java`: tên nào là chuỗi con của tên nút hay link khác trên
  cùng trang thì phải có `setExact(true)`. Ví dụ "Đăng ký" / "Đăng ký mẫu", "Kết luận" / "Kết luận mẫu", "Duyệt" /
  "Phê duyệt".
- Mỗi assertion phủ định mới có một assertion khẳng định dùng cùng locator hoặc cùng tên chính xác.
- Mỗi mã TC trong bảng kịch bản có dòng ở "Test hiện thực" và một `@DisplayName("TC-E2E-xx … (BR-yy)")` khớp.
- Đã chạy lớp vừa viết hoặc vừa sửa bằng `-Dit.test` và lớp đó xanh. Nếu chưa chạy được thì ghi rõ lý do trong báo
  cáo.

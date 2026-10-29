---
name: flyway-migration
description: >-
  Tạo migration Flyway mới cho PostgreSQL (V<n+1>__*.sql) rồi cập nhật cùng lúc domain, mapper XML MyBatis và
  test tích hợp trên PostgreSQL thật (ghi-đọc + ràng buộc). Dùng khi task đổi cấu trúc CSDL hoặc cần lưu dữ liệu mà bảng
  chưa có chỗ chứa: người dùng nói "đổi schema", "thêm cột", "thêm bảng", "thêm trường", "migration",
  "lưu thêm thông tin","cần lưu thêm", "thêm thuộc tính cho", hoặc BR trong kế hoạch increment có kèm số migration. Không dùng cho dữ liệu demo db/devdata.
argument-hint: "<mô tả thay đổi schema>"
allowed-tools: Bash(./mvnw *) Bash(git ls-files *) Bash(git status *)
---
# Migration Flyway + domain + mapper + test

Thay đổi cần làm: $ARGUMENTS

Đọc trước `.claude/rules/database.md`, `mybatis.md`, `testing.md`. Task nhắc BR-xx thì đọc thêm mục 8
`docs/yeu-cau-nghiep-vu.md` và mục 1 `docs/cau-hoi-lam-ro.md` (tầng phải chặn, loại test cần có).

## Migration hiện có
!`git ls-files src/main/resources/db/migration`

## Quy trình
1. **Version tiếp theo.** n = số V lớn nhất ở danh sách trên + 1, và phải trùng số migration của BR ở mục 13
   `docs/yeu-cau-nghiep-vu.md` (V6 = BR-13, V7 = BR-18…). Hai số lệch nhau → dừng, hỏi người dùng.
   Tên theo mẫu sẵn có: động từ tiếng Anh, snake_case, ví dụ `V6__add_priority_to_lab_sample.sql`.
2. **SQL** trong `src/main/resources/db/migration/`:
   - Dòng đầu `-- BR-xx: <lý do>` như V5; chú thích nói vì sao, không tả lại câu lệnh.
   - Cột `NOT NULL` thêm vào bảng đã có phải kèm `DEFAULT` hằng số. Thiếu thì lỗi trên CSDL đã có dòng, dù test
     chạy lúc bảng rỗng vẫn qua.
   - Enum: `VARCHAR(n)` + `CONSTRAINT ck_<bảng>_<ý nghĩa> CHECK (cot IN ('HANG_A', 'HANG_B'))`, giá trị là tên hằng
     Java nguyên văn. Không dùng `CREATE TYPE … AS ENUM`.
   - Mọi ràng buộc tự đặt tên `ck_`/`uq_`/`fk_<bảng>_<ý nghĩa>`. Đổi ràng buộc = `DROP CONSTRAINT` + `ADD` đúng tên cũ.
   - Không đụng trigger `tg_*` (V4). Bảng tham chiếu có dữ liệu nạp sẵn → thêm tên vào `NOT IN (…)` của `e2e/DbCleaner`.
3. **Domain** `domain/<Entity>.java`: thêm field + getter/setter theo kiểu của lớp đó; enum mới là lớp trong `domain`.
   Thêm/đổi hằng của enum đã có → làm đủ danh sách ở `database.md` (CHECK cũ, template, `hasRole`).
4. **Mapper XML** `mapper/<Entity>Mapper.xml`:
   - Thêm cột vào `<sql id="columns">` (thiếu thì SELECT không đọc lên, field luôn null) và vào `INSERT`/`UPDATE`
     liên quan. `map-underscore-to-camel-case` đã bật nên không cần `resultMap`.
   - Tham số có thể null ghi `#{x,jdbcType=VARCHAR}` (hoặc `NUMERIC`, `BIGINT`…) như `StatusHistoryMapper.xml`;
     thiếu thì driver PostgreSQL không đoán được kiểu khi giá trị là null.
   - Chỉ `#{}`; không `@Select`/`@Insert`… trên interface.
5. **Test tích hợp** ở `src/test/java/com/pharmalab/labtrack/persistence/`: `@SpringBootTest`
   + `@Transactional`, tên `*Tests` (không `*IT`),
   `@DisplayName("TC-BRxx-yy …")`. Bảng đã có lớp `*MapperTests`/`*ConstraintTests` thì thêm vào đó.
   - Ghi-đọc: ghi qua mapper hoặc service, đọc lại qua mapper, kiểm cột mới với giá trị thật và với null.
   - Ràng buộc: SQL ghi thẳng qua `JdbcTemplate`, mỗi câu vi phạm chạy trong savepoint, kiểm đúng tên ràng buộc
     trong lỗi; dùng lại cách `assertViolates` của `LabSampleConstraintTests`. Có cả một câu hợp lệ ghi được.
6. **Chạy** `./mvnw -q test` (CSDL `labtrack_test`, không chạy lên CSDL dev). Báo cáo: file đã tạo/sửa, số test chạy/đỏ kèm output lỗi thật, các cảnh báo ở mục dưới.

## Tự kiểm tra trước khi báo xong
- Không sửa V cũ: `git status --short src/main/resources/db/migration` chỉ có file mới, và
  `git diff --name-status main -- src/main/resources/db/migration` chỉ có dòng `A`. Sửa V đã chạy làm Flyway
  `validate` lỗi checksum trên mọi CSDL đã migrate → hoàn lại, đưa thay đổi vào V mới.
- Không `${` trong `src/main/resources/mapper/*.xml` (Grep `\$\{` ra 0 dòng).
- Ràng buộc mới đều có tên `ck_`/`uq_`/`fk_`; không còn tên PostgreSQL tự sinh (`*_check`, `*_key`, `*_fkey`).
- Cảnh báo khóa bảng / mất dữ liệu: có câu nào dưới đây thì ghi rõ trong báo cáo, đừng im lặng cho qua.
  - Mất dữ liệu, không lùi được: `DROP COLUMN`, `DROP TABLE`, thu hẹp kiểu (`VARCHAR(100)` → `(50)`), ép kiểu `USING`.
  - Viết lại cả bảng dưới khóa `ACCESS EXCLUSIVE`: `ALTER COLUMN … TYPE`, `ADD COLUMN … DEFAULT` hàm volatile
    (`clock_timestamp()`, `gen_random_uuid()`).
  - Quét cả bảng khi giữ khóa: `SET NOT NULL`, `ADD CONSTRAINT` CHECK/FOREIGN KEY/UNIQUE trên bảng có dữ liệu.
  - `CREATE INDEX` chặn ghi tới khi xong; Flyway chạy migration trong transaction nên không dùng được `CONCURRENTLY`.
  - `UPDATE`/`DELETE` dữ liệu cũ của `test_result`, `result_audit` bị trigger V4 chặn: không backfill được ở đó.

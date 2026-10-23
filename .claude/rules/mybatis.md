---
paths:
  - "src/main/resources/mapper/**/*.xml"
  - "src/main/java/**/persistence/**/*.java"
---
# MyBatis mapper
- SQL nằm ở `mapper/<TênMapper>.xml`, `namespace` = tên đầy đủ của interface. Không dùng `@Select`/`@Insert`/`@Update`/`@Delete` trên interface (repo hiện có 0 chỗ).
- Tham số luôn viết `#{}`. Đúng: `WHERE code = #{code}`. Sai: `WHERE code = '${code}'` (SQL injection; repo hiện có 0 chỗ `${`).
- `<select>` bọc `INSERT/UPDATE … RETURNING` phải có `flushCache="true" useCache="false"`; thiếu thì lần gọi thứ hai trong cùng transaction nhận lại giá trị cũ từ cache.
- `SELECT … FOR UPDATE` cũng vậy (`LabSampleMapper.lockById`): lấy từ cache thì câu khóa không chạy và dòng không bị khóa.
- Điều kiện ghi nằm trong chính câu ghi (`ON CONFLICT … RETURNING`, `UPDATE … WHERE status = #{from}`), trả `int` số dòng để service kiểm; không chỉ dựa vào một câu SELECT trước đó.
- `TIMESTAMPTZ` đọc lên theo UTC; đổi về múi giờ ứng dụng ở service (`toClockZone`), không đổi trong SQL.

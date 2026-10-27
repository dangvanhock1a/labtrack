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
- So không phân biệt hoa thường (`ILIKE`, `lower()`) ghi `COLLATE pg_c_utf8` trên cột, như `searchWhere` của `LabSampleMapper.xml`. Collation mặc định theo locale máy chủ: PostgreSQL trên Windows locale `Japanese_Japan.932` không gập Í, Á, É, Ô…, trong khi Testcontainers (en_US.utf8) gập được, nên thiếu `COLLATE` thì test qua trên Docker mà lỗi trên máy thật.
- Số liệu đếm cho từng dòng của trang (tiến độ x/y) đặt trong `LATERAL` sau khi cắt trang bằng subquery có `LIMIT/OFFSET`; subquery đếm đặt ở danh sách cột bị tính cho mọi dòng tới OFFSET + LIMIT (`LabSampleMapper.search`).

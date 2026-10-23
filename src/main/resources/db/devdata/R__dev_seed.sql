-- Dữ liệu giả cho dev/test (NFR-07): 5 tài khoản demo của LT-BRD mục 4. Họ tên là hư cấu.
-- Mật khẩu mọi tài khoản: Passw0rd! (đã công khai trong LT-BRD, chỉ dùng cho dev/test).
-- Đây là migration repeatable: Flyway chạy lại mỗi khi checksum của file đổi, nên câu lệnh phải
-- chạy lại nhiều lần vẫn ra cùng kết quả. Vì thế dùng ON CONFLICT … DO UPDATE thay cho INSERT thuần.
INSERT INTO app_user (username, full_name, password_hash, role, enabled)
VALUES ('tiepnhan',    'Trần Thị Thu',  '{bcrypt}$2a$10$hbR7RS/2BOVupE7seDYmruEfQhomYdrcagZP0uSyViN4vNz83ainK', 'RECEPTIONIST', TRUE),
       ('kiemnghiem1', 'Lê Văn Minh',   '{bcrypt}$2a$10$hbR7RS/2BOVupE7seDYmruEfQhomYdrcagZP0uSyViN4vNz83ainK', 'ANALYST',      TRUE),
       ('kiemnghiem2', 'Phạm Thị Hà',   '{bcrypt}$2a$10$hbR7RS/2BOVupE7seDYmruEfQhomYdrcagZP0uSyViN4vNz83ainK', 'ANALYST',      TRUE),
       ('duyet',       'Hoàng Văn Duy', '{bcrypt}$2a$10$hbR7RS/2BOVupE7seDYmruEfQhomYdrcagZP0uSyViN4vNz83ainK', 'REVIEWER',     TRUE),
       ('quanly',      'Vũ Thị Quỳnh',  '{bcrypt}$2a$10$hbR7RS/2BOVupE7seDYmruEfQhomYdrcagZP0uSyViN4vNz83ainK', 'MANAGER',      TRUE)
ON CONFLICT (username) DO UPDATE
SET full_name     = EXCLUDED.full_name,
    password_hash = EXCLUDED.password_hash,
    role          = EXCLUDED.role,
    enabled       = EXCLUDED.enabled;

-- BR-12, UC-01: tài khoản đăng nhập. Phiên bản 1.0 không có màn hình quản lý người dùng (LT-BRD mục 3);
-- tài khoản demo nạp bằng db/devdata/R__dev_seed.sql, không đặt trong migration.
CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Dài 50 cho khớp lab_sample.created_by và sample_status_history.changed_by, nơi lưu username.
    username      VARCHAR(50)  NOT NULL
                  CONSTRAINT uq_app_user_username UNIQUE
                  CONSTRAINT ck_app_user_username CHECK (btrim(username) <> ''),
    full_name     VARCHAR(100) NOT NULL
                  CONSTRAINT ck_app_user_full_name CHECK (btrim(full_name) <> ''),
    -- NFR-02: chỉ lưu hash. Tiền tố {id} (ví dụ {bcrypt}) là thứ DelegatingPasswordEncoder dùng để chọn
    -- thuật toán; thiếu nó thì đăng nhập lỗi mà không báo gì, nên chặn ngay ở CSDL.
    password_hash VARCHAR(100) NOT NULL
                  CONSTRAINT ck_app_user_password_hash CHECK (password_hash ~ '^\{[a-z0-9]+\}'),
    role          VARCHAR(20)  NOT NULL
                  CONSTRAINT ck_app_user_role
                  CHECK (role IN ('RECEPTIONIST', 'ANALYST', 'REVIEWER', 'MANAGER')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE
);

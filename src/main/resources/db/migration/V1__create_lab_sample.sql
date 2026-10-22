-- I-1 (Lab 03): bộ đếm mã mẫu theo năm (BR-01) và bảng mẫu kiểm nghiệm (BR-02).

CREATE TABLE sample_code_counter (
    year    INTEGER NOT NULL,
    last_no INTEGER NOT NULL,
    CONSTRAINT pk_sample_code_counter PRIMARY KEY (year),
    CONSTRAINT ck_sample_code_counter_year CHECK (year BETWEEN 2000 AND 9999),
    CONSTRAINT ck_sample_code_counter_last_no CHECK (last_no >= 0)
);

CREATE TABLE lab_sample (
    id           BIGINT GENERATED ALWAYS AS IDENTITY,
    code         VARCHAR(13)  NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    batch_no     VARCHAR(50)  NOT NULL,
    sample_type  VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',
    received_at  TIMESTAMPTZ  NOT NULL,
    due_date     DATE         NOT NULL,
    created_by   VARCHAR(50)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_lab_sample PRIMARY KEY (id),
    CONSTRAINT uq_lab_sample_code UNIQUE (code),
    CONSTRAINT ck_lab_sample_code_format CHECK (code ~ '^LS-[0-9]{4}-[0-9]{5}$'),
    CONSTRAINT ck_lab_sample_product_name CHECK (btrim(product_name) <> ''),
    CONSTRAINT ck_lab_sample_batch_no CHECK (batch_no ~ '^[A-Za-z0-9/-]+$'),
    CONSTRAINT ck_lab_sample_type CHECK (sample_type IN ('RAW_MATERIAL', 'INTERMEDIATE', 'FINISHED_PRODUCT', 'STABILITY')),
    CONSTRAINT ck_lab_sample_status CHECK (status IN ('RECEIVED', 'IN_TESTING', 'COMPLETED', 'CANCELLED')),
    -- Ngày nhận tính theo giờ VN. Không dùng received_at::date vì kết quả phụ thuộc TimeZone của phiên.
    -- Đổi labtrack.time-zone thì phải viết migration mới cho ràng buộc này.
    CONSTRAINT ck_lab_sample_due_date CHECK (due_date >= (received_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date)
);

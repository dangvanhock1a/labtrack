-- BR-01: bộ đếm số thứ tự mã mẫu theo năm (năm nhận theo giờ Việt Nam)
CREATE TABLE sample_code_counter (
    year    INTEGER PRIMARY KEY,
    last_no INTEGER NOT NULL CONSTRAINT ck_counter_last_no CHECK (last_no >= 1)
);

-- BR-01, BR-02: mẫu kiểm nghiệm
CREATE TABLE lab_sample (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         VARCHAR(13)  NOT NULL
                 CONSTRAINT uq_lab_sample_code UNIQUE
                 CONSTRAINT ck_lab_sample_code CHECK (code ~ '^LS-[0-9]{4}-[0-9]{5}$'),
    product_name VARCHAR(200) NOT NULL
                 CONSTRAINT ck_lab_sample_product CHECK (btrim(product_name) <> ''),
    batch_no     VARCHAR(50)  NOT NULL
                 CONSTRAINT ck_lab_sample_batch CHECK (batch_no ~ '^[A-Za-z0-9/-]+$'),
    sample_type  VARCHAR(20)  NOT NULL
                 CONSTRAINT ck_lab_sample_type
                 CHECK (sample_type IN ('RAW_MATERIAL', 'INTERMEDIATE', 'FINISHED_PRODUCT', 'STABILITY')),
    status       VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED'
                 CONSTRAINT ck_lab_sample_status
                 CHECK (status IN ('RECEIVED', 'IN_TESTING', 'COMPLETED', 'CANCELLED')),
    received_at  TIMESTAMPTZ  NOT NULL,
    due_date     DATE         NOT NULL,
    created_by   VARCHAR(50)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_lab_sample_due_date
        CHECK (due_date >= (received_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date)
);

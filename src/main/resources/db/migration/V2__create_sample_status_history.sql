-- BR-04: lịch sử trạng thái mẫu. Bảng chỉ-thêm (NFR-03): không UPDATE, không DELETE.
CREATE TABLE sample_status_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sample_id   BIGINT       NOT NULL
                -- Không ON DELETE CASCADE: NFR-03 cấm xóa cứng mẫu, nên để khóa ngoại
                -- chặn thẳng mọi lệnh DELETE lab_sample còn lịch sử.
                CONSTRAINT fk_status_history_sample
                REFERENCES lab_sample (id) ON DELETE RESTRICT,
    -- NULL = dòng lúc đăng ký (Tạo mới → Đã tiếp nhận), mẫu chưa có trạng thái cũ.
    from_status VARCHAR(20)
                CONSTRAINT ck_status_history_from
                CHECK (from_status IN ('RECEIVED', 'IN_TESTING', 'COMPLETED', 'CANCELLED')),
    to_status   VARCHAR(20)  NOT NULL
                CONSTRAINT ck_status_history_to
                CHECK (to_status IN ('RECEIVED', 'IN_TESTING', 'COMPLETED', 'CANCELLED')),
    -- NFR-03 (ALCOA+, chữ A là Attributable): dòng lịch sử không có người thực hiện thì vô giá trị.
    changed_by  VARCHAR(50)  NOT NULL
                CONSTRAINT ck_status_history_changed_by
                CHECK (btrim(changed_by) <> ''),
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reason      VARCHAR(500)
                CONSTRAINT ck_status_history_reason
                CHECK (reason IS NULL OR char_length(btrim(reason)) >= 5),
    -- Chỉ dòng đầu tiên của một mẫu được thiếu trạng thái cũ.
    CONSTRAINT ck_status_history_new
        CHECK (from_status IS NOT NULL OR to_status = 'RECEIVED'),
    CONSTRAINT ck_status_history_distinct
        CHECK (from_status IS NULL OR from_status <> to_status),
    -- BR-05: hủy mẫu bắt buộc có lý do. Chặn ở cả form, service và CSDL.
    CONSTRAINT ck_status_history_cancel_reason
        CHECK (to_status <> 'CANCELLED' OR reason IS NOT NULL)
);

-- Trang chi tiết đọc lịch sử của một mẫu theo thời gian (BR-04).
CREATE INDEX ix_status_history_sample ON sample_status_history (sample_id, changed_at);

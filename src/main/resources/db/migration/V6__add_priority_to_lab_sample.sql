-- BR-13: mức ưu tiên của mẫu; giá trị lấy theo tên hằng SamplePriority. DEFAULT hằng số để mẫu đã có nhận
-- Bình thường mà không phải viết lại bảng.
ALTER TABLE lab_sample
    ADD COLUMN priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL'
                        CONSTRAINT ck_lab_sample_priority CHECK (priority IN ('LOW', 'NORMAL', 'URGENT'));

-- BR-13: danh sách mẫu lọc theo trạng thái và xếp mẫu nhận mới nhất trước.
CREATE INDEX ix_lab_sample_status_received ON lab_sample (status, received_at DESC);

-- BR-18: điều kiện bảo quản của mẫu; giá trị lấy theo tên hằng StorageCondition. DEFAULT hằng số để mẫu đã có nhận
-- Nhiệt độ phòng (giá trị mặc định của BR-18) mà không phải viết lại bảng.
ALTER TABLE lab_sample
    ADD COLUMN storage_condition VARCHAR(20) NOT NULL DEFAULT 'ROOM_TEMPERATURE'
                                 CONSTRAINT ck_lab_sample_storage_condition
                                 CHECK (storage_condition IN ('ROOM_TEMPERATURE', 'COOL', 'FROZEN'));

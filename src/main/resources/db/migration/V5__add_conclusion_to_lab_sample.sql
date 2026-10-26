-- BR-11: kết luận mẫu. Hai cột để trống tới khi người duyệt kết luận; giá trị lấy theo tên hằng Verdict.
ALTER TABLE lab_sample
    ADD COLUMN conclusion   VARCHAR(4)
                            CONSTRAINT ck_lab_sample_conclusion CHECK (conclusion IN ('PASS', 'FAIL')),
    ADD COLUMN completed_at TIMESTAMPTZ,
    -- Hoàn thành ⇔ có kết luận; kết luận và thời điểm hoàn thành luôn đi cùng nhau. Chặn cả SQL ghi thẳng
    -- chuyển mẫu sang COMPLETED mà không kết luận, lẫn ghi kết luận cho mẫu chưa hoàn thành.
    ADD CONSTRAINT ck_lab_sample_completed
        CHECK ((status = 'COMPLETED') = (conclusion IS NOT NULL)
               AND (conclusion IS NULL) = (completed_at IS NULL));

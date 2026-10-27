\set ON_ERROR_STOP on
\encoding UTF8

-- Dữ liệu GIẢ cho CSDL dev (NFR-07): 25 mẫu đủ 4 trạng thái, 3 mức ưu tiên, 4 loại mẫu. Tên sản phẩm và số lô
-- đều bịa; mọi số lô bắt đầu bằng DEV- để nhận ra dữ liệu này và để script từ chối nạp lần hai.
--
-- Không phải migration: Flyway không đọc thư mục scripts/. Chạy tay bằng psql trên CSDL dev đã migrate tới V6:
--   psql -h localhost -p 5433 -U labtrack -d labtrack -f scripts/dev-data-25-mau.sql
-- Thêm -v dryrun=1 để chạy thử: mọi câu lệnh chạy và kiểm tra, cuối cùng ROLLBACK.
--
-- Mỗi mẫu đi kèm lịch sử trạng thái (BR-04); mẫu đang và đã kiểm nghiệm có kết quả cùng audit (BR-06..BR-10),
-- để trang chi tiết, cột "Đã duyệt" (BR-13) và nút Kết luận (BR-11) hiện đúng như dữ liệu do ứng dụng tạo ra.
-- Mẫu Hoàn thành có đủ 5 chỉ tiêu Đã duyệt và kết luận khớp kết quả; khối kiểm tra ở cuối chặn nếu lệch.
--
-- Mã mẫu: cả 25 mẫu nhận trong năm 2026 theo giờ VN, nên mã là LS-2026-NNNNN, đánh số theo thời điểm nhận. Dải số
-- lấy từ sample_code_counter bằng cùng kiểu câu INSERT … ON CONFLICT … RETURNING mà SampleCodeGenerator dùng, nên
-- số ứng dụng cấp tiếp theo nằm sau dải này, kể cả khi CSDL đã có mẫu năm 2026.
--
-- "Quá hạn" trong chú thích tính tại ngày 27/10/2026. Toàn bộ script chạy trong một transaction: lỗi ở bất kỳ câu
-- nào thì psql dừng (ON_ERROR_STOP) và không có gì được ghi.

BEGIN;

DO $$
BEGIN
    IF current_database() ~ 'test$' THEN
        RAISE EXCEPTION 'CSDL "%" là CSDL test (E2E xóa sạch trước mỗi test). Script này chỉ dành cho CSDL dev.',
            current_database();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = current_schema() AND table_name = 'lab_sample' AND column_name = 'priority') THEN
        RAISE EXCEPTION 'lab_sample chưa có cột priority (V6): chạy ./mvnw spring-boot:run một lần để Flyway migrate, rồi chạy lại script.';
    END IF;
    IF EXISTS (SELECT 1 FROM lab_sample WHERE batch_no LIKE 'DEV-%') THEN
        RAISE EXCEPTION 'CSDL đã có mẫu số lô DEV-…: script đã chạy rồi. Mẫu có lịch sử không xóa được (NFR-03), muốn nạp lại thì dựng lại CSDL dev.';
    END IF;
    IF (SELECT count(*) FROM test_parameter
        WHERE active AND code IN ('ASSAY', 'PH', 'LOD', 'DISSOLUTION', 'IMPURITY_TOTAL')) <> 5 THEN
        RAISE EXCEPTION 'Danh mục chỉ tiêu khác V4 (thiếu hoặc đã ngừng dùng một trong 5 chỉ tiêu): sửa phần kết quả của script trước khi chạy.';
    END IF;
END
$$;

-- Một dòng cho mỗi mẫu, giờ VN (+07). started_*: lúc Nhận kiểm nghiệm, trống nếu chưa nhận. ended_*: lúc kết thúc,
-- COMPLETED kèm kết luận, CANCELLED kèm lý do hủy (5–500 ký tự, BR-05).
CREATE TEMP TABLE dev_sample (
    batch_no      VARCHAR(50)  PRIMARY KEY,
    product_name  VARCHAR(200) NOT NULL,
    sample_type   VARCHAR(20)  NOT NULL,
    priority      VARCHAR(10)  NOT NULL,
    received_at   TIMESTAMPTZ  NOT NULL,
    due_date      DATE         NOT NULL,
    started_by    VARCHAR(50),
    started_at    TIMESTAMPTZ,
    ended_status  VARCHAR(20),
    ended_by      VARCHAR(50),
    ended_at      TIMESTAMPTZ,
    conclusion    VARCHAR(4),
    cancel_reason VARCHAR(500),
    sample_id     BIGINT
) ON COMMIT DROP;

INSERT INTO dev_sample (batch_no, product_name, sample_type, priority, received_at, due_date,
                        started_by, started_at, ended_status, ended_by, ended_at, conclusion, cancel_reason)
VALUES
-- Đã tiếp nhận (7)
('DEV-A12-2610',  'Nguyên liệu hoạt chất A-12',          'RAW_MATERIAL',     'NORMAL', '2026-10-26 09:10+07', '2026-11-02', NULL, NULL, NULL, NULL, NULL, NULL, NULL),
('DEV-C310-2610', 'Viên nén bao phim C-310 500 mg',      'FINISHED_PRODUCT', 'URGENT', '2026-10-26 14:20+07', '2026-10-28', NULL, NULL, NULL, NULL, NULL, NULL, NULL),
('DEV-D45-2610',  'Siro D-45 chai 60 ml',                'FINISHED_PRODUCT', 'LOW',    '2026-10-25 10:05+07', '2026-11-08', NULL, NULL, NULL, NULL, NULL, NULL, NULL),
('DEV-B07-2610',  'Cốm pha hỗn dịch B-07',               'INTERMEDIATE',     'NORMAL', '2026-10-24 08:40+07', '2026-10-31', NULL, NULL, NULL, NULL, NULL, NULL, NULL),
-- quá hạn; tên có % để thử tìm "70%" (BR-13)
('DEV-E70-2610',  'Dung dịch sát khuẩn E-70 70%',        'FINISHED_PRODUCT', 'NORMAL', '2026-10-17 15:30+07', '2026-10-24', NULL, NULL, NULL, NULL, NULL, NULL, NULL),
('DEV-H20-ST06',  'Viên nang H-20, độ ổn định mốc 6 tháng', 'STABILITY',     'LOW',    '2026-10-20 09:00+07', '2026-11-19', NULL, NULL, NULL, NULL, NULL, NULL, NULL),
-- Khẩn và quá hạn
('DEV-K3-2610',   'Tá dược độn K-3',                     'RAW_MATERIAL',     'URGENT', '2026-10-23 07:55+07', '2026-10-25', NULL, NULL, NULL, NULL, NULL, NULL, NULL),
-- Đang kiểm nghiệm (8). Nhận lúc 00:30 giờ VN, khi UTC vẫn là 21/10; tên có _ để thử tìm "F_250" (BR-13).
('DEV-F250-2610', 'Bột pha tiêm F_250 lọ 1 g',           'FINISHED_PRODUCT', 'URGENT', '2026-10-22 00:30+07', '2026-10-29', 'kiemnghiem1', '2026-10-22 08:15+07', NULL, NULL, NULL, NULL, NULL),
('DEV-L5-2610',   'Nguyên liệu hoạt chất L-5',           'RAW_MATERIAL',     'NORMAL', '2026-10-21 10:00+07', '2026-10-28', 'kiemnghiem2', '2026-10-21 13:00+07', NULL, NULL, NULL, NULL, NULL),
('DEV-M80-2610',  'Viên nén M-80 80 mg',                 'FINISHED_PRODUCT', 'NORMAL', '2026-10-19 09:30+07', '2026-10-26', 'kiemnghiem1', '2026-10-19 14:00+07', NULL, NULL, NULL, NULL, NULL),
('DEV-N14-2610',  'Cốm bán thành phẩm N-14',             'INTERMEDIATE',     'LOW',    '2026-10-16 08:00+07', '2026-10-30', 'kiemnghiem2', '2026-10-16 10:00+07', NULL, NULL, NULL, NULL, NULL),
('DEV-P60-2610',  'Hỗn dịch uống P-60 chai 100 ml',      'FINISHED_PRODUCT', 'URGENT', '2026-10-20 13:45+07', '2026-10-23', 'kiemnghiem1', '2026-10-20 15:00+07', NULL, NULL, NULL, NULL, NULL),
('DEV-Q9-2610',   'Viên sủi Q-9',                        'FINISHED_PRODUCT', 'NORMAL', '2026-10-15 09:20+07', '2026-10-22', 'kiemnghiem1', '2026-10-15 11:00+07', NULL, NULL, NULL, NULL, NULL),
('DEV-R2-ST03',   'Viên nén R-2, độ ổn định mốc 3 tháng', 'STABILITY',       'NORMAL', '2026-10-12 09:00+07', '2026-11-11', 'kiemnghiem2', '2026-10-13 08:30+07', NULL, NULL, NULL, NULL, NULL),
('DEV-S11-2610',  'Tá dược dính S-11',                   'RAW_MATERIAL',     'LOW',    '2026-10-14 10:10+07', '2026-10-21', 'kiemnghiem1', '2026-10-14 14:00+07', NULL, NULL, NULL, NULL, NULL),
-- Hoàn thành (6): 4 Đạt, 2 Không đạt. DEV-M80-2609 kết luận lúc 00:20 giờ VN, khi UTC vẫn là 06/10.
('DEV-C310-2609', 'Viên nén bao phim C-310 500 mg',      'FINISHED_PRODUCT', 'NORMAL', '2026-09-02 08:30+07', '2026-09-09', 'kiemnghiem1', '2026-09-02 10:00+07', 'COMPLETED', 'duyet',  '2026-09-04 16:00+07', 'PASS', NULL),
('DEV-A12-2609',  'Nguyên liệu hoạt chất A-12',          'RAW_MATERIAL',     'URGENT', '2026-09-10 14:00+07', '2026-09-12', 'kiemnghiem2', '2026-09-10 15:00+07', 'COMPLETED', 'duyet2', '2026-09-11 16:00+07', 'PASS', NULL),
('DEV-D45-2609',  'Siro D-45 chai 60 ml',                'FINISHED_PRODUCT', 'NORMAL', '2026-09-18 09:00+07', '2026-09-25', 'kiemnghiem1', '2026-09-18 13:00+07', 'COMPLETED', 'duyet',  '2026-09-22 10:00+07', 'FAIL', NULL),
('DEV-B07-2609',  'Cốm pha hỗn dịch B-07',               'INTERMEDIATE',     'LOW',    '2026-09-25 10:30+07', '2026-10-09', 'kiemnghiem2', '2026-09-26 08:00+07', 'COMPLETED', 'duyet',  '2026-10-01 15:00+07', 'PASS', NULL),
('DEV-H20-ST03',  'Viên nang H-20, độ ổn định mốc 3 tháng', 'STABILITY',     'NORMAL', '2026-10-01 08:00+07', '2026-10-31', 'kiemnghiem1', '2026-10-01 09:00+07', 'COMPLETED', 'duyet2', '2026-10-08 09:30+07', 'FAIL', NULL),
('DEV-M80-2609',  'Viên nén M-80 80 mg',                 'FINISHED_PRODUCT', 'URGENT', '2026-10-05 13:00+07', '2026-10-07', 'kiemnghiem2', '2026-10-05 14:00+07', 'COMPLETED', 'duyet',  '2026-10-07 00:20+07', 'PASS', NULL),
-- Đã hủy (4): 3 hủy khi mới tiếp nhận, 1 hủy khi đang kiểm nghiệm nhưng chưa có kết quả.
('DEV-T4-2610',   'Dung dịch tiêm T-4 ống 2 ml',         'FINISHED_PRODUCT', 'NORMAL', '2026-10-08 09:15+07', '2026-10-15', NULL, NULL, 'CANCELLED', 'tiepnhan', '2026-10-08 10:00+07', NULL, 'Gửi nhầm mẫu, sản xuất sẽ gửi lại lô khác'),
('DEV-L5-2609',   'Nguyên liệu hoạt chất L-5',           'RAW_MATERIAL',     'URGENT', '2026-09-28 11:00+07', '2026-09-30', NULL, NULL, 'CANCELLED', 'duyet',    '2026-09-28 14:30+07', NULL, 'Bao bì mẫu bị rách, không đủ điều kiện kiểm nghiệm'),
('DEV-N14-2609',  'Cốm bán thành phẩm N-14',             'INTERMEDIATE',     'NORMAL', '2026-09-15 08:20+07', '2026-09-22', 'kiemnghiem1', '2026-09-15 10:00+07', 'CANCELLED', 'duyet', '2026-09-15 11:00+07', NULL, 'Sản xuất hủy lô bán thành phẩm, không cần kiểm nghiệm tiếp'),
('DEV-R2-ST00',   'Viên nén R-2, độ ổn định mốc 0',      'STABILITY',        'LOW',    '2026-09-05 09:00+07', '2026-10-05', NULL, NULL, 'CANCELLED', 'tiepnhan', '2026-09-06 08:30+07', NULL, 'Trùng với mẫu đã đăng ký trước đó');

-- Giữ dải 25 số của năm 2026 trong một câu lệnh, rồi cấp số theo thứ tự thời điểm nhận. created_at là lúc đăng ký,
-- 5 phút sau khi nhận mẫu; updated_at là lần đổi trạng thái cuối (như LabSampleMapper.updateStatus ghi).
WITH block AS (
    INSERT INTO sample_code_counter AS c (year, last_no)
    VALUES (2026, (SELECT count(*) FROM dev_sample))
    ON CONFLICT (year) DO UPDATE SET last_no = c.last_no + EXCLUDED.last_no
    RETURNING last_no
),
inserted AS (
    INSERT INTO lab_sample (code, product_name, batch_no, sample_type, priority, status, received_at, due_date,
                            created_by, created_at, updated_at, conclusion, completed_at)
    SELECT 'LS-2026-' || lpad((b.last_no - d.total + d.seq)::text, 5, '0'),
           d.product_name, d.batch_no, d.sample_type, d.priority,
           COALESCE(d.ended_status, CASE WHEN d.started_at IS NULL THEN 'RECEIVED' ELSE 'IN_TESTING' END),
           d.received_at, d.due_date, 'tiepnhan', d.received_at + interval '5 minutes',
           COALESCE(d.ended_at, d.started_at, d.received_at + interval '5 minutes'),
           d.conclusion,
           CASE WHEN d.ended_status = 'COMPLETED' THEN d.ended_at END
    FROM (SELECT ds.*, row_number() OVER (ORDER BY ds.received_at) AS seq, count(*) OVER () AS total
          FROM dev_sample ds) d
    CROSS JOIN block b
    RETURNING id, batch_no
)
UPDATE dev_sample d
SET sample_id = i.id
FROM inserted i
WHERE i.batch_no = d.batch_no;

-- BR-04: dòng đăng ký (lý do cố định như LabSampleService.REGISTER_REASON), Nhận kiểm nghiệm (không lý do), rồi dòng
-- kết thúc (Kết luận: <nhãn> như CONCLUSION_REASON_PREFIX, hoặc lý do hủy).
INSERT INTO sample_status_history (sample_id, from_status, to_status, changed_by, changed_at, reason)
SELECT sample_id, NULL, 'RECEIVED', 'tiepnhan', received_at + interval '5 minutes', 'Đăng ký mẫu'
FROM dev_sample
UNION ALL
SELECT sample_id, 'RECEIVED', 'IN_TESTING', started_by, started_at, NULL
FROM dev_sample
WHERE started_at IS NOT NULL
UNION ALL
SELECT sample_id, CASE WHEN started_at IS NULL THEN 'RECEIVED' ELSE 'IN_TESTING' END, ended_status, ended_by, ended_at,
       CASE ended_status
           WHEN 'COMPLETED' THEN 'Kết luận: ' || CASE conclusion WHEN 'PASS' THEN 'Đạt' ELSE 'Không đạt' END
           ELSE cancel_reason
       END
FROM dev_sample
WHERE ended_status IS NOT NULL;

-- Kết quả, mỗi dòng một lần nhập. Người nhập là người đã nhận kiểm nghiệm; người duyệt khác người nhập (BR-08).
-- Đơn vị, tiêu chuẩn và Đạt/Không đạt lấy từ test_parameter lúc ghi, như TestResultService.enter (BR-07).
CREATE TEMP TABLE dev_result (
    batch_no       VARCHAR(50)  NOT NULL REFERENCES dev_sample,
    parameter_code VARCHAR(30)  NOT NULL,
    value          NUMERIC      NOT NULL,
    entered_by     VARCHAR(50)  NOT NULL,
    entered_at     TIMESTAMPTZ  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    reviewed_by    VARCHAR(50),
    reviewed_at    TIMESTAMPTZ,
    reject_reason  VARCHAR(500)
) ON COMMIT DROP;

INSERT INTO dev_result (batch_no, parameter_code, value, entered_by, entered_at, status, reviewed_by, reviewed_at, reject_reason)
VALUES
-- Đang kiểm nghiệm. Cột "Đã duyệt": F250 0/0 (không có dòng nào), L5 0/2, M80 2/3, N14 1/2, P60 5/5 (chờ kết luận),
-- Q9 1/1 (pH bị từ chối, chưa nhập lại), R2-ST03 0/2, S11 2/2.
('DEV-L5-2610',   'ASSAY',          99.2,  'kiemnghiem2', '2026-10-22 09:00+07', 'PENDING',  NULL,     NULL,                  NULL),
('DEV-L5-2610',   'PH',             6.1,   'kiemnghiem2', '2026-10-22 09:20+07', 'PENDING',  NULL,     NULL,                  NULL),
('DEV-M80-2610',  'ASSAY',          101.3, 'kiemnghiem1', '2026-10-20 09:00+07', 'APPROVED', 'duyet',  '2026-10-20 15:00+07', NULL),
('DEV-M80-2610',  'PH',             5.8,   'kiemnghiem1', '2026-10-20 09:30+07', 'APPROVED', 'duyet',  '2026-10-20 15:05+07', NULL),
('DEV-M80-2610',  'LOD',            0.3,   'kiemnghiem1', '2026-10-21 10:00+07', 'PENDING',  NULL,     NULL,                  NULL),
('DEV-N14-2610',  'ASSAY',          97.8,  'kiemnghiem2', '2026-10-17 09:00+07', 'APPROVED', 'duyet2', '2026-10-17 16:00+07', NULL),
('DEV-N14-2610',  'DISSOLUTION',    85,    'kiemnghiem2', '2026-10-19 10:00+07', 'PENDING',  NULL,     NULL,                  NULL),
('DEV-P60-2610',  'ASSAY',          100.6, 'kiemnghiem1', '2026-10-21 09:00+07', 'APPROVED', 'duyet',  '2026-10-21 14:00+07', NULL),
('DEV-P60-2610',  'PH',             5.5,   'kiemnghiem1', '2026-10-21 09:10+07', 'APPROVED', 'duyet',  '2026-10-21 14:05+07', NULL),
('DEV-P60-2610',  'LOD',            0.2,   'kiemnghiem1', '2026-10-21 09:20+07', 'APPROVED', 'duyet',  '2026-10-21 14:10+07', NULL),
('DEV-P60-2610',  'DISSOLUTION',    92,    'kiemnghiem1', '2026-10-21 09:30+07', 'APPROVED', 'duyet',  '2026-10-21 14:15+07', NULL),
('DEV-P60-2610',  'IMPURITY_TOTAL', 0.4,   'kiemnghiem1', '2026-10-21 09:40+07', 'APPROVED', 'duyet',  '2026-10-21 14:20+07', NULL),
('DEV-Q9-2610',   'ASSAY',          100.4, 'kiemnghiem1', '2026-10-16 09:00+07', 'APPROVED', 'duyet',  '2026-10-16 15:00+07', NULL),
('DEV-Q9-2610',   'PH',             7.4,   'kiemnghiem1', '2026-10-16 09:30+07', 'REJECTED', 'duyet2', '2026-10-16 15:30+07', 'Máy đo pH chưa hiệu chuẩn trong ngày, cần đo lại'),
('DEV-R2-ST03',   'ASSAY',          96.1,  'kiemnghiem2', '2026-10-20 10:00+07', 'PENDING',  NULL,     NULL,                  NULL),
('DEV-R2-ST03',   'DISSOLUTION',    78,    'kiemnghiem2', '2026-10-20 11:00+07', 'PENDING',  NULL,     NULL,                  NULL),
('DEV-S11-2610',  'LOD',            0.42,  'kiemnghiem1', '2026-10-15 09:00+07', 'APPROVED', 'duyet',  '2026-10-15 14:00+07', NULL),
('DEV-S11-2610',  'IMPURITY_TOTAL', 0.6,   'kiemnghiem1', '2026-10-15 09:30+07', 'APPROVED', 'duyet2', '2026-10-15 14:30+07', NULL),
-- Hoàn thành: đủ 5 chỉ tiêu Đã duyệt. D45-2609 Không đạt vì độ hòa tan 72 < 80; H20-ST03 vì tạp chất 1.3 > 1.0.
('DEV-C310-2609', 'ASSAY',          99.6,  'kiemnghiem1', '2026-09-03 09:00+07', 'APPROVED', 'duyet',  '2026-09-04 10:00+07', NULL),
('DEV-C310-2609', 'PH',             6.0,   'kiemnghiem1', '2026-09-03 09:10+07', 'APPROVED', 'duyet',  '2026-09-04 10:05+07', NULL),
('DEV-C310-2609', 'LOD',            0.25,  'kiemnghiem1', '2026-09-03 09:20+07', 'APPROVED', 'duyet',  '2026-09-04 10:10+07', NULL),
('DEV-C310-2609', 'DISSOLUTION',    88,    'kiemnghiem1', '2026-09-03 09:30+07', 'APPROVED', 'duyet',  '2026-09-04 10:15+07', NULL),
('DEV-C310-2609', 'IMPURITY_TOTAL', 0.3,   'kiemnghiem1', '2026-09-03 09:40+07', 'APPROVED', 'duyet',  '2026-09-04 10:20+07', NULL),
('DEV-A12-2609',  'ASSAY',          100.9, 'kiemnghiem2', '2026-09-11 08:00+07', 'APPROVED', 'duyet2', '2026-09-11 11:00+07', NULL),
('DEV-A12-2609',  'PH',             5.2,   'kiemnghiem2', '2026-09-11 08:10+07', 'APPROVED', 'duyet2', '2026-09-11 11:05+07', NULL),
('DEV-A12-2609',  'LOD',            0.15,  'kiemnghiem2', '2026-09-11 08:20+07', 'APPROVED', 'duyet2', '2026-09-11 11:10+07', NULL),
('DEV-A12-2609',  'DISSOLUTION',    95,    'kiemnghiem2', '2026-09-11 08:30+07', 'APPROVED', 'duyet2', '2026-09-11 11:15+07', NULL),
('DEV-A12-2609',  'IMPURITY_TOTAL', 0.2,   'kiemnghiem2', '2026-09-11 08:40+07', 'APPROVED', 'duyet2', '2026-09-11 11:20+07', NULL),
('DEV-D45-2609',  'ASSAY',          98.4,  'kiemnghiem1', '2026-09-19 09:00+07', 'APPROVED', 'duyet',  '2026-09-21 10:00+07', NULL),
('DEV-D45-2609',  'PH',             4.9,   'kiemnghiem1', '2026-09-19 09:10+07', 'APPROVED', 'duyet',  '2026-09-21 10:05+07', NULL),
('DEV-D45-2609',  'LOD',            0.35,  'kiemnghiem1', '2026-09-19 09:20+07', 'APPROVED', 'duyet',  '2026-09-21 10:10+07', NULL),
('DEV-D45-2609',  'DISSOLUTION',    72,    'kiemnghiem1', '2026-09-19 09:30+07', 'APPROVED', 'duyet',  '2026-09-21 10:15+07', NULL),
('DEV-D45-2609',  'IMPURITY_TOTAL', 0.5,   'kiemnghiem1', '2026-09-19 09:40+07', 'APPROVED', 'duyet',  '2026-09-21 10:20+07', NULL),
-- B07-2609: pH 7.2 bị từ chối; bản nhập lại 6.4 ghi riêng ở câu sau vì cần id của bản bị từ chối (BR-09).
('DEV-B07-2609',  'ASSAY',          97.1,  'kiemnghiem2', '2026-09-29 09:00+07', 'APPROVED', 'duyet',  '2026-09-29 14:05+07', NULL),
('DEV-B07-2609',  'PH',             7.2,   'kiemnghiem2', '2026-09-29 09:10+07', 'REJECTED', 'duyet',  '2026-09-29 14:00+07', 'Ghi nhầm giá trị của mẫu khác, đề nghị đo lại'),
('DEV-B07-2609',  'LOD',            0.28,  'kiemnghiem2', '2026-09-29 09:20+07', 'APPROVED', 'duyet',  '2026-09-29 14:10+07', NULL),
('DEV-B07-2609',  'DISSOLUTION',    83,    'kiemnghiem2', '2026-09-29 09:30+07', 'APPROVED', 'duyet',  '2026-09-29 14:15+07', NULL),
('DEV-B07-2609',  'IMPURITY_TOTAL', 0.7,   'kiemnghiem2', '2026-09-29 09:40+07', 'APPROVED', 'duyet',  '2026-09-29 14:20+07', NULL),
('DEV-H20-ST03',  'ASSAY',          95.4,  'kiemnghiem1', '2026-10-06 09:00+07', 'APPROVED', 'duyet2', '2026-10-07 10:00+07', NULL),
('DEV-H20-ST03',  'PH',             6.6,   'kiemnghiem1', '2026-10-06 09:10+07', 'APPROVED', 'duyet2', '2026-10-07 10:05+07', NULL),
('DEV-H20-ST03',  'LOD',            0.45,  'kiemnghiem1', '2026-10-06 09:20+07', 'APPROVED', 'duyet2', '2026-10-07 10:10+07', NULL),
('DEV-H20-ST03',  'DISSOLUTION',    81,    'kiemnghiem1', '2026-10-06 09:30+07', 'APPROVED', 'duyet2', '2026-10-07 10:15+07', NULL),
('DEV-H20-ST03',  'IMPURITY_TOTAL', 1.3,   'kiemnghiem1', '2026-10-06 09:40+07', 'APPROVED', 'duyet2', '2026-10-07 10:20+07', NULL),
('DEV-M80-2609',  'ASSAY',          102.2, 'kiemnghiem2', '2026-10-06 08:30+07', 'APPROVED', 'duyet',  '2026-10-06 20:00+07', NULL),
('DEV-M80-2609',  'PH',             6.3,   'kiemnghiem2', '2026-10-06 08:40+07', 'APPROVED', 'duyet',  '2026-10-06 20:05+07', NULL),
('DEV-M80-2609',  'LOD',            0.2,   'kiemnghiem2', '2026-10-06 08:50+07', 'APPROVED', 'duyet',  '2026-10-06 20:10+07', NULL),
('DEV-M80-2609',  'DISSOLUTION',    90,    'kiemnghiem2', '2026-10-06 09:00+07', 'APPROVED', 'duyet',  '2026-10-06 20:15+07', NULL),
('DEV-M80-2609',  'IMPURITY_TOTAL', 0.35,  'kiemnghiem2', '2026-10-06 09:10+07', 'APPROVED', 'duyet',  '2026-10-06 20:20+07', NULL);

INSERT INTO test_result (sample_id, parameter_id, value, unit, spec_min, spec_max, verdict, status,
                         entered_by, entered_at, reviewed_by, reviewed_at, reject_reason)
SELECT s.sample_id, p.id, r.value, p.unit, p.spec_min, p.spec_max,
       CASE WHEN (p.spec_min IS NULL OR r.value >= p.spec_min) AND (p.spec_max IS NULL OR r.value <= p.spec_max)
            THEN 'PASS' ELSE 'FAIL' END,
       r.status, r.entered_by, r.entered_at, r.reviewed_by, r.reviewed_at, r.reject_reason
FROM dev_result r
JOIN dev_sample s ON s.batch_no = r.batch_no
JOIN test_parameter p ON p.code = r.parameter_code
ORDER BY r.entered_at;

INSERT INTO test_result (sample_id, parameter_id, value, unit, spec_min, spec_max, verdict, status,
                         entered_by, entered_at, reviewed_by, reviewed_at, reject_reason, replaces_id)
SELECT rejected.sample_id, rejected.parameter_id, 6.4, rejected.unit, rejected.spec_min, rejected.spec_max, 'PASS',
       'APPROVED', 'kiemnghiem2', '2026-09-30 09:00+07', 'duyet', '2026-09-30 14:00+07', NULL, rejected.id
FROM test_result rejected
JOIN dev_sample s ON s.sample_id = rejected.sample_id
WHERE s.batch_no = 'DEV-B07-2609' AND rejected.status = 'REJECTED';

-- BR-10: mỗi lần nhập một dòng ENTER, mỗi lần duyệt/từ chối một dòng APPROVE/REJECT. detail là ảnh chụp kết quả
-- ngay sau thao tác, cùng khóa JSON với ResultAuditMapper.insert (ENTER chụp lúc còn Chờ duyệt).
INSERT INTO result_audit (result_id, action, actor, acted_at, detail)
SELECT t.id, a.action, a.actor, a.acted_at,
       jsonb_build_object('parameter', p.code, 'value', t.value, 'unit', t.unit,
                          'specMin', t.spec_min, 'specMax', t.spec_max, 'verdict', t.verdict,
                          'status', a.status, 'rejectReason', a.reject_reason,
                          'replacesId', t.replaces_id)
FROM test_result t
JOIN dev_sample s ON s.sample_id = t.sample_id
JOIN test_parameter p ON p.id = t.parameter_id
CROSS JOIN LATERAL (
    SELECT 'ENTER', t.entered_by, t.entered_at, 'PENDING', NULL::VARCHAR
    UNION ALL
    SELECT CASE t.status WHEN 'APPROVED' THEN 'APPROVE' ELSE 'REJECT' END, t.reviewed_by, t.reviewed_at, t.status,
           t.reject_reason
    WHERE t.status <> 'PENDING'
) AS a(action, actor, acted_at, status, reject_reason)
ORDER BY a.acted_at, t.id;

-- Tự kiểm tra trước khi COMMIT. CSDL đã chặn định dạng, tiêu chuẩn, 4 mắt…; ở đây kiểm những gì chỉ service kiểm.
DO $$
DECLARE
    bad TEXT;
BEGIN
    -- BR-11: kết luận của mẫu Hoàn thành khớp kết quả hiệu lực, như ConclusionReadiness.
    SELECT string_agg(s.batch_no, ', ') INTO bad
    FROM dev_sample s
    WHERE s.ended_status = 'COMPLETED'
      AND s.conclusion IS DISTINCT FROM (
          SELECT CASE WHEN count(*) = 0 OR bool_or(t.status = 'PENDING') THEN 'NOT_READY'
                      WHEN bool_and(t.verdict = 'PASS') THEN 'PASS'
                      ELSE 'FAIL' END
          FROM test_result t
          WHERE t.sample_id = s.sample_id AND t.status IN ('PENDING', 'APPROVED'));
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'Kết luận không khớp kết quả (BR-11): %', bad;
    END IF;

    -- Kết quả chỉ có ở mẫu đã nhận kiểm nghiệm, chưa hủy, và nằm giữa lúc nhận kiểm nghiệm và lúc kết luận.
    SELECT string_agg(DISTINCT s.batch_no, ', ') INTO bad
    FROM dev_sample s
    JOIN test_result t ON t.sample_id = s.sample_id
    WHERE s.started_at IS NULL OR s.ended_status = 'CANCELLED' OR t.entered_at <= s.started_at
       OR t.reviewed_at < t.entered_at OR COALESCE(t.reviewed_at, t.entered_at) >= s.ended_at;
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'Kết quả sai mốc thời gian hoặc sai trạng thái mẫu: %', bad;
    END IF;

    -- Lịch sử theo đúng thứ tự: đăng ký → nhận kiểm nghiệm → kết thúc, không có mốc nào ở tương lai.
    SELECT string_agg(batch_no, ', ') INTO bad
    FROM dev_sample
    WHERE started_at <= received_at + interval '5 minutes'
       OR ended_at <= COALESCE(started_at, received_at + interval '5 minutes')
       OR COALESCE(ended_at, started_at, received_at) > now();
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'Mốc thời gian của mẫu sai thứ tự hoặc ở tương lai: %', bad;
    END IF;
END
$$;

SELECT count(*)                                         AS so_mau,
       min(code)                                        AS ma_dau,
       max(code)                                        AS ma_cuoi,
       count(*) FILTER (WHERE status = 'RECEIVED')      AS da_tiep_nhan,
       count(*) FILTER (WHERE status = 'IN_TESTING')    AS dang_kiem_nghiem,
       count(*) FILTER (WHERE status = 'COMPLETED')     AS hoan_thanh,
       count(*) FILTER (WHERE status = 'CANCELLED')     AS da_huy,
       (SELECT last_no FROM sample_code_counter WHERE year = 2026) AS bo_dem_2026
FROM lab_sample
WHERE batch_no LIKE 'DEV-%';

\if :{?dryrun}
ROLLBACK;
\echo 'dryrun: đã ROLLBACK, CSDL không đổi.'
\else
COMMIT;
\endif

-- BR-06, BR-07: danh mục chỉ tiêu (LT-BRD mục 11). Phiên bản 1.0 không có màn hình quản lý chỉ tiêu
-- (LT-BRD mục 3): thêm chỉ tiêu, đổi tiêu chuẩn hay ngừng dùng chỉ tiêu đều bằng migration mới.
CREATE TABLE test_parameter (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(30)  NOT NULL
                  CONSTRAINT uq_test_parameter_code UNIQUE
                  CONSTRAINT ck_test_parameter_code CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    name          VARCHAR(200) NOT NULL
                  CONSTRAINT ck_test_parameter_name CHECK (btrim(name) <> ''),
    unit          VARCHAR(20)  NOT NULL
                  CONSTRAINT ck_test_parameter_unit CHECK (btrim(unit) <> ''),
    -- NUMERIC không cố định scale để giữ đúng cách viết của tiêu chuẩn: "1.0" chứ không thành "1.0000".
    -- NULL = không giới hạn phía đó (BR-07).
    spec_min      NUMERIC
                  CONSTRAINT ck_test_parameter_spec_min CHECK (spec_min >= 0 AND scale(spec_min) <= 4),
    spec_max      NUMERIC
                  CONSTRAINT ck_test_parameter_spec_max CHECK (spec_max >= 0 AND scale(spec_max) <= 4),
    -- BR-06: chỉ nhập kết quả cho chỉ tiêu đang được dùng.
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order SMALLINT     NOT NULL
                  CONSTRAINT uq_test_parameter_display_order UNIQUE,
    -- Chỉ tiêu không có biên nào thì mọi giá trị đều Đạt, tức là không kiểm tra gì.
    CONSTRAINT ck_test_parameter_spec_bound CHECK (spec_min IS NOT NULL OR spec_max IS NOT NULL),
    CONSTRAINT ck_test_parameter_spec_range CHECK (spec_min IS NULL OR spec_max IS NULL OR spec_min <= spec_max)
);

-- Dữ liệu tham chiếu, không phải dữ liệu demo: nằm trong migration chứ không ở db/devdata.
INSERT INTO test_parameter (code, name, unit, spec_min, spec_max, display_order)
VALUES ('ASSAY',          'Định lượng hoạt chất',      '%',  95,   105,  1),
       ('PH',             'pH',                        'pH', 4.5,  7,    2),
       ('LOD',            'Mất khối lượng do làm khô', '%',  NULL, 0.5,  3),
       ('DISSOLUTION',    'Độ hòa tan (30 phút)',      '%',  80,   NULL, 4),
       ('IMPURITY_TOTAL', 'Tạp chất tổng',             '%',  NULL, 1.0,  5);

-- BR-06..BR-09: kết quả kiểm nghiệm. Không xóa cứng (NFR-03): bản bị từ chối được giữ lại,
-- bản nhập lại là một dòng mới trỏ về bản cũ qua replaces_id.
CREATE TABLE test_result (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sample_id     BIGINT       NOT NULL
                  CONSTRAINT fk_test_result_sample REFERENCES lab_sample (id) ON DELETE RESTRICT,
    parameter_id  BIGINT       NOT NULL
                  CONSTRAINT fk_test_result_parameter REFERENCES test_parameter (id) ON DELETE RESTRICT,
    -- BR-06: ≥ 0, tối đa 8 chữ số phần nguyên và 4 chữ số thập phân. Không dùng NUMERIC(12,4): kiểu đó
    -- làm tròn ngầm 1.23456 thành 1.2346 thay vì từ chối, và đổi 98.50 người dùng nhập thành 98.5000.
    value         NUMERIC      NOT NULL
                  CONSTRAINT ck_test_result_value
                  CHECK (value >= 0 AND value < 100000000 AND scale(value) <= 4),
    -- Tiêu chuẩn chụp từ test_parameter lúc nhập (ALCOA+): migration sau này đổi danh mục thì kết quả
    -- cũ vẫn mang đúng tiêu chuẩn đã dùng để đánh giá nó.
    unit          VARCHAR(20)  NOT NULL
                  CONSTRAINT ck_test_result_unit CHECK (btrim(unit) <> ''),
    spec_min      NUMERIC,
    spec_max      NUMERIC,
    verdict       VARCHAR(10)  NOT NULL
                  CONSTRAINT ck_test_result_verdict CHECK (verdict IN ('PASS', 'FAIL')),
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                  CONSTRAINT ck_test_result_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    entered_by    VARCHAR(50)  NOT NULL
                  CONSTRAINT ck_test_result_entered_by CHECK (btrim(entered_by) <> ''),
    entered_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_by   VARCHAR(50)
                  CONSTRAINT ck_test_result_reviewed_by CHECK (btrim(reviewed_by) <> ''),
    reviewed_at   TIMESTAMPTZ,
    reject_reason VARCHAR(500),
    -- BR-09: bản nhập lại trỏ về bản bị từ chối mà nó thay. UNIQUE: mỗi bản bị từ chối chỉ được thay một lần,
    -- nên hai người nhập lại cùng lúc thì một người bị chặn.
    replaces_id   BIGINT
                  CONSTRAINT uq_test_result_replaces UNIQUE,
    -- Đích của fk_test_result_replaces: bản thay phải cùng mẫu, cùng chỉ tiêu với bản bị thay.
    CONSTRAINT uq_test_result_identity UNIQUE (id, sample_id, parameter_id),
    CONSTRAINT fk_test_result_replaces FOREIGN KEY (replaces_id, sample_id, parameter_id)
        REFERENCES test_result (id, sample_id, parameter_id) ON DELETE RESTRICT,
    CONSTRAINT ck_test_result_replaces_self CHECK (replaces_id <> id),
    CONSTRAINT ck_test_result_spec_range CHECK (spec_min IS NULL OR spec_max IS NULL OR spec_min <= spec_max),
    -- BR-07: verdict phải khớp giá trị và tiêu chuẩn đã chụp. So nguyên văn, tính cả biên, biên NULL là
    -- không giới hạn. Service tự tính verdict; ràng buộc này chặn mọi đường ghi khác.
    CONSTRAINT ck_test_result_verdict_spec CHECK (verdict = CASE
        WHEN (spec_min IS NULL OR value >= spec_min) AND (spec_max IS NULL OR value <= spec_max) THEN 'PASS'
        ELSE 'FAIL' END),
    -- BR-08: nguyên tắc 4 mắt. Tên ràng buộc theo đúng LT-BRD.
    CONSTRAINT ck_four_eyes CHECK (reviewed_by IS NULL OR reviewed_by <> entered_by),
    -- Chờ duyệt ⇔ chưa có người xử lý; người xử lý và thời điểm xử lý luôn đi cùng nhau.
    CONSTRAINT ck_test_result_review
        CHECK ((status = 'PENDING') = (reviewed_by IS NULL) AND (reviewed_by IS NULL) = (reviewed_at IS NULL)),
    -- BR-09: Đã từ chối ⇔ có lý do 5–500 ký tự sau khi cắt khoảng trắng (cận trên do VARCHAR(500) chặn).
    CONSTRAINT ck_test_result_reject_reason
        CHECK ((status = 'REJECTED') = (reject_reason IS NOT NULL)
               AND (reject_reason IS NULL OR char_length(btrim(reject_reason)) >= 5))
);

-- BR-06: mỗi chỉ tiêu trên một mẫu chỉ có 1 kết quả hiệu lực (Chờ duyệt hoặc Đã duyệt), kể cả khi
-- hai người nhập cùng lúc. Bản Đã từ chối nằm ngoài index nên vẫn nhập lại được.
CREATE UNIQUE INDEX uq_test_result_effective ON test_result (sample_id, parameter_id)
    WHERE status IN ('PENDING', 'APPROVED');

-- Trang chi tiết đọc mọi kết quả của một mẫu (UC-12).
CREATE INDEX ix_test_result_sample ON test_result (sample_id, parameter_id, id);

-- BR-10: audit trail kết quả, chỉ thêm (NFR-03). Không lưu sample_id: trang chi tiết JOIN qua test_result,
-- nên không có cột nào có thể lệch với kết quả mà dòng audit nói tới.
CREATE TABLE result_audit (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    result_id BIGINT      NOT NULL
              CONSTRAINT fk_result_audit_result REFERENCES test_result (id) ON DELETE RESTRICT,
    action    VARCHAR(10) NOT NULL
              CONSTRAINT ck_result_audit_action CHECK (action IN ('ENTER', 'APPROVE', 'REJECT')),
    -- NFR-03 (ALCOA+, chữ A là Attributable).
    actor     VARCHAR(50) NOT NULL
              CONSTRAINT ck_result_audit_actor CHECK (btrim(actor) <> ''),
    acted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Ảnh chụp dòng test_result ngay sau thao tác, dựng bằng jsonb_build_object trong ResultAuditMapper.xml.
    detail    JSONB       NOT NULL
              CONSTRAINT ck_result_audit_detail CHECK (jsonb_typeof(detail) = 'object')
);

CREATE INDEX ix_result_audit_result ON result_audit (result_id, acted_at);

-- NFR-03, BR-10: chặn ở CSDL để cả SQL ghi thẳng cũng không sửa, xóa được dữ liệu chỉ thêm.
-- ERRCODE restrict_violation (23001) thuộc lớp 23, nên Spring đổi thành DataIntegrityViolationException.
-- Thông báo mở đầu bằng tên trigger để test so khớp.
CREATE FUNCTION forbid_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '%: không được % trên bảng %', TG_NAME, TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER tg_result_audit_append_only
    BEFORE UPDATE OR DELETE ON result_audit
    FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TRIGGER tg_result_audit_no_truncate
    BEFORE TRUNCATE ON result_audit
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_change();

CREATE TRIGGER tg_test_result_no_delete
    BEFORE DELETE ON test_result
    FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TRIGGER tg_test_result_no_truncate
    BEFORE TRUNCATE ON test_result
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_change();

-- BR-08, BR-09, NFR-03: một kết quả chỉ được đổi đúng một lần, từ Chờ duyệt sang Đã duyệt hoặc Đã từ chối,
-- và chỉ ở 4 cột của bước xử lý (status, reviewed_by, reviewed_at, reject_reason). Số được so ở dạng text
-- để cả việc đổi 98.50 thành 98.5 (bằng nhau về giá trị) cũng bị chặn.
CREATE FUNCTION guard_test_result_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status <> 'PENDING' OR NEW.status NOT IN ('APPROVED', 'REJECTED')
       OR (NEW.id, NEW.sample_id, NEW.parameter_id, NEW.value::text, NEW.unit, NEW.spec_min::text,
           NEW.spec_max::text, NEW.verdict, NEW.entered_by, NEW.entered_at, NEW.replaces_id)
          IS DISTINCT FROM
          (OLD.id, OLD.sample_id, OLD.parameter_id, OLD.value::text, OLD.unit, OLD.spec_min::text,
           OLD.spec_max::text, OLD.verdict, OLD.entered_by, OLD.entered_at, OLD.replaces_id)
    THEN
        RAISE EXCEPTION '%: kết quả % chỉ được chuyển một lần từ PENDING sang APPROVED hoặc REJECTED', TG_NAME, OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_test_result_guard_update
    BEFORE UPDATE ON test_result
    FOR EACH ROW EXECUTE FUNCTION guard_test_result_update();

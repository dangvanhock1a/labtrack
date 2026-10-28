-- BR-02: ck_lab_sample_product của V6_1 chỉ cắt 6 khoảng trắng ASCII, nên tên chỉ gồm ký tự điều khiển khác như U+001F
-- vẫn lọt (code review TC-BR02-28). Tên phải có ít nhất một ký tự sau U+0020: đúng tập ký tự mà String.trim của form
-- (StringTrimmerEditor rồi @NotBlank) cắt bỏ; PostgreSQL không lưu được U+0000. V6_1 đã commit nên không sửa tại chỗ.
-- Số 6.2 cùng lý do với 6.1: mục 13 docs/yeu-cau-nghiep-vu.md đã dành V7 cho BR-17.
ALTER TABLE lab_sample
    DROP CONSTRAINT ck_lab_sample_product,
    ADD CONSTRAINT ck_lab_sample_product CHECK (product_name ~ '[^\x01-\x20]');

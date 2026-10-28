-- BR-02: tên sản phẩm chỉ gồm tab hay xuống dòng lọt qua ck_lab_sample_product của V1, vì btrim một tham số chỉ cắt
-- dấu cách (TC-BR02-28). Cắt thêm các khoảng trắng ASCII khác (tab, xuống dòng, CR, form feed, vertical tab), là những
-- ký tự form đã coi là trống. Số 6.1 chứ không phải 7: mục 13 docs/yeu-cau-nghiep-vu.md đã dành V7 cho BR-17.
ALTER TABLE lab_sample
    DROP CONSTRAINT ck_lab_sample_product,
    ADD CONSTRAINT ck_lab_sample_product CHECK (btrim(product_name, E' \t\n\r\f\x0B') <> '');

package com.pharmalab.labtrack.domain;

/**
 * Chuẩn hóa số lô (quy tắc luyện tập, không thuộc BRD): cắt khoảng trắng đầu/cuối, chuyển chữ hoa, chỉ nhận
 * A-Z, 0-9, '-', '/', dài 1–20 ký tự sau chuẩn hóa. Vi phạm → {@code BusinessException} mã
 * {@code BATCH_NO_INVALID}.
 */
public final class BatchNoNormalizer {

	private BatchNoNormalizer() {
	}

	public static String normalize(String raw) {
		throw new UnsupportedOperationException("Chưa implement");
	}

}

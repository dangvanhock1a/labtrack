package com.pharmalab.labtrack.domain;

import java.util.Locale;
import java.util.regex.Pattern;

import com.pharmalab.labtrack.service.BusinessException;

/**
 * Chuẩn hóa số lô (quy tắc luyện tập, không thuộc BRD): cắt khoảng trắng đầu/cuối, chuyển chữ hoa, chỉ nhận
 * A-Z, 0-9, '-', '/', dài 1–20 ký tự sau chuẩn hóa. Vi phạm → {@code BusinessException} mã
 * {@code BATCH_NO_INVALID}.
 */
public final class BatchNoNormalizer {

	private static final int MAX_LENGTH = 20;

	private static final Pattern VALID = Pattern.compile("[A-Z0-9/-]{1," + MAX_LENGTH + "}");

	private BatchNoNormalizer() {
	}

	/**
	 * Kiểm tra trên chuỗi đã chuẩn hóa chứ không trên chuỗi nhập, nên khoảng trắng đầu/cuối không tính vào độ
	 * dài. {@code null} đi chung đường với chuỗi rỗng để chỉ có một chỗ ném lỗi. Chuyển chữ hoa theo
	 * {@code Locale.ROOT}: theo locale tiếng Thổ Nhĩ Kỳ, "i" thành "İ" và số lô hợp lệ bị chặn oan.
	 */
	public static String normalize(String raw) {
		String normalized = raw != null ? raw.trim().toUpperCase(Locale.ROOT) : "";
		if (!VALID.matcher(normalized).matches()) {
			throw new BusinessException("BATCH_NO_INVALID", "batchNo",
					"Số lô chỉ gồm chữ không dấu, số, dấu - và /, dài 1–" + MAX_LENGTH + " ký tự");
		}
		return normalized;
	}

}

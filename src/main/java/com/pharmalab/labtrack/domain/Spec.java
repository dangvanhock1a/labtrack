package com.pharmalab.labtrack.domain;

import java.math.BigDecimal;

/**
 * Tiêu chuẩn của một chỉ tiêu (BR-07): {@code min}, {@code max} là biên tính cả hai đầu, để trống là không
 * giới hạn phía đó. Phải có ít nhất một biên, giống {@code ck_test_parameter_spec_bound} (V4): không có biên
 * nào thì mọi giá trị đều Đạt, tức là không kiểm tra gì.
 */
public record Spec(BigDecimal min, BigDecimal max) {

	public Spec {
		if (min == null && max == null) {
			throw new IllegalArgumentException("Tiêu chuẩn phải có ít nhất một biên");
		}
	}

	/**
	 * So nguyên văn, không làm tròn (SPEC-004 D6): ASSAY 95–105, đo 105.04 là Không đạt. Dùng
	 * {@code compareTo} chứ không dùng {@code equals}, vì {@code equals} coi {@code 105.00} khác {@code 105}.
	 * Cùng công thức với {@code ck_test_result_verdict_spec}, nên CSDL chặn mọi verdict tính khác cách này.
	 */
	public Verdict evaluate(BigDecimal value) {
		boolean aboveMin = min == null || value.compareTo(min) >= 0;
		boolean belowMax = max == null || value.compareTo(max) <= 0;
		return (aboveMin && belowMax) ? Verdict.PASS : Verdict.FAIL;
	}

	/** Nhãn hiển thị: "95 – 105", "≤ 0.5", "≥ 80". In bằng {@code toPlainString()} để "1.0" vẫn là "1.0". */
	public String label() {
		if (min != null && max != null) {
			return min.toPlainString() + " – " + max.toPlainString();
		}
		return (max != null) ? "≤ " + max.toPlainString() : "≥ " + min.toPlainString();
	}

}

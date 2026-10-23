package com.pharmalab.labtrack.domain;

/**
 * Đánh giá kết quả so với tiêu chuẩn (BR-07). Do hệ thống tự tính bằng {@link Spec#evaluate}, người dùng
 * không chọn. Tên hằng được lưu nguyên văn vào cột {@code test_result.verdict}.
 */
public enum Verdict {

	PASS("Đạt"),
	FAIL("Không đạt");

	private final String label;

	Verdict(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

}

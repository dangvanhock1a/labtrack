package com.pharmalab.labtrack.domain;

/**
 * Mức ưu tiên của mẫu (BR-13). Tên hằng được lưu nguyên văn vào cột {@code lab_sample.priority}. Mặc định
 * Bình thường: {@code LabSampleService.DEFAULT_PRIORITY} cho form và lúc đăng ký, {@code DEFAULT} của cột (V6)
 * cho mẫu có trước V6.
 */
public enum SamplePriority {

	LOW("Thấp"),
	NORMAL("Bình thường"),
	URGENT("Khẩn");

	private final String label;

	SamplePriority(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

}

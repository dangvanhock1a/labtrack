package com.pharmalab.labtrack.domain;

/** Loại mẫu (BR-02). Tên hằng được lưu nguyên văn vào cột {@code lab_sample.sample_type}. */
public enum SampleType {

	RAW_MATERIAL("Nguyên liệu"),
	INTERMEDIATE("Bán thành phẩm"),
	FINISHED_PRODUCT("Thành phẩm"),
	STABILITY("Mẫu độ ổn định");

	private final String label;

	SampleType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

}

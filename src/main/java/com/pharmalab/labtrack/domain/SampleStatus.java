package com.pharmalab.labtrack.domain;

/** Trạng thái mẫu (BR-03). Tên hằng được lưu nguyên văn vào cột {@code lab_sample.status}. */
public enum SampleStatus {

	RECEIVED("Đã tiếp nhận"),
	IN_TESTING("Đang kiểm nghiệm"),
	COMPLETED("Hoàn thành"),
	CANCELLED("Đã hủy");

	private final String label;

	SampleStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

}

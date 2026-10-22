package com.pharmalab.labtrack.domain;

/** Trạng thái mẫu. Tên hằng phải khớp CHECK ck_lab_sample_status. Bảng chuyển trạng thái (BR-03) làm ở Lab 04. */
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

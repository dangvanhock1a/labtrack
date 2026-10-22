package com.pharmalab.labtrack.domain;

/** Trạng thái mẫu và bảng chuyển trạng thái (BR-03). Tên hằng phải khớp CHECK ck_lab_sample_status. */
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

	public boolean canTransitionTo(SampleStatus target) {
		return switch (this) {
			case RECEIVED -> target == IN_TESTING || target == CANCELLED;
			case IN_TESTING -> target == COMPLETED || target == CANCELLED;
			case COMPLETED, CANCELLED -> false;
		};
	}

	public boolean isTerminal() {
		return this == COMPLETED || this == CANCELLED;
	}

}

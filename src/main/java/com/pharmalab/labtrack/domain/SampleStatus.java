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

	/**
	 * Có được chuyển từ trạng thái này sang {@code target} không (BR-03). Mọi cặp không có
	 * trong bảng dưới đây đều bị chặn, kể cả chuyển sang chính nó.
	 */
	public boolean canTransitionTo(SampleStatus target) {
		return switch (this) {
			case RECEIVED -> target == IN_TESTING || target == CANCELLED;
			case IN_TESTING -> target == COMPLETED || target == CANCELLED;
			case COMPLETED, CANCELLED -> false;
		};
	}

	/** Hoàn thành và Đã hủy là trạng thái kết thúc (BR-03). */
	public boolean isTerminal() {
		return switch (this) {
			case RECEIVED, IN_TESTING -> false;
			case COMPLETED, CANCELLED -> true;
		};
	}

}

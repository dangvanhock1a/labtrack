package com.pharmalab.labtrack.domain;

/**
 * Trạng thái kết quả kiểm nghiệm (BR-08, BR-09). Tên hằng được lưu nguyên văn vào cột
 * {@code test_result.status} và được so dạng chuỗi trong trigger {@code guard_test_result_update} (V4).
 */
public enum ResultStatus {

	PENDING("Chờ duyệt"),
	APPROVED("Đã duyệt"),
	REJECTED("Đã từ chối");

	private final String label;

	ResultStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/**
	 * Kết quả hiệu lực là kết quả Chờ duyệt hoặc Đã duyệt (LT-BRD mục 5). Bản Đã từ chối không tính,
	 * nên chỉ tiêu đó nhập lại được (BR-06, BR-09); index {@code uq_test_result_effective} dùng đúng định
	 * nghĩa này.
	 */
	public boolean isEffective() {
		return switch (this) {
			case PENDING, APPROVED -> true;
			case REJECTED -> false;
		};
	}

}

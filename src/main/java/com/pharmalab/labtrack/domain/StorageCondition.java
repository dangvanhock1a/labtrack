package com.pharmalab.labtrack.domain;

/**
 * Điều kiện bảo quản của mẫu (BR-18). Tên hằng được lưu nguyên văn vào cột {@code lab_sample.storage_condition}.
 * Nhãn kèm khoảng nhiệt độ vì người đọc phiếu hay file CSV cần biết đúng điều kiện, không chỉ tên gọi. Mặc định Nhiệt
 * độ phòng: {@code LabSampleService.DEFAULT_STORAGE_CONDITION} cho form và lúc đăng ký, {@code DEFAULT} của cột (V7)
 * cho mẫu có trước V7.
 */
public enum StorageCondition {

	ROOM_TEMPERATURE("Nhiệt độ phòng (15–30 °C)"),
	COOL("Mát (2–8 °C)"),
	FROZEN("Đông lạnh (≤ −20 °C)");

	private final String label;

	StorageCondition(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

}

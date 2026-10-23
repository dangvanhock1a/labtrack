package com.pharmalab.labtrack.domain;

/** Thao tác được ghi vào audit trail kết quả (BR-10). Tên hằng được lưu nguyên văn vào cột {@code result_audit.action}. */
public enum ResultAction {

	ENTER("Nhập"),
	APPROVE("Duyệt"),
	REJECT("Từ chối");

	private final String label;

	ResultAction(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

}

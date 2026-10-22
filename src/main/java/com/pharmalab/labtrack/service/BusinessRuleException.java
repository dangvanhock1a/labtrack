package com.pharmalab.labtrack.service;

/** Vi phạm quy tắc nghiệp vụ; thông báo hiển thị thẳng cho người dùng. */
public class BusinessRuleException extends RuntimeException {

	private final String field;

	public BusinessRuleException(String message) {
		this(null, message);
	}

	public BusinessRuleException(String field, String message) {
		super(message);
		this.field = field;
	}

	/** Tên ô nhập gây lỗi (trùng tên thành phần của command), hoặc {@code null} nếu là lỗi chung. */
	public String getField() {
		return field;
	}

}

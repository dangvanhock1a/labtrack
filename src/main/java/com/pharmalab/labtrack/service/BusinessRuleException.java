package com.pharmalab.labtrack.service;

/** Vi phạm quy tắc nghiệp vụ; thông báo hiển thị thẳng cho người dùng. */
public class BusinessRuleException extends RuntimeException {

	private final String field;
	private final String code;

	public BusinessRuleException(String message) {
		this(null, message);
	}

	public BusinessRuleException(String field, String message) {
		this(field, message, null);
	}

	public BusinessRuleException(String field, String message, String code) {
		super(message);
		this.field = field;
		this.code = code;
	}

	/** Tên ô nhập gây lỗi (trùng tên thành phần của command), hoặc {@code null} nếu là lỗi chung. */
	public String getField() {
		return field;
	}

	/** Mã lỗi ổn định để code/test phân biệt các lỗi, ví dụ {@code CONCURRENT_UPDATE}; có thể {@code null}. */
	public String getCode() {
		return code;
	}

}

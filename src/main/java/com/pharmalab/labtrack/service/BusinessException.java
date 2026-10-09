package com.pharmalab.labtrack.service;

/**
 * Vi phạm quy tắc nghiệp vụ, thông báo bằng tiếng Việt để hiển thị thẳng cho người dùng.
 * {@code field} là tên trường trên form gây lỗi, hoặc {@code null} nếu lỗi không thuộc
 * trường nào.
 */
public class BusinessException extends RuntimeException {

	private final String field;

	public BusinessException(String message) {
		this(null, message);
	}

	public BusinessException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String getField() {
		return field;
	}

}

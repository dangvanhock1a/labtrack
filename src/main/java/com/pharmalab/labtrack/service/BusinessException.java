package com.pharmalab.labtrack.service;

/**
 * Vi phạm quy tắc nghiệp vụ, thông báo bằng tiếng Việt để hiển thị thẳng cho người dùng.
 * {@code code} là mã lỗi ổn định để phân biệt loại lỗi mà không phụ thuộc câu chữ, hoặc
 * {@code null}. {@code field} là tên trường trên form gây lỗi, hoặc {@code null} nếu lỗi không
 * thuộc trường nào.
 */
public class BusinessException extends RuntimeException {

	private final String code;

	private final String field;

	public BusinessException(String message) {
		this(null, null, message);
	}

	public BusinessException(String field, String message) {
		this(null, field, message);
	}

	public BusinessException(String code, String field, String message) {
		super(message);
		this.code = code;
		this.field = field;
	}

	public String getCode() {
		return code;
	}

	public String getField() {
		return field;
	}

}

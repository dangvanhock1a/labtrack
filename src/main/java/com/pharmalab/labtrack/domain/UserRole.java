package com.pharmalab.labtrack.domain;

/**
 * Vai trò người dùng (LT-BRD mục 4, BR-12). Tên hằng được lưu nguyên văn vào cột
 * {@code app_user.role}, có {@code CHECK ck_app_user_role} liệt kê đúng 4 giá trị này, và cũng là
 * tên vai trò mà {@code SecurityConfig} so khớp. Đổi tên hằng là phải viết migration cho CHECK đó,
 * và sửa cả các chuỗi {@code hasRole('…')} trong {@code sec:authorize} của template.
 */
public enum UserRole {

	RECEPTIONIST("Tiếp nhận mẫu"),
	ANALYST("Kiểm nghiệm viên"),
	REVIEWER("Người duyệt (QA)"),
	MANAGER("Quản lý");

	private final String label;

	UserRole(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

}

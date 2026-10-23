package com.pharmalab.labtrack.domain;

/** Một tài khoản đăng nhập, ánh xạ 1-1 với bảng {@code app_user}. */
public class AppUser {

	private Long id;

	private String username;

	private String fullName;

	/** Hash kèm tiền tố thuật toán, ví dụ {@code {bcrypt}$2a$10$…}; không bao giờ là mật khẩu gốc. */
	private String passwordHash;

	private UserRole role;

	private boolean enabled;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public UserRole getRole() {
		return role;
	}

	public void setRole(UserRole role) {
		this.role = role;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}

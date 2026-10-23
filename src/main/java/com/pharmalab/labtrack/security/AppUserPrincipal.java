package com.pharmalab.labtrack.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.pharmalab.labtrack.domain.AppUser;
import com.pharmalab.labtrack.domain.UserRole;

/**
 * Người đăng nhập, nằm trong phiên. Ngoài những gì {@code User} của Spring Security có, lớp này giữ
 * thêm họ tên và vai trò để menu hiển thị mà không phải truy vấn lại CSDL ở mỗi trang.
 * <p>
 * Cài {@link CredentialsContainer} để Spring Security xóa hash mật khẩu sau khi xác thực xong,
 * nên hash không nằm lại trong phiên.
 */
public class AppUserPrincipal implements UserDetails, CredentialsContainer {

	private final String username;

	private final String fullName;

	private final UserRole role;

	private final boolean enabled;

	private String passwordHash;

	public AppUserPrincipal(AppUser user) {
		this.username = user.getUsername();
		this.fullName = user.getFullName();
		this.role = user.getRole();
		this.enabled = user.isEnabled();
		this.passwordHash = user.getPasswordHash();
	}

	/** Mỗi tài khoản có đúng một vai trò; {@code hasRole('X')} trong cấu hình so với {@code ROLE_X}. */
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void eraseCredentials() {
		passwordHash = null;
	}

	public String getFullName() {
		return fullName;
	}

	public UserRole getRole() {
		return role;
	}

}

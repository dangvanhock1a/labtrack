package com.pharmalab.labtrack.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.pharmalab.labtrack.persistence.AppUserMapper;

/**
 * Nạp tài khoản cho form đăng nhập (UC-01) từ bảng {@code app_user} qua MyBatis. Đặt ở package
 * {@code security} thay vì {@code service} để tầng nghiệp vụ không phụ thuộc Spring Security: tầng
 * đó chỉ nhận tên người thực hiện dưới dạng chuỗi.
 * <p>
 * Spring Security tự lấy bean này (bean {@code UserDetailsService} duy nhất) cùng
 * {@code PasswordEncoder} để dựng bộ xác thực, nên {@code SecurityConfig} không cần inject nó.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

	private final AppUserMapper userMapper;

	public AppUserDetailsService(AppUserMapper userMapper) {
		this.userMapper = userMapper;
	}

	/**
	 * Thông báo của exception không tới tay người dùng: Spring Security đổi nó thành lỗi "sai thông
	 * tin đăng nhập" chung, để không lộ tài khoản nào tồn tại.
	 */
	@Override
	public UserDetails loadUserByUsername(String username) {
		return userMapper.findByUsername(username)
			.map(AppUserPrincipal::new)
			.orElseThrow(() -> new UsernameNotFoundException("Không có tài khoản " + username));
	}

}

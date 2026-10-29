package com.pharmalab.labtrack.config;

import static com.pharmalab.labtrack.domain.UserRole.ANALYST;
import static com.pharmalab.labtrack.domain.UserRole.MANAGER;
import static com.pharmalab.labtrack.domain.UserRole.RECEPTIONIST;
import static com.pharmalab.labtrack.domain.UserRole.REVIEWER;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phân quyền theo URL (BR-12, ma trận LT-BRD mục 9) và đăng nhập bằng form (UC-01, NFR-02).
 * <p>
 * Đây là nơi cưỡng chế quyền duy nhất: giao diện chỉ ẩn nút cho dễ dùng, còn gõ thẳng URL hay gửi
 * POST bằng công cụ khác vẫn phải qua các luật dưới đây. Luật cuối là {@code denyAll()}: endpoint
 * mới chưa được khai báo ở đây sẽ bị chặn, không vô tình mở cho mọi người đã đăng nhập.
 * <p>
 * Lớp này cố ý không inject {@code UserDetailsService} hay mapper. Spring Security tự tìm bean
 * {@code UserDetailsService} và {@link PasswordEncoder} duy nhất, nên {@code @WebMvcTest} có thể
 * {@code @Import} lớp này mà không phải dựng tầng persistence.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	/**
	 * Luật được so theo thứ tự khai báo, luật đầu tiên khớp sẽ quyết định. Vì vậy
	 * {@code /samples/new} và {@code /samples/export.csv} phải đứng trước {@code GET /samples/*},
	 * nếu không chúng sẽ bị coi là "xem chi tiết" và mở cho mọi vai trò.
	 * <p>
	 * Duyệt/từ chối kết quả chỉ dành cho {@code REVIEWER} (BR-08, SPEC-004 R9). {@code *} chỉ khớp một
	 * đoạn đường dẫn, nên {@code /samples/*}{@code /results} không khớp {@code /samples/1/results/2/approve}
	 * và hai luật không chồng nhau. Kết luận mẫu cũng chỉ dành cho {@code REVIEWER} (BR-11, ma trận mục 9).
	 * <p>
	 * Trang chủ {@code /} là trang Tổng quan, mở cho mọi vai trò đã đăng nhập (BR-15, ma trận mục 9), và là nơi tới
	 * sau khi đăng nhập. Người bị chuyển tới trang đăng nhập khi đang mở một URL khác vẫn được đưa về URL đó.
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/login", "/error", "/webjars/**", "/css/**", "/js/**", "/images/**",
						"/favicon.ico")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/samples/new").hasRole(RECEPTIONIST.name())
				.requestMatchers(HttpMethod.POST, "/samples").hasRole(RECEPTIONIST.name())
				.requestMatchers(HttpMethod.GET, "/samples/export.csv").hasAnyRole(REVIEWER.name(), MANAGER.name())
				.requestMatchers(HttpMethod.POST, "/samples/*/start", "/samples/*/results")
				.hasAnyRole(ANALYST.name(), REVIEWER.name())
				.requestMatchers(HttpMethod.POST, "/samples/*/cancel").hasAnyRole(RECEPTIONIST.name(), REVIEWER.name())
				.requestMatchers(HttpMethod.POST, "/samples/*/results/*/approve", "/samples/*/results/*/reject")
				.hasRole(REVIEWER.name())
				.requestMatchers(HttpMethod.POST, "/samples/*/conclude").hasRole(REVIEWER.name())
				.requestMatchers(HttpMethod.GET, "/").authenticated()
				.requestMatchers(HttpMethod.GET, "/samples", "/samples/*").authenticated()
				.anyRequest().denyAll())
			// /login đã permitAll ở luật đầu, nên không gọi permitAll() của formLogin nữa.
			.formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/"))
			.logout(logout -> logout.logoutSuccessUrl("/login?logout"));
		return http.build();
	}

	/**
	 * Hash trong CSDL mang tiền tố thuật toán ({@code {bcrypt}…}), nên đổi thuật toán sau này không
	 * làm hỏng các hash cũ: encoder ủy quyền chọn đúng thuật toán theo tiền tố đó.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}

package com.pharmalab.labtrack.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.pharmalab.labtrack.TestcontainersConfiguration;

/**
 * Đăng nhập thật qua cả chuỗi (UC-01): form, bộ lọc Spring Security, {@link AppUserDetailsService},
 * MyBatis, rồi tài khoản demo trong PostgreSQL. Các test {@code @WebMvcTest} dựng sẵn người đăng
 * nhập nên không bắt được lỗi ở những mắt xích này, ví dụ vai trò đọc từ CSDL bị gắn sai authority.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("TC-BR12-13 Đăng nhập đúng tài khoản demo: vào danh sách mẫu với đúng vai trò, phiên không giữ hash mật khẩu")
	void validCredentialsSignIn() throws Exception {
		mockMvc.perform(formLogin().user("tiepnhan").password("Passw0rd!"))
			.andExpect(redirectedUrl("/samples"))
			.andExpect(authenticated().withUsername("tiepnhan")
				.withRoles("RECEPTIONIST")
				.withAuthentication(auth -> assertThat(auth.getPrincipal())
					.isInstanceOfSatisfying(AppUserPrincipal.class, principal -> {
						assertThat(principal.getFullName()).isEqualTo("Trần Thị Thu");
						assertThat(principal.getPassword()).isNull();
					})));
	}

	@Test
	@DisplayName("TC-BR12-14 Sai mật khẩu: quay lại trang đăng nhập kèm ?error và không đăng nhập được")
	void wrongPasswordIsRejected() throws Exception {
		mockMvc.perform(formLogin().user("tiepnhan").password("sai-mat-khau"))
			.andExpect(redirectedUrl("/login?error"))
			.andExpect(unauthenticated());
	}

}
